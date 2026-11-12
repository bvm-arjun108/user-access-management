package com.wu.pharos.uam.employee.controller;

import com.wu.pharos.uam.employee.api.EmployeeQueryApi;
import com.wu.pharos.uam.employee.service.EmployeeQueryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class EmployeeQueryController implements EmployeeQueryApi {
  private final EmployeeQueryService service;

  public EmployeeQueryController(EmployeeQueryService service) {
    this.service = service;
  }

  @Override
  public JsonNode employee(String id) {
    return service.employee(id);
  }

  @Override
  public JsonNode event(UUID id) {
    return service.event(id);
  }
}
