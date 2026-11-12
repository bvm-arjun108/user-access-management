# Pharos User Access Management

Java 21 · Spring Boot · Maven · PostgreSQL · Spring JDBC

## Run

```bash
docker compose up -d --wait postgres
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U uam -d uam < ddl/01_create_schema.sql
mvn spring-boot:run
```

For an empty database, run the DDL once before starting the API. Existing four-table databases must instead run `ddl/02_upgrade_existing_schema.sql` with ingestion stopped; see `ddl/README.md`. The API does not initialize or migrate the database. No authentication or special Spring profile is required.

| Setting | Default |
| --- | --- |
| Database | uam |
| Username | uam |
| Password | uamLocal |
| PostgreSQL port | 5440 |
| API port | 8086 |

- Swagger: http://localhost:8086/swagger-ui.html
- Health: http://localhost:8086/actuator/health
- Postman requests: `postman/`
- Manual DDL: `ddl/01_create_schema.sql`
- ER diagram and scenarios: `docs/`

The API and database bind to localhost. Compose uses `${VARIABLE:-default}`; Spring configuration uses `${VARIABLE:default}`. Environment variable overrides are listed in `.env.example`. The JVM does not automatically load `.env`; export the same variables when overriding defaults.

**Existing Docker volume:** changing POSTGRES_DB/USER/PASSWORD does not change an already initialized database. Existing volumes keep their data and original credentials. Update that database manually or point Compose at a separate empty volume. Do not delete an existing volume unless its data is disposable.

## API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | /api/v1/ipass/events | Provision, update, or terminate an employee |
| GET | /api/v1/employees/{employeeId} | Profile and assignment history |
| GET | /api/v1/ipass/events/{eventId} | Event processing result |

New event: new UUID. Retry: identical UUID and payload. Send complete approved `iPassRoles` for the supplied `account`. Omitted roles are removed from that account only; [] removes all assignments for that account. Missing/null role lists are rejected. Manager details (including displayName), employeeClass and account are required for active profiles. Termination requires employee identity and TERMINATED status, closes assignments for every account in this deployment, and is independent of iPass's 15-day cleanup.

Employee/assignment changes and successful event status commit together. Failures roll back and are recorded separately. Duplicate successful events make no changes. A terminated employee requires an approved USER_PROVISIONED event to restore access.

No eventVersion is used. iPass must preserve employee event order, including retries. Employee and role identifiers must be stable across source systems. One Pharos environment per deployment.

Granular permission editing and external session revocation are separate integrations. New roles remain PENDING until their Pharos mapping is configured.

## Tests

```bash
mvn test
```

For HTTP/PostgreSQL integration tests, prepare the separate disposable test database manually:

```bash
docker compose --profile test up -d --wait postgres-test
docker compose exec -T postgres-test psql -v ON_ERROR_STOP=1 -U uam_test -d pharos_uam_test < ddl/01_create_schema.sql
mvn verify
```

Apply test DDL once per empty test database. Tests truncate only the dedicated database whose name ends in `_test`. They never run DDL against the main database. TEST_DB_URL, TEST_DB_USER and TEST_DB_PASSWORD override test connections. The test database uses temporary storage.

## Account and role metadata

Six tables: employee, account, role, role_owner, account_role and ipass_event. An account is uniquely identified by application/environment/accountId and cannot move to another employee. Entitlements are uniquely identified by application/environment/entitlementValue. The previous pharosRoles and source roleId fields are not accepted; use iPassRoles and entitlementValue.

Each supplied role carries a complete owners list. [] clears owners; missing/null is invalid. Owners belong to shared role definitions, not assignments. Role removal never deletes owners. iPass must provide current role metadata when sending or manually retriggering a snapshot; shared metadata is applied in processing order without independent role versioning.

isAvailableForRequest and isPrivileged are stored role metadata and never activate permissions. isPrivileged is placed at role level under the agreed sample; confirm its source meaning before production integration. Existing entitlement names require review before renaming; displayName, description, flags and owners can update normally. New catalog entries remain PENDING.

Employee class is preserved as source text. Human employee/contractor ingestion still requires a Workday/VNDLY identity and profile. Robot/service-account lifecycle is not implemented by inventing employment data.
