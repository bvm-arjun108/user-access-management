package com.wu.pharos.uam.ingestion.controller;

import com.wu.pharos.uam.ingestion.api.EmployeeEventApi;
import com.wu.pharos.uam.ingestion.dto.EventResult;
import com.wu.pharos.uam.ingestion.service.EmployeeEventService;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class EmployeeEventController implements EmployeeEventApi {
  private final EmployeeEventService service;

  public EmployeeEventController(EmployeeEventService service) {
    this.service = service;
  }

  @Override
  public EventResult process(JsonNode request) {
    return service.process(request);
  }
}
