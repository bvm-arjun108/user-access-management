package com.wu.pharos.uam.ingestion;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmployeeLifecycleIT {
  @LocalServerPort
  int port;
  @Autowired
  ObjectMapper mapper;
  @Autowired
  JdbcTemplate jdbc;
  private final HttpClient http = HttpClient.newHttpClient();
  private ObjectNode initial;

  @BeforeEach
  void setup() throws Exception {
    String db = jdbc.queryForObject("SELECT current_database()", String.class);
    if (db == null || !db.endsWith("_test")) {
      throw new IllegalStateException("Integration tests require a dedicated database ending _test");
    }
    jdbc.execute(
        "TRUNCATE pharos_uam.account_role,pharos_uam.role_owner,pharos_uam.account,pharos_uam.employee,pharos_uam.role,pharos_uam."
        + "ipass_event RESTART IDENTITY CASCADE");
    initial = (ObjectNode) mapper.readTree(getClass().getResourceAsStream("/provision.json"));
    initial.put("eventId", UUID.randomUUID().toString());
  }

  record Reply(int status, JsonNode body) {}

  private Reply post(ObjectNode payload) throws Exception {
    HttpResponse<String> r = http.send(HttpRequest
          .newBuilder(URI.create("http://localhost:" + port + "/api/v1/ipass/events"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
          .build(), HttpResponse.BodyHandlers.ofString());
    return new Reply(r.statusCode(), mapper.readTree(r.body()));
  }

  private ObjectNode next(String type) {
    ObjectNode p = initial.deepCopy();
    p.put("eventId", UUID.randomUUID().toString());
    p.put("eventType", type);
    return p;
  }

  private ObjectNode termination() {
    ObjectNode p = next("USER_TERMINATED");
    p.put("occurredAt", "2026-08-31T17:00:00Z");
    p.set("user",
        mapper.createObjectNode().put("employeeId", "WU100482").put("employmentStatus", "TERMINATED").put("terminationDate", "2026-08-31"));
    return p;
  }

  private long active() {
    return jdbc.queryForObject("SELECT count(*) FROM pharos_uam.account_role WHERE removed_at IS NULL", Long.class);
  }

  @Test
  void lifecyclePreservesAssignmentHistory() throws Exception {
    Reply first = post(initial);
    assertThat(first.status()).isEqualTo(200);
    assertThat(active()).isEqualTo(3);
    assertThat(first.body().size()).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT processing_status FROM pharos_uam.ipass_event WHERE event_id=?", String.class,
        UUID.fromString(initial.get("eventId").asString())))
      .isEqualTo("APPLIED");
    assertThat(first.body().get("status").asString()).isEqualTo("SUCCESS");
    ObjectNode update = next("USER_UPDATED");
    ((ArrayNode) update.get("user").get("iPassRoles")).remove(1);
    assertThat(post(update).status()).isEqualTo(200);
    assertThat(active()).isEqualTo(2);
    ObjectNode add = next("USER_UPDATED");
    ArrayNode roles = (ArrayNode) add.get("user").get("iPassRoles");
    roles.remove(1);
    ObjectNode role = (ObjectNode) roles.get(0).deepCopy();
    role.put("entitlementValue", "00gProdGddAnalyst001");
    role.put("roleName", "APP_Pharos_Prod_GDD_Analyst");
    role.put("roleDescription", "GDD Analyst");
    role.put("assignedAt", "2026-07-30T11:00:00Z");
    roles.add(role);
    add.put("occurredAt", "2026-07-30T11:00:00Z");
    assertThat(post(add).status()).isEqualTo(200);
    assertThat(active()).isEqualTo(3);
    assertThat(post(termination()).status()).isEqualTo(200);
    assertThat(active()).isZero();
    assertThat(jdbc.queryForObject("SELECT employment_status FROM pharos_uam.employee", String.class)).isEqualTo("TERMINATED");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.account_role", Long.class)).isEqualTo(4);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.role", Long.class)).isEqualTo(4);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.ipass_event WHERE processing_status='APPLIED'", Long.class)).isEqualTo(
        4);
  }

  @Test
  void duplicateAndChangedPayloadAreDifferent() throws Exception {
    Reply first = post(initial);
    Reply duplicate = post(initial);
    assertThat(duplicate.status()).isEqualTo(200);
    assertThat(duplicate.body()).isEqualTo(first.body());
    ((ObjectNode) initial.get("user")).put("site", "New York");
    assertThat(post(initial).status()).isEqualTo(409);
    assertThat(active()).isEqualTo(3);
  }

  @Test
  void missingManagerFailsWithoutPartialState() throws Exception {
    ((ObjectNode) initial.get("user")).putNull("manager");
    assertThat(post(initial).status()).isEqualTo(400);
    assertThat(active()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.employee", Long.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT processing_status FROM pharos_uam.ipass_event", String.class)).isEqualTo("FAILED");
  }

  @Test
  void roleConflictRollsBackProfileAndOtherRoleChanges() throws Exception {
    post(initial);
    ObjectNode update = next("USER_UPDATED");
    ((ObjectNode) update.get("user")).put("site", "New York");
    ((ObjectNode) update.get("user").get("iPassRoles").get(2)).put("roleName", "APP_Pharos_Prod_BKYC_Reviewer");
    assertThat(post(update).status()).isEqualTo(409);
    assertThat(jdbc.queryForObject("SELECT site FROM pharos_uam.employee", String.class)).isEqualTo("Denver");
    assertThat(active()).isEqualTo(3);
  }

  @Test
  void finalRoleRemovalAndFreshAssignment() throws Exception {
    post(initial);
    ObjectNode empty = next("USER_UPDATED");
    ((ObjectNode) empty.get("user")).putArray("iPassRoles");
    assertThat(post(empty).status()).isEqualTo(200);
    assertThat(active()).isZero();
    ObjectNode reassign = next("USER_UPDATED");
    reassign.put("occurredAt", "2026-08-12T12:00:00Z");
    ArrayNode roles = ((ObjectNode) reassign.get("user")).putArray("iPassRoles");
    ObjectNode role = (ObjectNode) initial.get("user").get("iPassRoles").get(0).deepCopy();
    role.put("assignedAt", "2026-08-12T12:00:00Z");
    roles.add(role);
    assertThat(post(reassign).status()).isEqualTo(200);
    assertThat(active()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.account_role", Long.class)).isEqualTo(4);
  }

  @Test
  void terminationBeforeProvisioningAndApprovedRestoration() throws Exception {
    assertThat(post(termination()).status()).isEqualTo(200);
    assertThat(post(next("USER_UPDATED")).status()).isEqualTo(409);
    ObjectNode restore = next("USER_PROVISIONED");
    restore.put("occurredAt", "2026-09-02T09:00:00Z");
    for (JsonNode r : restore.get("user").get("iPassRoles")) {
      ((ObjectNode) r).put("assignedAt", "2026-09-02T09:00:00Z");
    }
    assertThat(post(restore).status()).isEqualTo(200);
    assertThat(jdbc.queryForObject("SELECT employment_status FROM pharos_uam.employee", String.class)).isEqualTo("ACTIVE");
    assertThat(jdbc.queryForObject("SELECT termination_date FROM pharos_uam.employee", java.sql.Date.class)).isNull();
  }

  @Test
  void terminationIgnoresRoleAndManagerGarbage() throws Exception {
    post(initial);
    ObjectNode p = termination();
    ((ObjectNode) p.get("user")).put("iPassRoles", "not a list").putNull("manager");
    Reply response = post(p);
    assertThat(response.status()).isEqualTo(200);
    assertThat(active()).isZero();
    assertThat(response.body().get("status").asString()).isEqualTo("SUCCESS");
  }

  @Test
  void concurrentIdenticalDeliveriesCreateOneSetOfAssignments() throws Exception {
    List<CompletableFuture<Reply>> calls = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      calls.add(CompletableFuture.supplyAsync(() -> {
        try {
          return post(initial);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }));
    }
    var replies = calls.stream().map(CompletableFuture::join).toList();
    assertThat(replies).allMatch(r -> r.status() == 200);
    assertThat(replies).allMatch(r -> r.body().get("status").asString().equals("SUCCESS"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.ipass_event WHERE processing_status='APPLIED'", Long.class)).isEqualTo(
        1);
    assertThat(active()).isEqualTo(3);
  }

  @Test
  void failedEventCanBeRetriedAfterDependencyIsCorrected() throws Exception {
    post(initial);
    ObjectNode update = next("USER_UPDATED");
    ((ObjectNode) update.get("user").get("iPassRoles").get(0)).put("roleName", "APP_Pharos_Prod_KYC_Reviewer");
    assertThat(post(update).status()).isEqualTo(409);
    jdbc.update("UPDATE pharos_uam.role SET role_name='APP_Pharos_Prod_KYC_Reviewer' WHERE entitlement_value='00gProdKycAnalyst001'");
    assertThat(post(update).status()).isEqualTo(200);
    assertThat(jdbc.queryForObject("SELECT processing_status FROM pharos_uam.ipass_event WHERE event_id=?", String.class,
        UUID.fromString(update.get("eventId").asString())))
      .isEqualTo("APPLIED");
  }

  @Test
  void accountAndRoleEnvironmentAreEnforcedByDatabase() throws Exception {
    post(initial);
    jdbc.update("UPDATE pharos_uam.account_role SET removed_at=now(),removed_event_id=created_event_id");
    assertThatThrownBy(() -> jdbc.update("INSERT INTO pharos_uam.account_role(account_id,role_id,application,environment,assigned_at,"
        + "created_event_id) SELECT a.account_id,r.role_id,'Okta','UAT',now(),? FROM pharos_uam.account a CROSS JOIN pharos_uam.role "
        + "r LIMIT 1", UUID.fromString(initial.get("eventId").asString())))
      .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
      .hasMessageContaining("fk_account_role_account");
  }

  @Test
  void updatingOneAccountDoesNotRemoveAnotherAccountsRoles() throws Exception {
    post(initial);
    ObjectNode second = next("USER_UPDATED");
    ((ObjectNode) second.get("user").get("account")).put("accountId", "00u-second");
    assertThat(post(second).status()).isEqualTo(200);
    assertThat(active()).isEqualTo(6);
    ObjectNode empty = next("USER_UPDATED");
    ((ObjectNode) empty.get("user")).putArray("iPassRoles");
    assertThat(post(empty).status()).isEqualTo(200);
    assertThat(active()).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.account", Long.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.role", Long.class)).isEqualTo(3);
  }

  @Test
  void accountCannotMoveToAnotherEmployee() throws Exception {
    post(initial);
    ObjectNode other = next("USER_PROVISIONED");
    ((ObjectNode) other.get("user")).put("employeeId", "OTHER-EMPLOYEE");
    Reply result = post(other);
    assertThat(result.status()).isEqualTo(409);
    assertThat(result.body().get("code").asString()).isEqualTo("ACCOUNT_IDENTITY_CONFLICT");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.employee", Long.class)).isEqualTo(1);
  }

  @Test
  void ownersAndFlagsAreSharedRoleMetadataNotAccessGrants() throws Exception {
    post(initial);
    ObjectNode update = next("USER_UPDATED");
    ObjectNode role = (ObjectNode) update.get("user").get("iPassRoles").get(0);
    role.put("isAvailableForRequest", false).put("isPrivileged", true);
    role
      .putArray("owners")
      .add(mapper
        .createObjectNode()
        .put("ownerId", "ROLE-OWNER-NEW")
        .put("firstName", "Elena")
        .put("lastName", "Garcia")
        .put("email", "elena@example.com"));
    assertThat(post(update).status()).isEqualTo(200);
    assertThat(active()).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT owner_id FROM pharos_uam.role_owner o JOIN pharos_uam.role r USING(role_id) WHERE r."
        + "entitlement_value='00gProdKycAnalyst001'", String.class))
      .isEqualTo("ROLE-OWNER-NEW");
    assertThat(jdbc.queryForObject("SELECT is_privileged FROM pharos_uam.role WHERE entitlement_value='00gProdKycAnalyst001'", Boolean.class))
      .isTrue();
    assertThat(jdbc.queryForObject("SELECT permission_mapping_status FROM pharos_uam.role WHERE entitlement_value='00gProdKycAnalyst001'",
        String.class))
      .isEqualTo("PENDING");
  }

  @Test
  void removedAssignmentsNeverDeleteRoleOwners() throws Exception {
    post(initial);
    ObjectNode update = next("USER_UPDATED");
    ((ObjectNode) update.get("user")).putArray("iPassRoles");
    post(update);
    assertThat(active()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.role_owner", Long.class)).isEqualTo(3);
  }

  @Test
  void terminationClosesAllAccountsAndRestorationOnlyRestoresSuppliedAccount() throws Exception {
    post(initial);
    ObjectNode second = next("USER_UPDATED");
    ((ObjectNode) second.get("user").get("account")).put("accountId", "00u-second");
    post(second);
    assertThat(post(termination()).status()).isEqualTo(200);
    assertThat(active()).isZero();
    ObjectNode restore = next("USER_PROVISIONED");
    restore.put("occurredAt", "2026-09-02T09:00:00Z");
    for (JsonNode role : restore.get("user").get("iPassRoles")) {
      ((ObjectNode) role).put("assignedAt", "2026-09-02T09:00:00Z");
    }
    assertThat(post(restore).status()).isEqualTo(200);
    assertThat(active()).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.account_role ar JOIN pharos_uam.account a USING(account_id) WHERE a."
        + "external_account_id='00u-second' AND ar.removed_at IS NULL", Long.class))
      .isZero();
  }

  @Test
  void sameExternalIdentifiersAreSeparateAcrossApplications() throws Exception {
    post(initial);
    ObjectNode second = next("USER_UPDATED");
    ((ObjectNode) second.get("user").get("account")).put("application", "OktaSecondary");
    assertThat(post(second).status()).isEqualTo(200);
    assertThat(active()).isEqualTo(6);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pharos_uam.role", Long.class)).isEqualTo(6);
  }

  @Test
  void employeeReadIncludesAccountsEntitlementsAndOwners() throws Exception {
    post(initial);
    var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/employees/WU100482")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode body = mapper.readTree(response.body());
    assertThat(body.get("accounts").size()).isEqualTo(1);
    JsonNode account = body.get("accounts").get(0);
    assertThat(account.get("assignments").size()).isEqualTo(3);
    assertThat(account.get("assignments").get(0).get("owners").size()).isEqualTo(1);
    assertThat(body.get("employee").get("employee_class").asString()).isEqualTo("Employee");
    assertThat(body.get("employee").get("manager_display_name").asString()).isEqualTo("Maya Patel");
  }

  @Test
  void eventLookupContainsOnlyIngestionDetails() throws Exception {
    post(initial);
    var response = http.send(HttpRequest
          .newBuilder(URI.create("http://localhost:" + port + "/api/v1/ipass/events/" + initial.get("eventId").asString()))
          .GET()
          .build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode body = mapper.readTree(response.body());
    assertThat(body.get("processingStatus").asString()).isEqualTo("APPLIED");
    assertThat(body.has("warnings")).isFalse();
    assertThat(response.body()).doesNotContain("ROLE_MAPPING_PENDING");
  }

  @Test
  void timestampsAreUpdatedByApplicationWithoutTriggers() throws Exception {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c."
        + "relnamespace WHERE n.nspname='pharos_uam' AND NOT t.tgisinternal", Long.class))
      .isZero();
    assertThat(post(initial).status()).isEqualTo(200);
    String times =
        "SELECT e.updated_at,a.updated_at,r.updated_at,o.updated_at FROM pharos_uam.employee e JOIN pharos_uam.account a "
        + "USING(employee_id) JOIN pharos_uam.role r ON r.application=a.application AND r.environment=a.environment JOIN pharos_uam."
        + "role_owner o USING(role_id) WHERE r.entitlement_value='00gProdKycAnalyst001'";
    var before = jdbc.queryForObject(times, (rs, row) -> java.util.List.of(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant(),
        rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant()));
    ObjectNode update = next("USER_UPDATED");
    ((ObjectNode) update.get("user")).put("site", "New York");
    assertThat(post(update).status()).isEqualTo(200);
    var after = jdbc.queryForObject(times, (rs, row) -> java.util.List.of(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant(),
        rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant()));
    for (int j = 0; j < 4; j++) {
      assertThat(after.get(j)).isAfter(before.get(j));
    }
    assertThat(post(termination()).status()).isEqualTo(200);
    assertThat(jdbc.queryForObject("SELECT updated_at FROM pharos_uam.employee", java.sql.Timestamp.class).toInstant()).isAfter(after.get(0));
  }
}
