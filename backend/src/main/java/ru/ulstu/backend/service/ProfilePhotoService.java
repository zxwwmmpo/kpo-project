package ru.ulstu.backend.service;

import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class ProfilePhotoService {

  private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
  private static final int MAX_DIMENSION = 4096;

  @Value("${app.profile-photo.directory:./uploads/profile-photos}")
  private String directory;

  private Path root;

  @PostConstruct
  public void initialize() throws IOException {
    root = Path.of(directory).toAbsolutePath().normalize();
    Files.createDirectories(root);
  }

  public void save(Long userId, MultipartFile file) {
    if (file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите фотографию");
    }

    if (file.getSize() > MAX_FILE_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE, "Размер фотографии не должен превышать 5 МБ");
    }

    BufferedImage image = readImage(file);
    Path temporary = null;

    try {
      temporary = Files.createTempFile(root, "photo-", ".tmp");

      if (!ImageIO.write(image, "png", temporary.toFile())) {
        throw new IOException("PNG encoder is unavailable");
      }

      if (Files.size(temporary) > MAX_FILE_SIZE) {
        throw new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "После обработки фотография превышает 5 МБ. Уменьшите её размеры");
      }

      replaceFile(temporary, photoPath(userId));
    } catch (IOException exception) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить фотографию", exception);
    } finally {
      image.flush();
      removeTemporaryFile(temporary);
    }
  }

  public byte[] get(Long userId) {
    try {
      return Files.readAllBytes(photoPath(userId));
    } catch (NoSuchFileException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Фотография не загружена");
    } catch (IOException exception) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать фотографию", exception);
    }
  }

  public void delete(Long userId) {
    try {
      Files.deleteIfExists(photoPath(userId));
    } catch (IOException exception) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось удалить фотографию", exception);
    }
  }

  private BufferedImage readImage(MultipartFile file) {
    try (InputStream input = file.getInputStream();
        ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {

      if (imageInput == null) {
        throw invalidImage();
      }

      Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);

      if (!readers.hasNext()) {
        throw invalidImage();
      }

      ImageReader reader = readers.next();

      try {
        reader.setInput(imageInput, true, true);
        String format = reader.getFormatName();

        if (!"JPEG".equalsIgnoreCase(format) && !"PNG".equalsIgnoreCase(format)) {
          throw invalidImage();
        }

        int width = reader.getWidth(0);
        int height = reader.getHeight(0);

        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Размеры фотографии не должны превышать 4096 × 4096 пикселей");
        }

        BufferedImage image = reader.read(0);

        if (image == null) {
          throw invalidImage();
        }

        return image;
      } finally {
        reader.dispose();
      }
    } catch (IOException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Фотография повреждена или имеет неподдерживаемый формат",
          exception);
    }
  }

  private void replaceFile(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private Path photoPath(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("User ID must be positive");
    }

    return root.resolve(userId + ".png");
  }

  private void removeTemporaryFile(Path temporary) {
    if (temporary == null) {
      return;
    }

    try {
      Files.deleteIfExists(temporary);
    } catch (IOException exception) {
      log.warn("Failed to delete temporary photo file {}", temporary, exception);
    }
  }

  private ResponseStatusException invalidImage() {
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Допускаются только изображения JPEG и PNG");
  }
}
