package com.wu.pharos.uam.ingestion.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResult(UUID eventId, String processingStatus, Instant processedAt, List<Warning> warnings) {
  public record Warning(String code, String message) {}
}
