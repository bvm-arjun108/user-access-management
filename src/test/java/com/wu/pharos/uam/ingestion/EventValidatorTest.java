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
    validator = new EventValidator(Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC),
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
  void environmentIsNotPartOfSourceContract() {
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
    ((ObjectNode) payload.get("user")).remove("oktaAccountId");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("oktaAccountId");
  }

  @Test
  void flagsMustBeBooleans() {
    ((ObjectNode) payload.get("user").get("iPassRoles").get(0)).put("isPrivileged", "FALSE");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("JSON boolean");
  }

  @Test
  void nonPharosEntitlementsAreRejected() {
    ((ObjectNode) payload.get("user").get("iPassRoles").get(0)).put("isPharosGroup", false);
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("Pharos entitlements");
  }

  @Test
  void employeeEmailMayBeNullOrOmitted() {
    ObjectNode user = (ObjectNode) payload.get("user");
    user.putNull("email");
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
    user.remove("email");
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
    user.put("email", "");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("email");
  }

  @Test
  void roleOwnerDataIsRejectedRatherThanMisclassified() {
    ((ObjectNode) payload.get("user").get("iPassRoles").get(0)).putArray("owners");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("Unknown field");
  }

  @Test
  void managerNamesPreserveCompoundLastNameAndAllowNullMiddleName() {
    ((ObjectNode) payload.get("user").get("manager")).put("lastName", "van der Patel").putNull("middleName");
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
    ((ObjectNode) payload.get("user").get("manager")).remove("firstName");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("firstName");
  }

  @Test
  void sourceRoleNameDoesNotNeedEnvironmentOrPharosNamingConvention() {
    ((ObjectNode) payload.get("user").get("iPassRoles").get(0)).put("roleName", "Pharos Standard Investigator");
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
  }

  @Test
  void jobFieldsAllowNullButRequireAllKeys() {
    ObjectNode job = (ObjectNode) payload.get("user").get("jobDetails");
    job.putNull("department").putNull("jobCode").putNull("jobTitle").putNull("jobFamilyGroup").putNull("jobFamily");
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
    job.remove("jobCode");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("jobCode");
  }

  @Test
  void supplierIsOptionalAndAcceptsOnlyNonblankTextOrNull() {
    ObjectNode user = (ObjectNode) payload.get("user");
    user.remove("supplier");
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
    user.putNull("supplier");
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
    user.put("supplier", "Example Technology Services");
    assertThatCode(() -> validator.validate(validator.envelope(payload))).doesNotThrowAnyException();
    user.put("supplier", " ");
    assertThatThrownBy(() -> validator.validate(validator.envelope(payload))).hasMessageContaining("supplier");
  }
}
