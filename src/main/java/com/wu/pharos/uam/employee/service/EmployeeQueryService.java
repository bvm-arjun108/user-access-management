package com.wu.pharos.uam.employee.service;

import com.wu.pharos.uam.ingestion.repository.UamRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@Transactional(readOnly = true)
public class EmployeeQueryService {
  private final UamRepository repository;
  private final String environment;

  public EmployeeQueryService(UamRepository repository, @Value("${uam.environment}") String environment) {
    this.repository = repository;
    this.environment = environment;
  }

  public JsonNode employee(String id, boolean includeHistory) {
    return repository.employee(id, environment, includeHistory);
  }

  public JsonNode event(UUID id) {
    return repository.event(id, environment);
  }
}
