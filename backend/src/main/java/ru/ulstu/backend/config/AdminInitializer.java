package ru.ulstu.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.ulstu.backend.entity.Role;
import ru.ulstu.backend.entity.User;
import ru.ulstu.backend.repository.UserRepository;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminInitializer implements ApplicationRunner {

  private static final String ADMIN_EMAIL = "admin@admin.com";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.admin.password:}")
  private String initialPassword;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    var existing = userRepository.findByEmail(ADMIN_EMAIL);

    if (existing.isPresent()) {
      if (existing.get().getRole() != Role.ADMIN) {
        throw new IllegalStateException(
            "Адрес admin@admin.com уже занят пользователем без роли администратора");
      }
      return;
    }

    if (initialPassword == null || initialPassword.isBlank()) {
      throw new IllegalStateException(
          "Для создания администратора задайте переменную окружения ADMIN_PASSWORD");
    }

    userRepository.saveAndFlush(
        User.administrator(ADMIN_EMAIL, passwordEncoder.encode(initialPassword)));
  }
}
