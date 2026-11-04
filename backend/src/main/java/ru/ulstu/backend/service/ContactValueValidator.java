package ru.ulstu.backend.service;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.entity.ContactType;

@Component
public class ContactValueValidator {

  public String normalize(ContactType type, String value) {
    if (type == null) {
      throw invalid("Выберите тип контакта");
    }

    if (value == null || value.isBlank() || value.length() > 2048) {
      throw invalid("Контакт должен содержать от 1 до 2048 символов");
    }

    String normalized = value.strip();

    return switch (type) {
      case PHONE -> normalizePhone(normalized);
      case URL -> validateUrl(normalized, false);
      case GITHUB -> validateUrl(normalized, true);
    };
  }

  private String normalizePhone(String value) {
    if (!value.matches("\\+?[0-9() -]+")) {
      throw invalid("Телефон содержит недопустимые символы");
    }

    String phone = value.replaceAll("[() -]", "");

    if (!phone.matches("\\+[1-9][0-9]{7,14}")) {
      throw invalid("Введите телефон с кодом страны: знак + и от 8 до 15 цифр");
    }

    return phone;
  }

  private String validateUrl(String value, boolean github) {
    URI uri;

    try {
      uri = new URI(value);
    } catch (URISyntaxException exception) {
      throw invalid("Введите корректную ссылку");
    }

    String scheme = uri.getScheme();
    String host = uri.getHost();

    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw invalid("Ссылка должна начинаться с http:// или https://");
    }

    if (host == null || host.isBlank() || uri.getRawUserInfo() != null) {
      throw invalid("Введите ссылку с корректным адресом сайта без логина и пароля");
    }

    if (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535)) {
      throw invalid("В ссылке указан недопустимый порт");
    }

    if (github) {
      validateGithub(uri);
    }

    return value;
  }

  private void validateGithub(URI uri) {
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || !"github.com".equalsIgnoreCase(uri.getHost())) {
      throw invalid("Ссылка GitHub должна начинаться с https://github.com/");
    }

    if (uri.getPort() != -1 && uri.getPort() != 443) {
      throw invalid("Ссылка GitHub не должна содержать нестандартный порт");
    }

    String path = uri.getPath();

    if (path == null || path.equals("/") || path.isBlank()) {
      throw invalid("Укажите ссылку на профиль или репозиторий GitHub");
    }
  }

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
