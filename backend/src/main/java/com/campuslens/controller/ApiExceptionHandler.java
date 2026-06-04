package com.campuslens.controller;

import com.campuslens.service.AdminRequiredException;
import com.campuslens.service.AuthRequiredException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
  }

  @ExceptionHandler(AuthRequiredException.class)
  public ResponseEntity<Map<String, String>> unauthorized(AuthRequiredException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getMessage()));
  }

  @ExceptionHandler(AdminRequiredException.class)
  public ResponseEntity<Map<String, String>> forbidden(AdminRequiredException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .orElse("参数校验失败");
    return ResponseEntity.badRequest().body(Map.of("message", message));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, String>> uploadTooLarge() {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "图片大小超过限制"));
  }
}
