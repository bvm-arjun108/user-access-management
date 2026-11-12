BEGIN;
-- Fresh development database. Edit this file and recreate the Docker volume for schema changes.
CREATE SCHEMA pharos_uam;

CREATE TABLE pharos_uam.employee (

    employee_id                 TEXT PRIMARY KEY CHECK (btrim(employee_id) <> ''),
    source_system               TEXT CHECK (source_system IN ('WORKDAY', 'VNDLY')),
    first_name                  TEXT CHECK (btrim(first_name) <> ''),
    middle_name                 TEXT,
    last_name                   TEXT CHECK (btrim(last_name) <> ''),
    email                       TEXT CHECK (btrim(email) <> ''),
    employment_status           TEXT NOT NULL CHECK (employment_status IN ('ACTIVE', 'TERMINATED')),
    hire_date                   DATE,
    termination_date            DATE,
    employee_class              TEXT CHECK (btrim(employee_class) <> ''),
    employment_type             TEXT CHECK (btrim(employment_type) <> ''),
    superior_organization       TEXT CHECK (btrim(superior_organization) <> ''),
    organization_id             TEXT CHECK (btrim(organization_id) <> ''),
    country                     TEXT CHECK (btrim(country) <> ''),
    region                      TEXT CHECK (btrim(region) <> ''),
    site                        TEXT CHECK (btrim(site) <> ''),
    supervisory_organization    TEXT CHECK (btrim(supervisory_organization) <> ''),
    manager_employee_id         TEXT CHECK (btrim(manager_employee_id) <> ''),
    manager_first_name          TEXT CHECK (btrim(manager_first_name) <> ''),
    manager_middle_name         TEXT,
    manager_last_name           TEXT CHECK (btrim(manager_last_name) <> ''),
    manager_display_name        TEXT CHECK (btrim(manager_display_name) <> ''),
    manager_email               TEXT CHECK (btrim(manager_email) <> ''),
    employee_last_synced_at       TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_employee_dates CHECK (
        termination_date IS NULL OR hire_date IS NULL OR termination_date >= hire_date
    ),
    CONSTRAINT ck_active_employee CHECK (
        employment_status <> 'ACTIVE' OR (
            termination_date IS NULL AND source_system IS NOT NULL
            AND first_name IS NOT NULL AND last_name IS NOT NULL AND email IS NOT NULL
            AND hire_date IS NOT NULL AND employment_type IS NOT NULL AND employee_class IS NOT NULL
            AND superior_organization IS NOT NULL AND organization_id IS NOT NULL
            AND country IS NOT NULL AND region IS NOT NULL AND site IS NOT NULL
            AND supervisory_organization IS NOT NULL AND employee_last_synced_at IS NOT NULL
            AND manager_employee_id IS NOT NULL AND manager_first_name IS NOT NULL
            AND manager_last_name IS NOT NULL AND manager_email IS NOT NULL AND manager_display_name IS NOT NULL
        )
    )
);

CREATE TABLE pharos_uam.okta_account (
account_id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    employee_id         TEXT NOT NULL REFERENCES pharos_uam.employee(employee_id) ON DELETE RESTRICT,
    application         TEXT NOT NULL CHECK (btrim(application) <> ''),
    environment         TEXT NOT NULL CHECK (environment IN ('Prod','UAT','Dev')),
    okta_account_id TEXT NOT NULL CHECK (btrim(okta_account_id) <> ''),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_account_okta UNIQUE(application,environment,okta_account_id),
    CONSTRAINT uq_account_scope UNIQUE(account_id,application,environment)
);

CREATE TABLE pharos_uam.ipass_role (
role_id             UUID DEFAULT gen_random_uuid() CONSTRAINT pk_role PRIMARY KEY,
    application         TEXT NOT NULL CHECK (btrim(application) <> ''),
    environment         TEXT NOT NULL CHECK (environment IN ('Prod','UAT','Dev')),
    entitlement_value   TEXT NOT NULL CHECK (btrim(entitlement_value) <> ''),
    entitlement_type    TEXT NOT NULL CHECK (btrim(entitlement_type) <> ''),
    role_name           TEXT NOT NULL CHECK (btrim(role_name) <> ''),
    display_name        TEXT NOT NULL CHECK (btrim(display_name) <> ''),
    role_description    TEXT NOT NULL CHECK (btrim(role_description) <> ''),
    is_pharos_group     BOOLEAN NOT NULL CHECK (is_pharos_group),
    is_available_for_request BOOLEAN NOT NULL,
    is_privileged       BOOLEAN NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_role_entitlement UNIQUE(application,environment,entitlement_value),
    CONSTRAINT uq_role_scope UNIQUE(role_id,application,environment),
    CONSTRAINT ck_role_name_scope CHECK (starts_with(role_name,'APP_Pharos_' || environment || '_'))
);

CREATE TABLE pharos_uam.ipass_role_owner (
role_id             UUID NOT NULL REFERENCES pharos_uam.ipass_role(role_id) ON DELETE RESTRICT,
    owner_id            TEXT NOT NULL CHECK (btrim(owner_id) <> ''),
    first_name          TEXT NOT NULL CHECK (btrim(first_name) <> ''),
    last_name           TEXT NOT NULL CHECK (btrim(last_name) <> ''),
    email               TEXT NOT NULL CHECK (btrim(email) <> ''),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(role_id,owner_id)
);

CREATE TABLE pharos_uam.ipass_event (

    event_id            UUID PRIMARY KEY, -- Generated by iPass; deliberately no database default.
    event_type          TEXT NOT NULL CHECK (event_type IN (
                            'USER_PROVISIONED', 'USER_UPDATED', 'USER_TERMINATED')),
    employee_id         TEXT NOT NULL CHECK (btrim(employee_id) <> ''),
    environment         TEXT NOT NULL CHECK (environment IN ('Prod', 'UAT', 'Dev')),
    application         TEXT,
    okta_account_id TEXT,
    event_effective_at         TIMESTAMPTZ NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    processed_at        TIMESTAMPTZ,
    processing_status   TEXT NOT NULL DEFAULT 'RECEIVED'
                            CHECK (processing_status IN ('RECEIVED', 'APPLIED', 'FAILED')),
    payload             JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    error_code          TEXT,
    error_message       TEXT,
    CONSTRAINT ck_event_processing CHECK (
        (processing_status = 'RECEIVED' AND processed_at IS NULL
            AND error_code IS NULL AND error_message IS NULL)
        OR (processing_status = 'APPLIED' AND processed_at IS NOT NULL
            AND error_code IS NULL AND error_message IS NULL)
        OR (processing_status = 'FAILED' AND processed_at IS NOT NULL
            AND error_code IS NOT NULL AND error_message IS NOT NULL)
    )
);

CREATE TABLE pharos_uam.okta_account_role_assignment (
assignment_id       UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    account_id          UUID NOT NULL,
    role_id             UUID NOT NULL,
    application         TEXT NOT NULL,
    environment         TEXT NOT NULL,
    assigned_at         TIMESTAMPTZ NOT NULL,
    removed_at          TIMESTAMPTZ,
    assignment_event_id    UUID NOT NULL REFERENCES pharos_uam.ipass_event(event_id) ON DELETE RESTRICT,
    removal_event_id    UUID REFERENCES pharos_uam.ipass_event(event_id) ON DELETE RESTRICT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_account_role_account FOREIGN KEY(account_id,application,environment)
        REFERENCES pharos_uam.okta_account(account_id,application,environment) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_role FOREIGN KEY(role_id,application,environment)
        REFERENCES pharos_uam.ipass_role(role_id,application,environment) ON DELETE RESTRICT,
    CONSTRAINT ck_account_role_removal CHECK (
        (removed_at IS NULL AND removal_event_id IS NULL) OR
        (removed_at IS NOT NULL AND removal_event_id IS NOT NULL AND removed_at >= assigned_at)
    )
);

CREATE UNIQUE INDEX uq_account_role_current ON pharos_uam.okta_account_role_assignment(account_id,role_id) WHERE removed_at IS NULL;

COMMIT;
