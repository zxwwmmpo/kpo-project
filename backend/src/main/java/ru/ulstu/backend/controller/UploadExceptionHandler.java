package ru.ulstu.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class UploadExceptionHandler {

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail handleUploadSize(MaxUploadSizeExceededException exception) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.PAYLOAD_TOO_LARGE, "Размер фотографии не должен превышать 5 МБ");
  }
}
