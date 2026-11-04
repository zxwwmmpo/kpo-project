package ru.ulstu.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.dto.CreateStudyEntryRequest;
import ru.ulstu.backend.dto.RenameStudyEntryRequest;
import ru.ulstu.backend.dto.StudyEntryResponse;
import ru.ulstu.backend.entity.StudyEntryType;
import ru.ulstu.backend.service.CurrentUserService;
import ru.ulstu.backend.service.StudyCatalogService;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class StudyCatalogController {

  private final StudyCatalogService catalogService;
  private final CurrentUserService currentUserService;

  @GetMapping("/{type}")
  public List<StudyEntryResponse> list(
      @PathVariable String type,
      @RequestParam(required = false) Long parentId,
      HttpServletRequest request) {

    currentUserService.requireUser(request);
    return catalogService.list(parseType(type), parentId);
  }

  @PostMapping(value = "/{type}", headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.CREATED)
  public StudyEntryResponse create(
      @PathVariable String type,
      @Valid @RequestBody CreateStudyEntryRequest body,
      HttpServletRequest request) {

    currentUserService.requireAdmin(request);
    return catalogService.create(parseType(type), body);
  }

  @PatchMapping(value = "/{type}/{id}", headers = "X-Requested-With=XMLHttpRequest")
  public StudyEntryResponse rename(
      @PathVariable String type,
      @PathVariable Long id,
      @Valid @RequestBody RenameStudyEntryRequest body,
      HttpServletRequest request) {

    currentUserService.requireAdmin(request);
    return catalogService.rename(parseType(type), id, body);
  }

  @DeleteMapping(value = "/{type}/{id}", headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String type, @PathVariable Long id, HttpServletRequest request) {
    currentUserService.requireAdmin(request);
    catalogService.delete(parseType(type), id);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleStatus(ResponseStatusException exception) {
    String message =
        exception.getReason() == null ? "Не удалось выполнить запрос" : exception.getReason();
    return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), message);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleIntegrityViolation() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        "Изменение невозможно: запись уже существует или используется другими данными");
  }

  private StudyEntryType parseType(String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "faculties" -> StudyEntryType.FACULTY;
      case "directions" -> StudyEntryType.DIRECTION;
      case "groups" -> StudyEntryType.GROUP;
      default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Неизвестный справочник");
    };
  }
}
