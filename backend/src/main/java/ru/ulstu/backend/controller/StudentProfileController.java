package ru.ulstu.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.dto.StudentProfileResponse;
import ru.ulstu.backend.dto.UpdateStudentProfileRequest;
import ru.ulstu.backend.entity.User;
import ru.ulstu.backend.service.CurrentUserService;
import ru.ulstu.backend.service.StudentProfileService;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class StudentProfileController {

  private final CurrentUserService currentUserService;
  private final StudentProfileService profileService;

  @GetMapping
  public StudentProfileResponse get(HttpServletRequest request) {
    User user = currentUserService.requireStudent(request);
    return profileService.get(user);
  }

  @PutMapping(headers = "X-Requested-With=XMLHttpRequest")
  public StudentProfileResponse update(
      @Valid @RequestBody UpdateStudentProfileRequest body, HttpServletRequest request) {

    User user = currentUserService.requireStudent(request);
    return profileService.update(user, body);
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
        "Данные изменились во время сохранения. Обновите страницу и повторите попытку");
  }
}
