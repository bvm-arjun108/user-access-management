# Pharos UAM data model

iPass sends employee details and a complete approved role snapshot for one account.

## Tables and uniqueness

| Table | Stores | Primary key | Duplicate prevention |
| --- | --- | --- | --- |
| employee | Latest employee profile, manager and employment status | employee_id | One row per employee identity |
| okta_account | Employee's source account | account_id, generated UUID | application + okta_account_id |
| ipass_role | Shared source role definition | role_id, generated UUID | application + entitlement_value |
| okta_account_role_assignment | Current and historical assignment periods | assignment_id, generated UUID | One open assignment per account_id + role_id |
| ipass_event | Original payload and latest processing result | event_id, UUID from iPass | One event UUID; identical retries make no repeated changes |

## Relationships

~~~mermaid
erDiagram
    EMPLOYEE ||--o{ OKTA_ACCOUNT : has
    OKTA_ACCOUNT ||--o{ OKTA_ACCOUNT_ROLE_ASSIGNMENT : receives
    IPASS_ROLE ||--o{ OKTA_ACCOUNT_ROLE_ASSIGNMENT : assigned_as
    IPASS_EVENT ||--o{ OKTA_ACCOUNT_ROLE_ASSIGNMENT : creates
    IPASS_EVENT o|--o{ OKTA_ACCOUNT_ROLE_ASSIGNMENT : closes
~~~

Assignments reference an account, role and creation event. The removal event is optional until closed. Composite account/role foreign keys also check application. All five foreign keys use ON DELETE RESTRICT.

## Source mapping

| iPass export | API | Database |
| --- | --- | --- |
| Owner ID | user.employeeId | employee.employee_id |
| Owner First / Owner Last | user.firstName / user.lastName | Employee name columns |
| Account | user.account.oktaAccountId | okta_account.okta_account_id |
| Manager ID / Manager Name | user.manager.employeeId / user.manager.name | employee.manager_employee_id / manager_name |
| Entitlement Value | user.iPassRoles[].entitlementValue | ipass_role.entitlement_value |
| Display Name | user.iPassRoles[].displayName | ipass_role.display_name |

“Owner” in this export means the employee holding the account. Role ownership is not supplied and is not modeled.

Employee email is optional and nullable. Supply the agreed work/contact email when available; do not automatically substitute home email. Missing email in a full snapshot is stored as null. Manager ID and full name are required; Pharos does not split the name or require a manager email.

## Employee and account identity

Internal account_id, role_id and assignment_id values are PostgreSQL-generated UUIDs. Source employee and Okta account IDs are strings. Updating an existing account or role retains its internal UUID.

| Employee ID | Okta account ID | Assigned entitlement |
| --- | --- | --- |
| 1234 | 00u8k3m5n7P2qRsTu0h7 | 00g8n4q2r6T9vWxYz0h7 |
| 1234 | 00u9j4n6p8Q3rStUv0h7 | 00g9p5s3u7V2xYaBc0h7 |

This is one employee, two distinct accounts and two account-specific assignments. Removing roles from either account does not affect the other.

Environment is not supplied or inferred. Role names and IDs cannot establish QA or Prod authorization. An existing source account cannot move to another employee. Account uniqueness is application + okta_account_id; role uniqueness is application + entitlement_value.

## Changes and history

| Change | Result |
| --- | --- |
| Add role | Create an assignment; reuse shared role definition |
| Remove role | Close assignment; retain role and history |
| Remove all roles | Send iPassRoles: []; close assignments for that account only |
| Reassign role | Create a new assignment period |
| Terminate employee | Mark TERMINATED; close assignments for all accounts in this database |
| Approved restoration | Require USER_PROVISIONED; restore only supplied account roles |
| Retry identical event | Return SUCCESS without repeating applied changes |
| Change payload under same UUID | Reject with HTTP 409 |

The GET employee API returns current assignments by default. Add ?includeHistory=true for current and removed periods. The account remains with assignments: [] when it has no current roles.

## Timestamps and processing

| Column | Meaning |
| --- | --- |
| employee_last_synced_at | When iPass last refreshed employee data from Workday/VNDLY |
| event_effective_at | When the reported change became effective |
| received_at / processed_at | Pharos first receipt / latest processing completion |
| assigned_at / removed_at | Source assignment start / when Pharos closed assignment |
| assignment_event_id / removal_event_id | Event that created / closed assignment |
| created_at / updated_at | Pharos record timestamps |

removed_at does not confirm when iPass removed membership. Termination must reach the receiving Pharos database immediately, independently of iPass's cleanup period.

Event status is RECEIVED, APPLIED or FAILED. Pharos records failure code/message where possible. Business changes and APPLIED status commit together; business failures roll changes back.

## Source agreements

iPass must supply stable employee identity, ordered employee delivery including retries, and current shared role metadata. No automatic stale-event rejection is implemented. Additional HR columns require confirmed mappings before inclusion.

[Full ER diagram](pharos-ingestion-full-erd.md) · [DDL](../ddl/01_create_schema.sql) · [Scenarios](scenarios.md)
