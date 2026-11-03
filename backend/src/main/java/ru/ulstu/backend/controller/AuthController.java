package ru.ulstu.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.dto.LoginRequest;
import ru.ulstu.backend.dto.RegisterRequest;
import ru.ulstu.backend.dto.UserResponse;
import ru.ulstu.backend.service.AuthService;
import ru.ulstu.backend.service.EmailAlreadyRegisteredException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

  private static final String SESSION_USER_ID = "userId";

  private final AuthService authService;

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
    UserResponse user = authService.register(request);
    startSession(httpRequest, user.id());
    return user;
  }

  @PostMapping("/auth/login")
  public UserResponse login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    UserResponse user =
        authService
            .login(request)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

    startSession(httpRequest, user.id());
    return user;
  }

  @PostMapping(value = "/auth/logout", headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
  }

  @DeleteMapping(value = "/account", headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAccount(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null || !(session.getAttribute(SESSION_USER_ID) instanceof Long userId)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    authService.deleteAccount(userId);
    session.invalidate();
  }

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public void handleEmailAlreadyRegistered() {}

  private void startSession(HttpServletRequest request, Long userId) {
    HttpSession previousSession = request.getSession(false);
    if (previousSession != null) {
      previousSession.invalidate();
    }

    request.getSession(true).setAttribute(SESSION_USER_ID, userId);
  }
}
