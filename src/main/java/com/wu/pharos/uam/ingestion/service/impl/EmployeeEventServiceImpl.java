package com.wu.pharos.uam.ingestion.service.impl;

import com.wu.pharos.uam.common.error.ApiException;
import com.wu.pharos.uam.ingestion.dto.EventResult;
import com.wu.pharos.uam.ingestion.model.EmployeeEvent;
import com.wu.pharos.uam.ingestion.model.EventType;
import com.wu.pharos.uam.ingestion.repository.UamRepository;
import com.wu.pharos.uam.ingestion.repository.UamRepository.EventRow;
import com.wu.pharos.uam.ingestion.service.EmployeeEventService;
import com.wu.pharos.uam.ingestion.service.EventValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@Service
public class EmployeeEventServiceImpl implements EmployeeEventService {
  private static final Logger LOG = LoggerFactory.getLogger(EmployeeEventServiceImpl.class);
  private final UamRepository repository;
  private final EventValidator validator;
  private final TransactionTemplate transaction;
  private final TransactionTemplate independent;

  public EmployeeEventServiceImpl(UamRepository repository, EventValidator validator, PlatformTransactionManager manager) {
    this.repository = repository;
    this.validator = validator;
    transaction = new TransactionTemplate(manager);
    transaction.setTimeout(30);
    independent = new TransactionTemplate(manager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    independent.setTimeout(30);
  }

  @Override
  public EventResult process(JsonNode request) {
    EmployeeEvent event = validator.envelope(request);
    independent.executeWithoutResult(status -> {
      repository.receive(event);
      if (!repository.event(event, true).payloadMatches()) {
        throw ApiException.conflict("EVENT_ID_CONFLICT", "This event UUID was received with a different payload");
      }
    });
    try {
      EventResult result = Objects.requireNonNull(transaction.execute(status -> apply(event)));
      LOG.info("iPass event processed eventId={} type={} result={}", event.eventId(), event.eventType(), result.status());
      return result;
    } catch (RuntimeException failure) {
      String code = failure instanceof ApiException e ? e.code() : "PROCESSING_FAILED";
      String message = failure instanceof ApiException e ? e.getMessage() : "Processing failed; retry the identical event";
      try {
        independent.executeWithoutResult(status -> repository.failed(event, code, message));
      } catch (RuntimeException auditFailure) {
        LOG.error("Failed to persist failure eventId={}", event.eventId());
      }
      throw failure;
    }
  }

  private EventResult apply(EmployeeEvent event) {
    repository.lockEmployee(event.employeeId());
    EventRow receipt = repository.event(event, true);
    if ("APPLIED".equals(receipt.status())) {
      return new EventResult(event.eventId(), "SUCCESS");
    }
    validator.validate(event);
    if (event.eventType() == EventType.USER_TERMINATED) {
      repository.terminate(event);
      repository.terminateAssignments(event);
    } else {
      if ("TERMINATED".equals(repository.status(event.employeeId())) && event.eventType() != EventType.USER_PROVISIONED) {
        throw ApiException.conflict("REPROVISIONING_REQUIRED", "A terminated employee requires an approved USER_PROVISIONED event");
      }
      repository.saveProfile(event);
      long account = repository.saveAccount(event);
      // Stable ordering also avoids deadlocks when different employees share role definitions.
      List<JsonNode> roles = new ArrayList<>();
      event.user().get("iPassRoles").forEach(roles::add);
      roles.sort(java.util.Comparator.comparing(r -> r.get("entitlementValue").asString()));
      for (JsonNode role : roles) {
        var storedRole = repository.upsertRole(role, event);
        repository.saveOwners(storedRole.id(), role.get("owners"));
        repository.retainOrCreateAssignment(event, account, storedRole.id(), role);
      }
      repository.reconcile(event, account);
    }
    repository.applied(event);
    return new EventResult(event.eventId(), "SUCCESS");
  }
}
