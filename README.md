# Pharos User Access Management

Java 21 · Spring Boot · Maven · PostgreSQL · Spring JDBC

## Run

```bash
docker compose up -d
mvn spring-boot:run
```

PostgreSQL runs all `ddl/*.sql` files in filename order when creating the database. There is one create script for the six tables. No upgrade scripts, migration tools, authentication, or special Spring profile are required.

After editing the schema, recreate the development database:

```bash
docker compose down -v
docker compose up -d
```

This removes the local database data and rebuilds the schema. A normal restart keeps existing data and does not rerun initialization scripts.

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
- Startup DDL: `ddl/01_create_schema.sql`
- Data model guide: [docs/data-model.md](docs/data-model.md)
- ER diagram and scenarios: `docs/`

The API and database bind to localhost. Compose uses `${VARIABLE:-default}`; Spring configuration uses `${VARIABLE:default}`. Environment variable overrides are listed in `.env.example`. The JVM does not automatically load `.env`; export the same variables when overriding defaults.

## API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | /api/v1/ipass/events | Provision, update, or terminate an employee |
| GET | /api/v1/employees/{employeeId} | Profile and assignment history |
| GET | /api/v1/ipass/events/{eventId} | Event processing result |

New event: new UUID. Retry: identical UUID and payload. Send complete approved `iPassRoles` for the supplied `account`. Omitted roles are removed from that account only; [] removes all assignments for that account. Missing/null role lists are rejected. Manager details (including displayName), employeeClass and account are required for active profiles. Termination requires employee identity and TERMINATED status, closes assignments for every account in this deployment, and is independent of iPass's 15-day cleanup.

Employee/assignment changes and successful event status commit together. Failures roll back and are recorded separately. Duplicate successful events make no changes. A terminated employee requires an approved USER_PROVISIONED event to restore access.

No eventVersion is used. iPass must preserve employee event order, including retries. Employee and role identifiers must be stable across source systems. One Pharos environment per deployment.

Granular permission editing and external session revocation are separate integrations.

## Tests

```bash
mvn test
```

For HTTP/PostgreSQL integration tests, start the separate disposable test database:

```bash
docker compose --profile test up -d --wait postgres-test
mvn verify
```

The test database runs the same startup DDL automatically when initialized. Tests truncate only the dedicated database whose name ends in `_test`. They never run DDL against the main database. TEST_DB_URL, TEST_DB_USER and TEST_DB_PASSWORD override test connections. The test database uses temporary storage.

## Account and role metadata

Six tables: employee, okta_account, ipass_role, ipass_role_owner, account_role_assignment and ipass_event. An account is uniquely identified by application/environment/oktaAccountId and cannot move to another employee. Entitlements are uniquely identified by application/environment/entitlementValue. The previous pharosRoles and source roleId fields are not accepted; use iPassRoles and entitlementValue.

Each supplied role carries a complete owners list. [] clears owners; missing/null is invalid. Owners belong to shared role definitions, not assignments. Role removal never deletes owners. iPass must provide current role metadata when sending or manually retriggering a snapshot; shared metadata is applied in processing order without independent role versioning.

isAvailableForRequest and isPrivileged are stored role metadata and never activate permissions. isPrivileged is placed at role level under the agreed sample; confirm its source meaning before production integration. Existing entitlement names require review before renaming; displayName, description, flags and owners can update normally.

Employee class is preserved as source text. Human employee/contractor ingestion still requires a Workday/VNDLY identity and profile. Robot/service-account lifecycle is not implemented by inventing employment data.

Database setup contains no triggers or stored functions. Optional query indexes are deferred until needed. Only key/uniqueness indexes are retained; updated_at is maintained by application SQL.

The single OpenAPI source is `src/main/resources/static/openapi/ipass.json`, served at `/openapi/ipass.json`.

Assignment `removed_at` records when Pharos closed the assignment, including immediate termination. It does not confirm that iPass removed its membership.

Every HTTP response includes a Pharos-generated UUID in `X-Correlation-ID`. The same value appears in request logs and error bodies. Each retry gets a new correlation ID; keep the original eventId for duplicate protection. No correlation request header is required.

Internal account, role and assignment primary keys are UUIDs generated by PostgreSQL using gen_random_uuid(). iPass continues to supply oktaAccountId and entitlementValue as source strings.
