package ru.ulstu.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.dto.StudentContactRequest;
import ru.ulstu.backend.dto.StudentContactResponse;
import ru.ulstu.backend.entity.User;
import ru.ulstu.backend.service.CurrentUserService;
import ru.ulstu.backend.service.StudentContactService;

@RestController
@RequestMapping("/api/profile/contacts")
@RequiredArgsConstructor
public class StudentContactController {

  private final CurrentUserService currentUserService;
  private final StudentContactService contactService;

  @GetMapping
  public List<StudentContactResponse> list(HttpServletRequest request) {
    User user = currentUserService.requireStudent(request);
    return contactService.list(user);
  }

  @PostMapping(headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.CREATED)
  public StudentContactResponse create(
      @Valid @RequestBody StudentContactRequest body, HttpServletRequest request) {

    User user = currentUserService.requireStudent(request);
    return contactService.create(user, body);
  }

  @PutMapping(value = "/{id}", headers = "X-Requested-With=XMLHttpRequest")
  public StudentContactResponse update(
      @PathVariable Long id,
      @Valid @RequestBody StudentContactRequest body,
      HttpServletRequest request) {

    User user = currentUserService.requireStudent(request);
    return contactService.update(user, id, body);
  }

  @DeleteMapping(value = "/{id}", headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id, HttpServletRequest request) {
    User user = currentUserService.requireStudent(request);
    contactService.delete(user, id);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleStatus(ResponseStatusException exception) {
    String message =
        exception.getReason() == null ? "Не удалось выполнить запрос" : exception.getReason();
    return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), message);
  }
}
