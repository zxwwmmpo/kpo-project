package ru.ulstu.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.entity.User;
import ru.ulstu.backend.service.CurrentUserService;
import ru.ulstu.backend.service.ProfilePhotoService;

@RestController
@RequestMapping("/api/profile/photo")
@RequiredArgsConstructor
public class ProfilePhotoController {

  private final CurrentUserService currentUserService;
  private final ProfilePhotoService photoService;

  @GetMapping
  public ResponseEntity<byte[]> get(HttpServletRequest request) {
    User user = currentUserService.requireStudent(request);
    byte[] photo = photoService.get(user.getId());

    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(photo);
  }

  @PostMapping(
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
    User user = currentUserService.requireStudent(request);
    photoService.save(user.getId(), file);
  }

  @DeleteMapping(headers = "X-Requested-With=XMLHttpRequest")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(HttpServletRequest request) {
    User user = currentUserService.requireStudent(request);
    photoService.delete(user.getId());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleStatus(ResponseStatusException exception) {
    String message =
        exception.getReason() == null ? "Не удалось выполнить запрос" : exception.getReason();
    return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), message);
  }
}
