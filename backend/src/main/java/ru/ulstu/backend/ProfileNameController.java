package ru.ulstu.backend;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile/name")
public class ProfileNameController {

  private final ProfileNameService service;

  public ProfileNameController(ProfileNameService service) {
    this.service = service;
  }

  @PostMapping("/preview")
  public NameResponse preview(@RequestBody NameRequest request) {
    try {
      return new NameResponse(service.normalizeName(request.name()));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
  }

  public record NameRequest(String name) {}

  public record NameResponse(String name) {}
}
