package ru.ulstu.backend.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.entity.Role;
import ru.ulstu.backend.entity.User;
import ru.ulstu.backend.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

  private static final String SESSION_USER_ID = "userId";

  private final UserRepository userRepository;

  public User requireUser(HttpServletRequest request) {
    HttpSession session = request.getSession(false);

    if (session == null || !(session.getAttribute(SESSION_USER_ID) instanceof Long userId)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Необходимо войти в систему");
    }

    return userRepository
        .findById(userId)
        .orElseThrow(
            () -> {
              session.invalidate();
              return new ResponseStatusException(
                  HttpStatus.UNAUTHORIZED, "Пользователь больше не существует");
            });
  }

  public User requireAdmin(HttpServletRequest request) {
    User user = requireUser(request);

    if (user.getRole() != Role.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ только для администратора");
    }

    return user;
  }

  public User requireStudent(HttpServletRequest request) {
    User user = requireUser(request);

    if (user.getRole() != Role.STUDENT) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ только для студента");
    }

    return user;
  }
}
