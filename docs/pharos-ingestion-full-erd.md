# Pharos ingestion ER diagram

All columns and PostgreSQL types match the fresh-install DDL. PK = primary key; FK = foreign key. Profile columns are conditionally required for ACTIVE employees.

```mermaid
erDiagram
    EMPLOYEE ||--o{ ACCOUNT : has
    ACCOUNT ||--o{ ACCOUNT_ROLE : receives
    ROLE ||--o{ ACCOUNT_ROLE : assigned_as
    ROLE ||--o{ ROLE_OWNER : owned_by
    IPASS_EVENT ||--o{ ACCOUNT_ROLE : creates
    IPASS_EVENT o|--o{ ACCOUNT_ROLE : removes

    EMPLOYEE {
        text employee_id PK "Required"
        text source_system "Required when ACTIVE"
        text first_name "Required when ACTIVE"
        text middle_name "Nullable"
        text last_name "Required when ACTIVE"
        text email "Required when ACTIVE"
        text employment_status "Required"
        date hire_date "Required when ACTIVE"
        date termination_date "Nullable"
        text employee_class "Required when ACTIVE"
        text employment_type "Required when ACTIVE"
        text superior_organization "Required when ACTIVE"
        text organization_id "Required when ACTIVE"
        text country "Required when ACTIVE"
        text region "Required when ACTIVE"
        text site "Required when ACTIVE"
        text supervisory_organization "Required when ACTIVE"
        text manager_employee_id "Required when ACTIVE"
        text manager_first_name "Required when ACTIVE"
        text manager_middle_name "Nullable"
        text manager_last_name "Required when ACTIVE"
        text manager_display_name "Required when ACTIVE"
        text manager_email "Required when ACTIVE"
        timestamptz employee_last_synced_at "Required when ACTIVE"
        timestamptz created_at "Required"
        timestamptz updated_at "Required"
    }

    ACCOUNT {
        uuid account_id PK "Required"
        text employee_id FK "Required"
        text application "Required"
        text environment "Required"
        text okta_account_id "Required"
        timestamptz created_at "Required"
        timestamptz updated_at "Required"
    }

    ROLE {
        uuid role_id PK "Required"
        text application "Required"
        text environment "Required"
        text entitlement_value "Required"
        text entitlement_type "Required"
        text role_name "Required"
        text display_name "Required"
        text role_description "Required"
        boolean is_pharos_group "Required"
        boolean is_available_for_request "Required"
        boolean is_privileged "Required"
        timestamptz created_at "Required"
        timestamptz updated_at "Required"
    }

    ROLE_OWNER {
        uuid role_id PK,FK "Required"
        text owner_id PK "Required"
        text first_name "Required"
        text last_name "Required"
        text email "Required"
        timestamptz created_at "Required"
        timestamptz updated_at "Required"
    }

    IPASS_EVENT {
        uuid event_id PK "Required"
        text event_type "Required"
        text employee_id "Required"
        text environment "Required"
        text application "Nullable"
        text okta_account_id "Nullable"
        timestamptz event_effective_at "Required"
        timestamptz received_at "Required"
        timestamptz processed_at "Nullable"
        text processing_status "Required"
        jsonb payload "Required"
        text error_code "Nullable"
        text error_message "Nullable"
    }

    ACCOUNT_ROLE {
        uuid assignment_id PK "Required"
        uuid account_id FK "Required"
        uuid role_id FK "Required"
        text application FK "Required"
        text environment FK "Required"
        timestamptz assigned_at "Required"
        timestamptz removed_at "Nullable"
        uuid assigned_by_event_id FK "Required"
        uuid removed_by_event_id FK "Nullable"
        timestamptz created_at "Required"
    }
```

## Keys and scope

- Account source key: application + environment + okta_account_id.
- Role source key: application + environment + entitlement_value.
- Owner key: role_id + owner_id. Owners need not have employee accounts.
- Current assignment: removed_at IS NULL; one per account/role. Role and account must match application/environment through composite foreign keys.
- Internal account_id, role_id and assignment_id are database-generated UUIDs; they are not payload fields.
- Event UUID is supplied by iPass. Event employee/account identifiers are not foreign keys because receipt can precede entity creation.
- Termination affects the employee and every linked account in the deployment. Normal snapshots affect only their account.

## Example identifiers

All identifiers below are fictional. Internal keys are database-generated UUIDs; source identifiers are opaque strings from iPass.

| Table | Internal key | Source identifier | Scope |
| --- | --- | --- | --- |
| account | account_id = b6d27ea0-f56a-4a7e-8df2-26a849b6c430 | okta_account_id = 00u8k3m5n7P2qRsTu0h7 | Okta / Prod |
| role | role_id = 19c852d7-e483-49f8-93b6-63fe09fc1ea4 | entitlement_value = 00g8n4q2r6T9vWxYz0h7 | Okta / Prod |
| role | role_id = 832ebfac-b653-4719-a124-e6f1026c58f9 | entitlement_value = 00g9p5s3u7V2xYaBc0h7 | Okta / Prod |

The KYC role is named APP_Pharos_Prod_KYC_Analyst; the GSI role is APP_Pharos_Prod_GSI_Analyst. Assignment rows reference account_id b6d27ea0-f56a-4a7e-8df2-26a849b6c430 and role_id 19c852d7-e483-49f8-93b6-63fe09fc1ea4 or 832ebfac-b653-4719-a124-e6f1026c58f9. iPass sends the source identifiers, never these internal UUIDs. Source identifiers do not encode the employee, role name, or environment.
