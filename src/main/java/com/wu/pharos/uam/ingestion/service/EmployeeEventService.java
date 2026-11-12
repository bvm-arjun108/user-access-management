package com.wu.pharos.uam.ingestion.service;

import com.wu.pharos.uam.ingestion.dto.EventResult;
import tools.jackson.databind.JsonNode;

public interface EmployeeEventService {
  EventResult process(JsonNode request);
}
