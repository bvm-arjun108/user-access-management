package com.wu.pharos.uam.common.error;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> domain(ApiException e) {
    return response(e.status(), e.code(), e.getMessage());
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<ApiError> malformed(Exception e) {
    return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid JSON or parameter type");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ApiError> database(DataAccessException e) {
    return response(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "Database operation failed; retry the same event");
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ApiError> missing(Exception e) {
    return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e) {
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Request could not be completed");
  }

  private ResponseEntity<ApiError> response(HttpStatus status, String code, String message) {
    String correlation = UUID.randomUUID().toString();
    // Do not log payloads, SQL parameters, or exception text containing employee data.
    LOG.warn("Request rejected code={} status={} correlationId={}", code, status.value(), correlation);
    return ResponseEntity
      .status(status)
      .header("X-Correlation-ID", correlation)
      .body(new ApiError(Instant.now(), status.value(), code, message, correlation, List.of()));
  }
}
