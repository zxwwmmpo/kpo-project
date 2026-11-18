package ru.ulstu.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.entity.StudentAchievement;
import ru.ulstu.backend.repository.StudentAchievementRepository;
import ru.ulstu.backend.repository.StudentAchievementRepository.Summary;
import ru.ulstu.backend.service.CurrentUserService;
import ru.ulstu.backend.service.ProjectFileValidator;

@RestController
@RequestMapping("/api/profile/achievements")
@RequiredArgsConstructor
public class StudentAchievementController {

  private static final LocalDate EARLIEST_DATE = LocalDate.of(1926, 1, 1);
  private static final LocalDate LATEST_DATE = LocalDate.of(2026, 12, 31);

  private final CurrentUserService currentUserService;
  private final StudentAchievementRepository achievements;

  @GetMapping
  public List<Summary> list(HttpServletRequest request) {
    var student = currentUserService.requireStudent(request);
    return achievements.findSummaries(student.getId());
  }

  @PostMapping(
      headers = "X-Requested-With=XMLHttpRequest",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Summary create(@Valid @ModelAttribute AchievementForm form, HttpServletRequest request)
      throws IOException {
    var student = currentUserService.requireStudent(request);
    if (form.date().isBefore(EARLIEST_DATE) || form.date().isAfter(LATEST_DATE)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Укажите дату с 01.01.1926 по 31.12.2026.");
    }
    MultipartFile certificate = form.certificate();
    if (certificate == null || certificate.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Прикрепите сертификат.");
    }
    if (certificate.getSize() > ProjectFileValidator.MAX_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE, "Размер сертификата не должен превышать 5 МБ.");
    }
    String name = ProjectFileValidator.validatedName(certificate.getOriginalFilename());
    byte[] content = certificate.getBytes();
    ProjectFileValidator.validate(name, content);

    var achievement =
        StudentAchievement.builder()
            .user(student)
            .title(form.title().strip())
            .date(form.date())
            .description(form.description() == null ? "" : form.description().strip())
            .certificateName(name)
            .certificateContent(content)
            .build();
    return Summary.from(achievements.save(achievement));
  }

  @GetMapping("/{id}/certificate")
  public ResponseEntity<byte[]> download(@PathVariable Long id, HttpServletRequest request) {
    var student = currentUserService.requireStudent(request);
    var achievement =
        achievements
            .findByIdAndUserIdAndCertificateNameIsNotNull(id, student.getId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сертификат не найден."));
    String attachment =
        ContentDisposition.attachment()
            .filename(achievement.getCertificateName(), StandardCharsets.UTF_8)
            .build()
            .toString();
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, attachment)
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(achievement.getCertificateContent());
  }

  @DeleteMapping(value = "/{id}", headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id, HttpServletRequest request) {
    var student = currentUserService.requireStudent(request);
    if (achievements.deleteOwned(id, student.getId()) == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Достижение не найдено.");
    }
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleStatus(ResponseStatusException exception) {
    return exception.getBody();
  }

  public record AchievementForm(
      @NotBlank @Size(max = 120) String title,
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @Size(max = 3000) String description,
      MultipartFile certificate) {}
}
