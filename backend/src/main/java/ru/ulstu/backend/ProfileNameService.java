package ru.ulstu.backend;

import org.springframework.stereotype.Service;

@Service
public class ProfileNameService {

  public String normalizeName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Student name must not be blank");
    }

    return name.strip();
  }
}
