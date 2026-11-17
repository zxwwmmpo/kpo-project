package ru.ulstu.backend.service;

import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ulstu.backend.dto.LoginRequest;
import ru.ulstu.backend.dto.RegisterRequest;
import ru.ulstu.backend.dto.UserResponse;
import ru.ulstu.backend.entity.User;
import ru.ulstu.backend.repository.StudentContactRepository;
import ru.ulstu.backend.repository.StudentProfileRepository;
import ru.ulstu.backend.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final StudentContactRepository contactRepository;
  private final StudentProfileRepository profileRepository;
  private final PasswordEncoder passwordEncoder;

  public UserResponse register(RegisterRequest request) {
    String email = request.email().toLowerCase(Locale.ROOT);

    if (userRepository.existsByEmail(email)) {
      throw new EmailAlreadyRegisteredException();
    }

    String passwordHash = passwordEncoder.encode(request.password());
    User user = new User(email, passwordHash);

    try {
      user = userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException exception) {
      if (userRepository.existsByEmail(email)) {
        throw new EmailAlreadyRegisteredException();
      }
      throw exception;
    }

    return new UserResponse(user.getId(), user.getEmail());
  }

  public Optional<UserResponse> login(LoginRequest request) {
    String email = request.email().toLowerCase(Locale.ROOT);
    return userRepository
        .findByEmail(email)
        .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
        .map(user -> new UserResponse(user.getId(), user.getEmail()));
  }

  @Transactional
  public void deleteAccount(Long userId) {
    contactRepository.deleteByUserId(userId);
    contactRepository.flush();
    profileRepository.deleteByUserId(userId);
    profileRepository.flush();
    userRepository.deleteById(userId);
  }
}
