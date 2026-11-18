package ru.ulstu.backend.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ProjectFileValidator {

  public static final int MAX_SIZE = 5 * 1024 * 1024;

  private ProjectFileValidator() {}

  public static String validatedName(String filename) {
    String name = filename == null ? "" : filename.strip();
    if (name.isEmpty()
        || name.length() > 255
        || name.contains("/")
        || name.contains("\\")
        || name.chars().anyMatch(Character::isISOControl)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимое имя файла.");
    }
    return name;
  }

  public static void validate(String name, byte[] content) {
    String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    boolean supported =
        switch (extension) {
          case "pdf" -> startsWith(content, '%', 'P', 'D', 'F', '-');
          case "png" -> startsWith(content, 0x89, 'P', 'N', 'G', 13, 10, 26, 10);
          case "jpg", "jpeg" -> startsWith(content, 0xff, 0xd8, 0xff);
          case "docx" -> isWordArchive(content);
          default -> false;
        };
    if (name.lastIndexOf('.') <= 0 || !supported) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Выберите файл PDF, PNG, JPEG или DOCX.");
    }
  }

  private static boolean startsWith(byte[] content, int... signature) {
    if (content.length < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if (Byte.toUnsignedInt(content[i]) != signature[i]) {
        return false;
      }
    }
    return true;
  }

  private static boolean isWordArchive(byte[] content) {
    int remaining = 25 * 1024 * 1024;
    byte[] buffer = new byte[8192];
    try (var archive = new ZipInputStream(new ByteArrayInputStream(content))) {
      boolean hasTypes = false;
      boolean hasDocument = false;
      for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
        for (int read = archive.read(buffer); read != -1; read = archive.read(buffer)) {
          remaining -= read;
          if (remaining < 0) {
            return false;
          }
        }
        hasTypes |= "[Content_Types].xml".equals(entry.getName());
        hasDocument |= "word/document.xml".equals(entry.getName());
      }
      return hasTypes && hasDocument;
    } catch (IOException | IllegalArgumentException exception) {
      return false;
    }
  }
}
