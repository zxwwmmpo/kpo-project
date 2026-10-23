package ru.ulstu.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProfileNameServiceTest {

  private final ProfileNameService service = new ProfileNameService();

  @Test
  void preservesValidName() {
    assertEquals("Иван Иванов", service.normalizeName("Иван Иванов"));
  }

  @Test
  void removesSurroundingWhitespace() {
    assertEquals("Иван Иванов", service.normalizeName("  Иван Иванов  "));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n"})
  void rejectsMissingName(String name) {
    assertThrows(IllegalArgumentException.class, () -> service.normalizeName(name));
  }
}
