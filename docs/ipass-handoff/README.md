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
| Account scope | One employee and one oktaAccountId per provisioning/update call. Send separate calls for distinct Okta accounts. |
| Complete role list | `user.iPassRoles` contains all current Pharos roles for that account. Omitted roles are removed. Missing/null is rejected. Maximum 500 roles. |
| Identity | Employee: `employeeId` (source Owner ID). Account: `oktaAccountId`. Role: `entitlementValue`. Keep identifiers stable and preserve their case. |
| Environment / owners | No environment field or inference. Source Owner First/Last describe the employee; role owners are not supplied. |
| Employee fields | Use every field in the full example. `email` and `supplier` may be omitted or null; either clears its saved value in a full update. `middleName` may be null. ACTIVE requires `terminationDate: null`. Manager ID, first name and last name are required; manager middleName is required but may be null. |
| Role fields | Every shown role field is required. `assignmentStatus` is always PROVISIONED; `isPharosGroup` must be true. Send JSON booleans, not strings. Requestability/privilege flags are metadata. |
| Dates | Dates: YYYY-MM-DD. Timestamps: RFC 3339 with timezone. `eventEffectiveAt` is when the change took effect; `employeeLastSyncedAt` is when iPass refreshed employee details from Workday/VNDLY. |
| Assignment time | `assignedAt` is the original start of the current assignment. Preserve it while membership continues; it cannot follow `eventEffectiveAt`. |
| Validation | Send nonblank strings without surrounding whitespace. Do not send future-effective changes. Full field types and limits are in Swagger. |

The jobDetails object contains department, jobCode, jobTitle, jobFamilyGroup and jobFamily. Include all five keys; use null for unavailable values. A null value clears the previously stored value.

supplier is the contractor supplier name from iPass. It is optional and may be null; missing/null clears the stored supplier in a full profile update.

## Request examples

The Swagger example selector contains these six cases. Each is a complete request.

| Use case | Event type | Expected result |
| --- | --- | --- |
| First Pharos access | `USER_PROVISIONED` | Create the employee and account with the complete approved role list. |
| Remove one role | `USER_UPDATED` | Omit GSI; retain every other current role for this account. |
| Add a role | `USER_UPDATED` | Include GDD together with every existing current role for this account. |
| Remove all roles | `USER_UPDATED` | Send iPassRoles: []; employment remains ACTIVE and the account has no current roles. |
| Terminate employee | `USER_TERMINATED` | Close current assignments across all employee accounts immediately. |
| Restore access after termination correction | `USER_PROVISIONED` | Send the approved ACTIVE profile and current roles for the account being restored. |

Fictional data. USER_UPDATED uses the full provisioning structure with a new event ID, the effective time of the change and the complete current account snapshot. To remove every role, send that full structure with `iPassRoles: []`.

<details>
<summary>Full provisioning payload — KYC, GSI and BKYC roles</summary>

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
    "companyName": "Western Union",
    "country": "USA",
    "businessUnit": "North America",
    "city": "Denver",
    "employeeLastSyncedAt": "2026-07-11T14:00:00Z",
    "manager": {
      "employeeId": "WU100210",
      "firstName": "Maya",
      "middleName": "R",
      "lastName": "Patel"
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
      },
      {
        "roleName": "APP_Pharos_Prod_BKYC_Analyst",
        "roleDescription": "BKYC Analyst",
        "assignedAt": "2026-07-11T14:00:00Z",
        "assignmentStatus": "PROVISIONED",
        "entitlementValue": "00g7m3r5t8W4zAbCd0h7",
        "entitlementType": "groups",
        "displayName": "BKYC Analyst",
        "isPharosGroup": true,
        "isAvailableForRequest": true,
        "isPrivileged": false
      }
    ],
    "employeeClass": "Employee",
    "jobDetails": {
      "department": "Financial Crime Compliance",
      "jobCode": "2726T",
      "jobTitle": "Senior Analyst AML Compliance",
      "jobFamilyGroup": "Compliance",
      "jobFamily": "AML/CPCE Compliance Global Operations"
    },
    "supplier": null,
    "oktaAccountId": "00u8k3m5n7P2qRsTu0h7"
  }
}
```

</details>

<details>
<summary>Update payload — remove GSI and retain the other current roles</summary>

GSI is omitted from the complete account role list. Pharos removes that assignment and retains the listed roles.

```json
{
  "eventId": "c3809b3c-45a2-4df9-adaf-b00a507f08bd",
  "eventType": "USER_UPDATED",
  "eventEffectiveAt": "2026-07-17T10:00:00Z",
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
    "companyName": "Western Union",
    "country": "USA",
    "businessUnit": "North America",
    "city": "Denver",
    "employeeLastSyncedAt": "2026-07-17T10:00:00Z",
    "manager": {
      "employeeId": "WU100210",
      "firstName": "Maya",
      "middleName": "R",
      "lastName": "Patel"
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
        "roleName": "APP_Pharos_Prod_BKYC_Analyst",
        "roleDescription": "BKYC Analyst",
        "assignedAt": "2026-07-11T14:00:00Z",
        "assignmentStatus": "PROVISIONED",
        "entitlementValue": "00g7m3r5t8W4zAbCd0h7",
        "entitlementType": "groups",
        "displayName": "BKYC Analyst",
        "isPharosGroup": true,
        "isAvailableForRequest": true,
        "isPrivileged": false
      }
    ],
    "employeeClass": "Employee",
    "jobDetails": {
      "department": "Financial Crime Compliance",
      "jobCode": "2726T",
      "jobTitle": "Senior Analyst AML Compliance",
      "jobFamilyGroup": "Compliance",
      "jobFamily": "AML/CPCE Compliance Global Operations"
    },
    "supplier": null,
    "oktaAccountId": "00u8k3m5n7P2qRsTu0h7"
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
| 400 | Resolve the invalid payload, identity conflict or business rule. Submit corrected content with a new event UUID. |
| Timeout / connection failure / 500 | Retry the same event UUID and unchanged payload with backoff. If retries fail, retain the event for investigation and replay. |

Generate a new UUID for each new event. Reuse that UUID only for delivery of the identical event. Preserve event order per employee across all accounts, including retries. Pharos does not reject stale events automatically; do not replay an older snapshot after a newer one. Agree retry limits and urgent termination-failure escalation before go-live.

Error JSON contains `timestamp`, `status`, `code`, `message` and `correlationId`. Use the message to resolve the failure; provide eventId and correlationId to Pharos support.

## Interactive API reference

Browse the POST endpoint, schemas and payload examples in the OpenAPI macro on this Confluence page. The macro reads the attached `pharos-ipass.openapi.json`; readers do not need a running Pharos application.

API execution is disabled for contract review. “Try it out” requires a reachable API endpoint and agreed authentication.
