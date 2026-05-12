package com.playona.api.global.common;

import lombok.Getter;

@Getter
public class ApiResponse<T> {
  private final boolean success;
  private final T data;
  private final String message;
  private final String result;

  private ApiResponse(boolean success, T data, String message, String result) {
    this.success = success;
    this.data = data;
    this.message = message;
    this.result = result;
  }

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, "SUCCESS");
  }

  public static <T> ApiResponse<T> fail(String message) {
    return new ApiResponse<>(false, null, message, "FAIL");
  }
}