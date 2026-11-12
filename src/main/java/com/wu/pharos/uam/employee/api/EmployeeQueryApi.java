package com.wu.pharos.uam.employee.api;

import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tools.jackson.databind.JsonNode;

@Tag(name = "Ingestion verification")
public interface EmployeeQueryApi {
  @Operation(summary = "Get employee profile and assignment history")
  @GetMapping(value = "/api/v1/employees/{employeeId}", produces = "application/json")
  JsonNode employee(@PathVariable("employeeId") String employeeId);

  @Operation(summary = "Get event processing result")
  @GetMapping(value = "/api/v1/ipass/events/{eventId}", produces = "application/json")
  JsonNode event(@PathVariable("eventId") UUID eventId);
}
