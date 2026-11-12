package com.wu.pharos.uam.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, int status, String code, String message, String correlationId, List<FieldError> details) {
  public record FieldError(String field, String message) {}
}
