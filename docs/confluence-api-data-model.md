# Pharos UAM — iPass API and data model

## Purpose

iPass sends approved Pharos role assignments and employee details from Workday/VNDLY. Pharos stores the data and assignment history. Granular permission configuration is outside this API.

Send events only for Pharos access or changes to employees already relevant to Pharos. Pending requests and roles for other applications do not require a call.

## APIs

| Method | Endpoint | Result |
| --- | --- | --- |
| POST | /api/v1/ipass/events | Create, update or terminate an employee's Pharos access records |
| GET | /api/v1/employees/{employeeId} | Employee profile, accounts and current assignments |
| GET | /api/v1/employees/{employeeId}?includeHistory=true | Same data, including removed assignments |
| GET | /api/v1/ipass/events/{eventId} | Event identity, timestamps, processing result and failure reason |

includeHistory defaults to false. After all roles are removed, the account remains with an empty assignments array. Employee details in GET currently use database-style snake_case; account and assignment fields use camelCase.

### Headers

| Header | Direction | Rule |
| --- | --- | --- |
| Content-Type: application/json | POST request | Required |
| Accept: application/json | Request | Optional |
| X-Correlation-ID | Every response | Pharos-generated UUID; use for troubleshooting |

The same correlation ID appears in request logs and error bodies. Each retry gets a new correlation ID. Authentication is not implemented in the current local API; production authentication must be agreed before integration.

## Event types

| eventType | Required payload | Effect |
| --- | --- | --- |
| USER_PROVISIONED | Full ACTIVE employee profile, account and at least one approved role | Initial provisioning or explicitly approved restoration |
| USER_UPDATED | Full ACTIVE employee profile, account and complete current role list | Update profile and reconcile this account's assignments |
| USER_TERMINATED | employeeId, TERMINATED employmentStatus; terminationDate when known | Mark employee terminated and close all assignments in this deployment |

### Provisioning example

All identifiers and personal details below are fictional. USER_UPDATED uses the same structure with its own eventId, eventEffectiveAt and complete current role list.

~~~json
{
  "eventId": "9cd02b67-53f5-47c5-ac38-426527f41c72",
  "eventType": "USER_PROVISIONED",
  "eventEffectiveAt": "2026-07-11T14:00:00Z",
  "environment": "Prod",
  "user": {
    "employeeId": "WU100482",
    "sourceSystem": "WORKDAY",
    "firstName": "Arjun",
    "middleName": null,
    "lastName": "Sharma",
    "email": "arjun.sharma@example.com",
    "employmentStatus": "ACTIVE",
    "hireDate": "2026-07-01",
    "terminationDate": null,
    "employmentType": "FTE",
    "superiorOrganization": "Western Union",
    "organizationId": "ORG-FCC-101",
    "country": "USA",
    "region": "North America",
    "site": "Denver",
    "supervisoryOrganization": "Financial Crime Compliance",
    "employeeLastSyncedAt": "2026-07-11T14:00:00Z",
    "manager": {
      "employeeId": "WU100210",
      "firstName": "Maya",
      "middleName": "R",
      "lastName": "Patel",
      "email": "maya.patel@example.com"
    },
    "iPassRoles": [
      {
        "roleName": "APP_Pharos_Prod_KYC_Analyst",
        "roleDescription": "KYC Analyst",
        "assignedAt": "2026-07-11T14:00:00Z",
        "assignmentStatus": "PROVISIONED",
        "entitlementValue": "00g8n4q2r6T9vWxYz0h7",
        "entitlementType": "groups",
        "displayName": "KYC Analyst",
        "isPharosGroup": true,
        "isAvailableForRequest": true,
        "isPrivileged": false,
        "owners": [
          {
            "ownerId": "WU100310",
            "firstName": "Priya",
            "lastName": "Shah",
            "email": "priya.shah@example.com"
          }
        ]
      }
    ],
    "employeeClass": "Employee",
    "account": {
      "application": "Okta",
      "oktaAccountId": "00u8k3m5n7P2qRsTu0h7"
    }
  }
}
~~~

All shown fields are required for provisioning/updates. Middle names may be null; terminationDate must be null for ACTIVE employees. Manager details are required. Every role includes owners; an empty owners array explicitly clears them.

### Termination example

~~~json
{
  "eventId": "ae11e179-b029-4295-91ba-a3917d59d1a0",
  "eventType": "USER_TERMINATED",
  "eventEffectiveAt": "2026-08-31T18:00:00Z",
  "environment": "Prod",
  "user": {
    "employeeId": "WU100482",
    "employmentStatus": "TERMINATED",
    "terminationDate": "2026-08-31"
  }
}
~~~

### Payload rules

- One event identifies one employee and environment; provisioning/updates identify one account.
- Send the complete approved iPassRoles list for that account. Omitted roles are removed from that account only; [] removes all its roles. Missing/null is rejected.
- Role names follow APP_Pharos_Environment_BusinessGroup_Persona and match the target environment.
- entitlementValue identifies the source role. Internal account_id and role_id UUIDs are generated by PostgreSQL.
- Continuing assignments keep their assignedAt. A new assignment period receives a new assignedAt.
- Owners are shared role metadata; removing an assignment never deletes the role or its owners.
- Send termination immediately to every relevant deployment; do not wait for iPass's 15-day cleanup.
- Restoring a terminated employee requires approved USER_PROVISIONED with the full current snapshot. USER_UPDATED cannot reactivate employment.

## Responses and retries

Successful processing and identical retries return HTTP 200:

~~~json
{
  "eventId": "9cd02b67-53f5-47c5-ac38-426527f41c72",
  "status": "SUCCESS"
}
~~~

X-Correlation-ID is in the response header, not the success JSON.

| HTTP status | Meaning |
| --- | --- |
| 200 | Saved successfully or already applied |
| 400 | Invalid JSON, missing field, invalid field type or query parameter |
| 404 | Employee or event not found |
| 409 | Conflicting identity, event UUID reused with changed content, or disallowed reactivation |
| 422 | Payload violates a business rule |
| 503 | Database operation failed |
| 500 | Unexpected application failure |

Error bodies contain timestamp, status, code, message and correlationId.

New event: new UUID. Retry: same UUID and unchanged payload. Corrected content: new UUID. Already-applied retries do not change business records.

Pharos commits business changes and APPLIED status together. Failed business changes roll back; Pharos records the failure separately where possible. Invalid envelopes and database outages may prevent recording an event.

## Data model

| Table | Stores | Primary key | Additional uniqueness |
| --- | --- | --- | --- |
| employee | Current profile, embedded manager and employment status | employee_id | One employee row per source identity |
| okta_account | Employee's scoped Okta account | account_id, generated UUID | application + environment + okta_account_id |
| ipass_role | Shared source role details and flags | role_id, generated UUID | application + environment + entitlement_value |
| ipass_role_owner | Current owners of a role | role_id + owner_id | No duplicate owner within a role |
| okta_account_role_assignment | Current and historical assignment periods | assignment_id, generated UUID | One open assignment per account_id + role_id |
| ipass_event | Submitted payload and latest processing outcome | event_id, UUID from iPass | One row per event UUID |

Relationships: employee → accounts → assignments ← roles → owners. Each assignment also references its assignment event and optional removal event. Foreign keys enforce matching application/environment for the account and role.

Managers and owners do not need their own employee records. Event source identifiers are not foreign keys because receipt can precede employee/account creation.

### Important fields

| Field | Meaning |
| --- | --- |
| employee_last_synced_at | When iPass last refreshed employee data from Workday/VNDLY |
| event_effective_at | When the reported change took effect |
| received_at / processed_at | Pharos's first receipt / completion of the latest processing attempt |
| processing_status | RECEIVED, APPLIED or FAILED; populated by Pharos |
| error_code / error_message | Latest failure reason, populated by Pharos; null on success |
| assigned_at / removed_at | Source assignment start / Pharos assignment closure time |
| assignment_event_id / removal_event_id | Incoming events that created / closed the assignment |
| created_at / updated_at | Pharos record timestamps |

removed_at records closure in Pharos, not confirmation that iPass removed its membership. Closed assignments are retained; reassigning a role creates a new assignment row.

## Environment and delivery agreements

One environment per deployment. Dev, UAT and Prod are currently supported; QA remains pending implementation. iPass sends to the matching environment endpoint. The same Okta ID may exist in different environments because uniqueness includes environment.

iPass must guarantee stable employee IDs across Workday/VNDLY and rehire, ordered employee deliveries including retries, and current shared role/owner metadata. Automatic stale-event rejection is not implemented.

## Reference

- [OpenAPI contract](../src/main/resources/static/openapi/ipass.json)
- [Data model guide](data-model.md)
- [Full ER diagram](pharos-ingestion-full-erd.md)
- [DDL](../ddl/01_create_schema.sql)
- [Postman collection](../postman/Pharos-UAM.postman_collection.json)
