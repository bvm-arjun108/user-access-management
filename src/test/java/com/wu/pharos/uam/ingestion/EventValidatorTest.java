package com.wu.pharos.uam.ingestion;

import static org.assertj.core.api.Assertions.*;
import com.wu.pharos.uam.common.error.ApiException;
import com.wu.pharos.uam.ingestion.service.EventValidator;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class EventValidatorTest {
  private EventValidator validator;
  private final JsonMapper mapper = JsonMapper.builder().build();
  private ObjectNode payload;

  @BeforeEach
  void setup() throws Exception {
    validator = new EventValidator("Prod", Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC),
        Validation.buildDefaultValidatorFactory().getValidator());
    payload = (ObjectNode) mapper.readTree(getClass().getResourceAsStream("/provision.json"));
  }

  @Test
  void validSnapshot() {
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
  }

  @Test
  void eventIdMustBeUuid() {
    payload.put("eventId", "EVT-001");
    assertThatThrownBy(() -> validator.envelope(payload)).isInstanceOf(ApiException.class).hasMessageContaining("UUID");
  }

  @Test
  void rejectsEventVersion() {
    payload.put("eventVersion", 1);
    assertThatThrownBy(() -> validator.envelope(payload)).hasMessageContaining("Unknown field");
  }

  @Test
  void managerCannotBeNullOnProvisioning() {
    ((ObjectNode) payload.get("user")).putNull("manager");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("manager");
  }

  @Test
  void missingRoleListIsNotRevocation() {
    ((ObjectNode) payload.get("user")).remove("iPassRoles");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("iPassRoles");
  }

  @Test
  void emptyListAllowedOnlyForUpdates() {
    ((ObjectNode) payload.get("user")).putArray("iPassRoles");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("at least one");
    payload.put("eventType", "USER_UPDATED");
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
  }

  @Test
  void terminationIgnoresIncompleteProfile() {
    payload.put("eventType", "USER_TERMINATED");
    payload.set("user",
        mapper
          .createObjectNode()
          .put("employeeId", "TEST")
          .put("employmentStatus", "TERMINATED")
          .putNull("manager")
          .put("iPassRoles", "invalid optional content"));
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
  }

  @Test
  void wrongEnvironmentRejected() {
    payload.put("environment", "UAT");
    assertThatThrownBy(() -> validator.envelope(payload)).hasMessageContaining("environment");
  }

  @Test
  void duplicateRoleRejected() {
    var roles = (tools.jackson.databind.node.ArrayNode) payload.get("user").get("iPassRoles");
    roles.add(roles.get(0).deepCopy());
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("Duplicate");
  }

  @Test
  void invalidEmailRejected() {
    ((ObjectNode) payload.get("user")).put("email", "invalid");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("email");
  }

  @Test
  void invalidDateRejected() {
    ((ObjectNode) payload.get("user")).put("hireDate", "2026-02-30");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("YYYY-MM-DD");
  }

  @Test
  void legacyRoleListIsRejected() {
    ObjectNode u = (ObjectNode) payload.get("user");
    u.set("pharosRoles", u.remove("iPassRoles"));
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("iPassRoles");
  }

  @Test
  void accountIsRequired() {
    ((ObjectNode) payload.get("user")).remove("account");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("account");
  }

  @Test
  void flagsMustBeBooleans() {
    ((ObjectNode) payload.get("user").get("iPassRoles").get(0)).put("isPrivileged", "FALSE");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("JSON boolean");
  }

  @Test
  void duplicateOwnerIsRejected() {
    var owners = (tools.jackson.databind.node.ArrayNode) payload.get("user").get("iPassRoles").get(0).get("owners");
    owners.add(owners.get(0).deepCopy());
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("Duplicate ownerId");
  }

  @Test
  void emptyOwnerListIsValidAndExplicit() {
    ((ObjectNode) payload.get("user").get("iPassRoles").get(0)).putArray("owners");
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
  }

  @Test
  void nonPharosEntitlementsAreRejected() {
    ((ObjectNode) payload.get("user").get("iPassRoles").get(0)).put("isPharosGroup", false);
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("Pharos entitlements");
  }
}
