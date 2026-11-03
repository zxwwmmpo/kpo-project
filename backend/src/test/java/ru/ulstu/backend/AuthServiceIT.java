package ru.ulstu.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.ulstu.backend.dto.LoginRequest;
import ru.ulstu.backend.dto.RegisterRequest;
import ru.ulstu.backend.dto.UserResponse;
import ru.ulstu.backend.entity.User;
import ru.ulstu.backend.repository.UserRepository;
import ru.ulstu.backend.service.AuthService;
import ru.ulstu.backend.service.EmailAlreadyRegisteredException;

@SpringBootTest
class AuthServiceIT {

  private static final String PASSWORD = "Student123!";

  @Autowired private AuthService authService;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void registrationStoresNormalizedEmailAndPasswordHash() {
    String email = newEmail();

    UserResponse response =
        authService.register(
            new RegisterRequest(email.toUpperCase(java.util.Locale.ROOT), PASSWORD));

    User savedUser = userRepository.findById(response.id()).orElseThrow();

    assertEquals(email, savedUser.getEmail());
    assertEquals(email, response.email());
    assertNotEquals(PASSWORD, savedUser.getPasswordHash());
    assertTrue(passwordEncoder.matches(PASSWORD, savedUser.getPasswordHash()));
  }

  @Test
  void registrationRejectsAnExistingEmailRegardlessOfCase() {
    String email = newEmail();
    authService.register(new RegisterRequest(email, PASSWORD));

    RegisterRequest duplicate =
        new RegisterRequest(email.toUpperCase(java.util.Locale.ROOT), PASSWORD);

    assertThrows(EmailAlreadyRegisteredException.class, () -> authService.register(duplicate));
  }

  @Test
  void loginChecksPasswordAgainstStoredHash() {
    String email = newEmail();
    UserResponse registered = authService.register(new RegisterRequest(email, PASSWORD));

    UserResponse loggedIn = authService.login(new LoginRequest(email, PASSWORD)).orElseThrow();

    assertEquals(registered.id(), loggedIn.id());
    assertTrue(authService.login(new LoginRequest(email, "WrongPassword!")).isEmpty());
  }

  @Test
  void deletionRemovesUserFromDatabase() {
    UserResponse registered = authService.register(new RegisterRequest(newEmail(), PASSWORD));

    authService.deleteAccount(registered.id());

    assertFalse(userRepository.existsById(registered.id()));
  }

  private String newEmail() {
    return "student-" + UUID.randomUUID() + "@example.com";
  }
}
