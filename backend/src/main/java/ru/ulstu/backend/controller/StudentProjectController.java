package ru.ulstu.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.entity.ProjectFile;
import ru.ulstu.backend.entity.StudentProject;
import ru.ulstu.backend.entity.StudentProject.ProjectLink;
import ru.ulstu.backend.repository.ProjectFileRepository;
import ru.ulstu.backend.repository.ProjectFileRepository.FileMetadata;
import ru.ulstu.backend.repository.StudentProjectRepository;
import ru.ulstu.backend.service.CurrentUserService;
import ru.ulstu.backend.service.ProjectFileValidator;

@RestController
@RequestMapping("/api/profile/projects")
@RequiredArgsConstructor
public class StudentProjectController {

  private final CurrentUserService currentUserService;
  private final StudentProjectRepository projects;
  private final ProjectFileRepository files;

  @GetMapping
  public List<StudentProjectRepository.Summary> list(HttpServletRequest request) {
    var student = currentUserService.requireStudent(request);
    return projects.findSummaries(student.getId());
  }

  @PostMapping(headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.CREATED)
  public ProjectResponse create(
      @Valid @RequestBody ProjectRequest body, HttpServletRequest request) {
    var student = currentUserService.requireStudent(request);
    var project =
        StudentProject.builder()
            .user(student)
            .title(body.title().strip())
            .description(body.description().strip())
            .role(optionalText(body.role()))
            .build();
    return ProjectResponse.from(projects.save(project));
  }

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  public ProjectDetails details(@PathVariable Long id, HttpServletRequest request) {
    var project = ownedProject(id, request);
    return new ProjectDetails(
        ProjectResponse.from(project),
        files.findAllByProjectIdOrderById(id),
        List.copyOf(project.getLinks()));
  }

  @PutMapping(value = "/{id}", headers = "X-Requested-With=XMLHttpRequest")
  @Transactional
  public ProjectResponse update(
      @PathVariable Long id, @Valid @RequestBody ProjectRequest body, HttpServletRequest request) {
    var project = lockProject(id, request);
    project.update(body.title().strip(), body.description().strip(), optionalText(body.role()));
    return ProjectResponse.from(project);
  }

  @DeleteMapping(value = "/{id}", headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(@PathVariable Long id, HttpServletRequest request) {
    var project = lockProject(id, request);
    files.deleteAllForProject(id);
    projects.delete(project);
  }

  @PostMapping(value = "/{id}/links", headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public ProjectLink addLink(
      @PathVariable Long id, @Valid @RequestBody LinkRequest body, HttpServletRequest request) {
    var project = lockProject(id, request);
    String url = body.url().strip();
    if (!validLink(url)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Введите адрес HTTP или HTTPS.");
    }
    var link = new ProjectLink(url, optionalText(body.label()));
    project.getLinks().add(link);
    return link;
  }

  @PostMapping(
      value = "/{id}/files",
      headers = "X-Requested-With=XMLHttpRequest",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public FileMetadata upload(
      @PathVariable Long id, @RequestParam MultipartFile file, HttpServletRequest request)
      throws IOException {
    var project = lockProject(id, request);
    if (file.getSize() > ProjectFileValidator.MAX_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE, "Размер файла не должен превышать 5 МБ.");
    }
    if (files.countByProjectId(id) >= 5) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Можно прикрепить до 5 файлов.");
    }

    String name = optionalText(file.getOriginalFilename());
    if (name.isEmpty()
        || name.length() > 255
        || name.contains("/")
        || name.contains("\\")
        || name.chars().anyMatch(Character::isISOControl)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимое имя файла.");
    }
    byte[] content = file.getBytes();
    ProjectFileValidator.validate(name, content);
    var saved =
        files.save(
            ProjectFile.builder()
                .project(project)
                .name(name)
                .size(content.length)
                .content(content)
                .build());
    return new FileMetadata(saved.getId(), saved.getName(), saved.getSize());
  }

  @GetMapping("/{id}/files/{fileId}")
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> download(
      @PathVariable Long id, @PathVariable Long fileId, HttpServletRequest request) {
    ownedProject(id, request);
    var file =
        files.findByIdAndProjectId(fileId, id).orElseThrow(StudentProjectController::fileNotFound);
    String attachment =
        ContentDisposition.attachment()
            .filename(file.getName(), StandardCharsets.UTF_8)
            .build()
            .toString();
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, attachment)
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(file.getContent());
  }

  @DeleteMapping(value = "/{id}/files/{fileId}", headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void deleteFile(
      @PathVariable Long id, @PathVariable Long fileId, HttpServletRequest request) {
    lockProject(id, request);
    if (files.deleteFromProject(fileId, id) == 0) {
      throw fileNotFound();
    }
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleStatus(ResponseStatusException exception) {
    return exception.getBody();
  }

  private StudentProject ownedProject(Long id, HttpServletRequest request) {
    var student = currentUserService.requireStudent(request);
    return projects
        .findByIdAndUserId(id, student.getId())
        .orElseThrow(StudentProjectController::projectNotFound);
  }

  private StudentProject lockProject(Long id, HttpServletRequest request) {
    var student = currentUserService.requireStudent(request);
    return projects
        .lockOwnedProject(id, student.getId())
        .orElseThrow(StudentProjectController::projectNotFound);
  }

  private static ResponseStatusException projectNotFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Проект не найден.");
  }

  private static ResponseStatusException fileNotFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл не найден.");
  }

  private static String optionalText(String text) {
    return text == null ? "" : text.strip();
  }

  private static boolean validLink(String url) {
    try {
      var uri = URI.create(url);
      return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null
          && uri.getRawUserInfo() == null
          && uri.getPort() <= 65535;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  public record ProjectRequest(
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 3000) String description,
      @Size(max = 100) String role) {}

  public record LinkRequest(
      @NotBlank @Size(max = 2048) String url, @Size(max = 100) String label) {}

  public record ProjectDetails(
      ProjectResponse project, List<FileMetadata> files, List<ProjectLink> links) {}

  public record ProjectResponse(Long id, String title, String description, String role) {
    private static ProjectResponse from(StudentProject project) {
      return new ProjectResponse(
          project.getId(), project.getTitle(), project.getDescription(), project.getRole());
    }
  }
}
