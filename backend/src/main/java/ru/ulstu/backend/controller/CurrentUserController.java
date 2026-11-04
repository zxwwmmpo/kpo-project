package ru.ulstu.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ulstu.backend.dto.CurrentUserResponse;
import ru.ulstu.backend.entity.User;
import ru.ulstu.backend.service.CurrentUserService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class CurrentUserController {

  private final CurrentUserService currentUserService;

  @GetMapping("/me")
  public CurrentUserResponse me(HttpServletRequest request) {
    User user = currentUserService.requireUser(request);
    return new CurrentUserResponse(user.getId(), user.getEmail(), user.getRole());
  }
}
