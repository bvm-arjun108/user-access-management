# Pharos UAM — iPass integration

iPass sends employee details and approved Pharos role assignments. Pharos stores the employee, each Okta account and its role history.

## Connection

| Item | Value |
| --- | --- |
| Endpoint | `POST {{PHAROS_BASE_URL}}/api/v1/ipass/events` |
| Request headers | `Content-Type: application/json`; optional `Accept: application/json` |
| Authentication | To be agreed before shared deployment. The current implementation has no authentication. |
| Troubleshooting | Pharos returns `X-Correlation-ID` on every response. Save it with the event ID. |

## When to send an event

| Event | Send when | Payload / result |
| --- | --- | --- |
| `USER_PROVISIONED` | First approved Pharos access, approved rehire or termination correction. | Full ACTIVE profile, one account and at least one current approved role. |
| `USER_UPDATED` | Roles or employee details change; another account is added for an active employee. | Full ACTIVE profile, one account and its complete current role list. An empty list removes all roles from that account. |
| `USER_TERMINATED` | Employment termination becomes effective. | Employee ID and TERMINATED status; termination date when known. Closes every current role assignment for that employee across all stored accounts. |

Do not send hires without Pharos access, pending requests or unrelated application roles. Send termination immediately, including for previously provisioned employees with no remaining roles; do not wait for the 15-day iPass cleanup. USER_UPDATED cannot restore terminated employment.

## Payload rules

| Rule | Requirement |
| --- | --- |
| Account scope | One employee and one account per provisioning/update call. Send separate calls for distinct Okta accounts. |
| Complete role list | `user.iPassRoles` contains all current Pharos roles for that account. Omitted roles are removed. Missing/null is rejected. Maximum 500 roles. |
| Identity | Employee: `employeeId` (source Owner ID). Account: `application` + `oktaAccountId`. Role: `application` + `entitlementValue`. Keep identifiers stable and preserve their case. |
| Environment / owners | No environment field or inference. Source Owner First/Last describe the employee; role owners are not supplied. |
| Employee fields | Use every field in the full example. Only `email` may be omitted; it may also be null. `middleName` may be null. ACTIVE requires `terminationDate: null`. Manager ID and full name are required. |
| Role fields | Every shown role field is required. `assignmentStatus` is always PROVISIONED; `isPharosGroup` must be true. Send JSON booleans, not strings. Requestability/privilege flags are metadata. |
| Dates | Dates: YYYY-MM-DD. Timestamps: RFC 3339 with timezone. `eventEffectiveAt` is when the change took effect; `employeeLastSyncedAt` is when iPass refreshed employee details from Workday/VNDLY. |
| Assignment time | `assignedAt` is the original start of the current assignment. Preserve it while membership continues; it cannot follow `eventEffectiveAt`. |
| Validation | Send nonblank strings without surrounding whitespace. Do not send future-effective changes. Full field types and limits are in Swagger. |

## Request examples

Fictional data. USER_UPDATED uses the full provisioning structure with a new event ID, the effective time of the change and the complete current account snapshot. To remove every role, send that full structure with `iPassRoles: []`.

<details>
<summary>Full provisioning payload — two Pharos roles</summary>

```json
{
  "eventId": "9cd02b67-53f5-47c5-ac38-426527f41c72",
  "eventType": "USER_PROVISIONED",
  "eventEffectiveAt": "2026-07-11T14:00:00Z",
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
      "name": "Maya R Patel"
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
        "isPrivileged": false
      },
      {
        "roleName": "APP_Pharos_Prod_GSI_Analyst",
        "roleDescription": "GSI Analyst",
        "assignedAt": "2026-07-11T14:00:00Z",
        "assignmentStatus": "PROVISIONED",
        "entitlementValue": "00g9p5s3u7V2xYaBc0h7",
        "entitlementType": "groups",
        "displayName": "GSI Analyst",
        "isPharosGroup": true,
        "isAvailableForRequest": true,
        "isPrivileged": false
      }
    ],
    "employeeClass": "Employee",
    "account": {
      "application": "Okta",
      "oktaAccountId": "00u8k3m5n7P2qRsTu0h7"
    }
  }
}
```

</details>

<details>
<summary>Minimal termination payload</summary>

```json
{
  "eventId": "05e09592-7f11-4251-b523-91ac52a98364",
  "eventType": "USER_TERMINATED",
  "eventEffectiveAt": "2026-08-31T17:00:00Z",
  "user": {
    "employeeId": "WU100482",
    "employmentStatus": "TERMINATED",
    "terminationDate": "2026-08-31"
  }
}
```

</details>

## Responses and delivery

HTTP 200 means the event was saved successfully or was already applied. An identical retry has no additional effect.

```json
{
  "eventId": "9cd02b67-53f5-47c5-ac38-426527f41c72",
  "status": "SUCCESS"
}
```

| Outcome | iPass action |
| --- | --- |
| 200 | Mark delivered. |
| 400 / 422 | Correct the invalid payload or business rule. Submit corrected content with a new event UUID. |
| 409 | Resolve the identity conflict, changed content under an existing UUID, or disallowed reactivation. Do not retry unchanged. |
| Timeout / connection failure / 500 / 503 | Retry the same event UUID and unchanged payload with backoff. If retries fail, retain the event for investigation and replay. |

Generate a new UUID for each new event. Reuse that UUID only for delivery of the identical event. Preserve event order per employee across all accounts, including retries. Pharos does not reject stale events automatically; do not replay an older snapshot after a newer one. Agree retry limits and urgent termination-failure escalation before go-live.

Error JSON contains `timestamp`, `status`, `code`, `message` and `correlationId`. Use the message to resolve the failure; provide eventId and correlationId to Pharos support.

## Verification APIs

| GET endpoint | Use |
| --- | --- |
| `/api/v1/employees/{employeeId}` | Current employee profile and account assignments. |
| `/api/v1/employees/{employeeId}?includeHistory=true` | Also include removed assignments. |
| `/api/v1/ipass/events/{eventId}` | Check the latest recorded processing outcome. APPLIED confirms processing; FAILED requires investigation. |

These endpoints return 404 when the record is not found. A missing event record does not prove a timed-out request was never received; retry the unchanged event safely.

## Interactive API reference

Swagger contains field definitions and examples for provisioning, updates, removals, contractors and restoration. “Try it out” sends real requests to the selected server.

[Open Swagger]({{PHAROS_BASE_URL}}/swagger-ui.html) · [OpenAPI JSON]({{PHAROS_BASE_URL}}/openapi/ipass.json)

The Confluence storage file includes an iFrame macro for Swagger and a direct-link fallback.
