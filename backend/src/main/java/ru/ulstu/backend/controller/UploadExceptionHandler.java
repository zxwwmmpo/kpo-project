package ru.ulstu.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class UploadExceptionHandler {

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail handleUploadSize(
      MaxUploadSizeExceededException exception, HttpServletRequest request) {
    String message =
        request.getRequestURI().endsWith("/files")
            ? "Размер файла не должен превышать 5 МБ."
            : "Размер фотографии не должен превышать 5 МБ";
    return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, message);
  }
}
