package ru.ulstu.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.service.ProjectFileValidator;

class ProjectFileValidatorTest {

  @Test
  void acceptsMatchingImageSignatures() {
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};

    assertDoesNotThrow(() -> ProjectFileValidator.validate("image.PNG", png));
    assertDoesNotThrow(() -> ProjectFileValidator.validate("image.jpeg", jpeg));
    assertThrows(
        ResponseStatusException.class, () -> ProjectFileValidator.validate("image.jpg", png));
    assertThrows(
        ResponseStatusException.class, () -> ProjectFileValidator.validate("image.gif", png));
  }

  @Test
  void acceptsWordArchiveAndRejectsRenamedZip() throws IOException {
    byte[] docx = archive("[Content_Types].xml", "word/document.xml");
    byte[] zip = archive("[Content_Types].xml", "word/other.xml");

    assertDoesNotThrow(() -> ProjectFileValidator.validate("report.docx", docx));
    assertThrows(
        ResponseStatusException.class, () -> ProjectFileValidator.validate("report.docx", zip));
  }

  private byte[] archive(String... entries) throws IOException {
    var content = new ByteArrayOutputStream();
    try (var archive = new ZipOutputStream(content)) {
      for (String entry : entries) {
        archive.putNextEntry(new ZipEntry(entry));
        archive.write("<root/>".getBytes(StandardCharsets.UTF_8));
        archive.closeEntry();
      }
    }
    return content.toByteArray();
  }
}
