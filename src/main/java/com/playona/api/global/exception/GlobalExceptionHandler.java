package com.playona.api.global.exception;

import com.playona.api.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ApiResponse<?>> handleRuntime(RuntimeException e) {
    return ResponseEntity.internalServerError().body(ApiResponse.fail(e.getMessage()));
  }
}