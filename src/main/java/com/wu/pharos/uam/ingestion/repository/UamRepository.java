package com.wu.pharos.uam.ingestion.repository;

import com.wu.pharos.uam.common.error.ApiException;
import com.wu.pharos.uam.common.jdbc.SqlResourceLoader;
import com.wu.pharos.uam.ingestion.model.EmployeeEvent;
import com.wu.pharos.uam.ingestion.service.EventValidator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class UamRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final SqlResourceLoader sql;
  private final ObjectMapper mapper;

  public UamRepository(NamedParameterJdbcTemplate jdbc, SqlResourceLoader sql, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.sql = sql;
    this.mapper = mapper;
  }

  public record EventRow(UUID id, String status, Instant processedAt, boolean payloadMatches) {}

  private MapSqlParameterSource params(EmployeeEvent e) {
    return new MapSqlParameterSource()
      .addValue("id", e.eventId())
      .addValue("type", e.eventType().name())
      .addValue("employee", e.employeeId())
      .addValue("occurred", OffsetDateTime.ofInstant(e.eventEffectiveAt(), java.time.ZoneOffset.UTC))
      .addValue("payload", e.payload().toString())
      .addValue("oktaAccount", optionalAccountId(e));
  }

  private String optionalAccountId(EmployeeEvent e) {
    JsonNode value = e.user().get("oktaAccountId");
    return value != null && value.isString() ? value.asString() : null;
  }

  public void receive(EmployeeEvent e) {
    jdbc.update(sql.load("event/insert-receipt"), params(e));
  }

  public EventRow event(EmployeeEvent e, boolean lock) {
    return jdbc.queryForObject(sql.load(lock ? "event/find-locked" : "event/find"), params(e), this::row);
  }

  private EventRow row(ResultSet rs, int i) throws SQLException {
    OffsetDateTime time = rs.getObject("processed_at", OffsetDateTime.class);
    return new EventRow(rs.getObject("event_id", UUID.class), rs.getString("processing_status"), time == null ? null : time.toInstant(),
        rs.getBoolean("payload_matches"));
  }

  public void lockEmployee(String employee) {
    jdbc.query(sql.load("event/employee-lock"), Map.of("employee", employee), (rs, i) -> true);
  }

  public String status(String employee) {
    return jdbc
      .query(sql.load("employee/status"), Map.of("employee", employee), (rs, i) -> rs.getString(1))
      .stream()
      .findFirst()
      .orElse(null);
  }

  public void saveProfile(EmployeeEvent e) {
    JsonNode u = e.user();
    Map<String, Object> p = new LinkedHashMap<>();
    String[][] fields = {{"employeeId", "employee_id"}, {"sourceSystem", "source_system"}, {"firstName", "first_name"},
        {"middleName", "middle_name"}, {"lastName", "last_name"}, {"email", "email"}, {"employmentStatus", "employment_status"},
        {"employeeClass", "employee_class"}, {"supplier", "supplier"}, {"companyName", "company_name"}, {"country", "country"},
        {"businessUnit", "business_unit"}, {"city", "city"}};
    for (String[] field : fields) {
      p.put(field[1], !u.hasNonNull(field[0]) ? null : u.get(field[0]).asString());
    }
    p.put("hire_date", EventValidator.date(u, "hireDate"));
    p.put("termination_date", null);
    p.put("employee_last_synced_at", OffsetDateTime.ofInstant(EventValidator.timestamp(u, "employeeLastSyncedAt"), java.time.ZoneOffset.UTC));
    JsonNode m = u.get("manager");
    String[][] manager = {{"employeeId", "manager_employee_id"}, {"firstName", "manager_first_name"},
        {"middleName", "manager_middle_name"}, {"lastName", "manager_last_name"}};
    for (String[] field : manager) {
      p.put(field[1], m.get(field[0]).isNull() ? null : m.get(field[0]).asString());
    }
    JsonNode job = u.get("jobDetails");
    String[][] jobFields = {{"department", "department"}, {"jobCode", "job_code"}, {"jobTitle", "job_title"},
        {"jobFamilyGroup", "job_family_group"}, {"jobFamily", "job_family"}};
    for (String[] field : jobFields) {
      p.put(field[1], job.get(field[0]).isNull() ? null : job.get(field[0]).asString());
    }
    jdbc.update(sql.load("employee/upsert"), p);
  }

  public void terminate(EmployeeEvent e) {
    JsonNode d = e.user().get("terminationDate");
    jdbc.update(sql.load("employee/terminate"),
        new MapSqlParameterSource("employee", e.employeeId())
          .addValue("date", d == null || d.isNull() ? null : EventValidator.date(e.user(), "terminationDate")));
  }

  public UUID saveAccount(EmployeeEvent e) {
    List<UUID> result = jdbc.query(sql.load("account/upsert"), Map.of("employee", e.employeeId(), "external",
        e.user().get("oktaAccountId").asString()), (rs, i) -> rs.getObject(1, UUID.class));
    if (result.isEmpty()) {
      throw ApiException.conflict("ACCOUNT_IDENTITY_CONFLICT", "Account is already linked to a different employee");
    }
    return result.getFirst();
  }

  public record RoleRow(UUID id) {}

  public RoleRow upsertRole(JsonNode r, EmployeeEvent e) {
    var p = new MapSqlParameterSource()
      .addValue("entitlement", r.get("entitlementValue").asString())
      .addValue("entitlementType", r.get("entitlementType").asString())
      .addValue("name", r.get("roleName").asString())
      .addValue("displayName", r.get("displayName").asString())
      .addValue("description", r.get("roleDescription").asString())
      .addValue("pharos", r.get("isPharosGroup").asBoolean())
      .addValue("requestable", r.get("isAvailableForRequest").asBoolean())
      .addValue("privileged", r.get("isPrivileged").asBoolean());
    List<RoleRow> result = jdbc.query(sql.load("role/upsert"), p, (rs, i) -> new RoleRow(rs.getObject("role_id", UUID.class)));
    if (result.isEmpty()) {
      throw ApiException.conflict("ROLE_IDENTITY_CONFLICT", "Existing entitlement has a different roleName; review the role definition");
    }
    return result.getFirst();
  }

  public void retainOrCreateAssignment(EmployeeEvent e, UUID account, UUID role, JsonNode source) {
    Instant assigned = EventValidator.timestamp(source, "assignedAt").truncatedTo(ChronoUnit.MICROS);
    List<Instant> existing = jdbc.query(sql.load("assignment/current"), Map.of("account", account, "role", role), (rs, i) -> rs
      .getObject(1, OffsetDateTime.class)
      .toInstant());
    if (!existing.isEmpty()) {
      if (!existing.getFirst().equals(assigned)) {
        throw ApiException.rule("assignedAt must remain unchanged for a current assignment");
      }
      return;
    }
    jdbc.update(sql.load("assignment/create"),
        new MapSqlParameterSource("account", account)
          .addValue("role", role)
          .addValue("assigned", OffsetDateTime.ofInstant(assigned, java.time.ZoneOffset.UTC))
          .addValue("event", e.eventId()));
  }

  public void reconcile(EmployeeEvent e, UUID account) {
    jdbc.update(sql.load("assignment/reconcile"),
        Map.of("account", account, "event", e.eventId(), "roles", e.user().get("iPassRoles").toString()));
  }

  public void terminateAssignments(EmployeeEvent e) {
    jdbc.update(sql.load("assignment/terminate"), Map.of("employee", e.employeeId(), "event", e.eventId()));
  }

  public void applied(EmployeeEvent e) {
    jdbc.update(sql.load("event/apply"), Map.of("id", e.eventId()));
  }

  public void failed(EmployeeEvent e, String code, String message) {
    jdbc.update(sql.load("event/fail"), Map.of("id", e.eventId(), "code", code, "message", message));
  }

  public JsonNode employee(String employee, boolean includeHistory) {
    return read("read/employee", Map.of("employee", employee, "includeHistory", includeHistory));
  }

  public JsonNode event(UUID id) {
    return read("read/event", Map.of("id", id));
  }

  private JsonNode read(String query, Map<String, ?> p) {
    return jdbc
      .query(sql.load(query), p, (rs, i) -> mapper.readTree(rs.getString("data")))
      .stream()
      .findFirst()
      .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "Record not found"));
  }
}
