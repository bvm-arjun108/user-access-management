package com.wu.pharos.uam.ingestion.api;

import com.wu.pharos.uam.ingestion.dto.EventResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import tools.jackson.databind.JsonNode;

@Tag(name = "iPass employee provisioning")
@RequestMapping("/api/v1/ipass/events")
public interface EmployeeEventApi {
  @Operation(summary = "Apply an employee snapshot", description = "New event: new UUID. Retry: original UUID and unchanged payload. "
      + "Full examples are available in /openapi/ipass.json.")
  @PostMapping(consumes = "application/json", produces = "application/json")
  EventResult process(@RequestBody JsonNode request);
}
