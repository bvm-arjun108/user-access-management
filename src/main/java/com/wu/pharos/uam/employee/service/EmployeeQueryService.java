package com.wu.pharos.uam.employee.service;

import com.wu.pharos.uam.ingestion.repository.UamRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@Transactional(readOnly = true)
public class EmployeeQueryService {
  private final UamRepository repository;

  public EmployeeQueryService(UamRepository repository) {
    this.repository = repository;
  }

  public JsonNode employee(String id, boolean includeHistory) {
    return repository.employee(id, includeHistory);
  }

  public JsonNode event(UUID id) {
    return repository.event(id);
  }
}
