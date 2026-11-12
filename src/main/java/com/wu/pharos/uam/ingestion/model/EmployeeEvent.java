package com.wu.pharos.uam.ingestion.model;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record EmployeeEvent(UUID eventId, EventType eventType, Instant occurredAt, String environment, String employeeId, JsonNode user,
    JsonNode payload) {}
