BEGIN;
-- Upgrade the previous four-table schema. Run once, with ingestion stopped.
-- Existing identities, event history and legacy assignments are preserved.
ALTER TABLE pharos_uam.employee ADD COLUMN employee_class TEXT CHECK (btrim(employee_class) <> '');
ALTER TABLE pharos_uam.employee ADD COLUMN manager_display_name TEXT CHECK (btrim(manager_display_name) <> '');
-- These source fields are unknown for old profiles. New API snapshots require both.
-- Keep old active rows valid; do not invent employee class or split/join source names.
ALTER TABLE pharos_uam.ipass_event ADD COLUMN application TEXT;
ALTER TABLE pharos_uam.ipass_event ADD COLUMN external_account_id TEXT;
ALTER TABLE pharos_uam.employee_role RENAME TO legacy_employee_role;
ALTER TABLE pharos_uam.role RENAME TO legacy_role;

CREATE TABLE pharos_uam.account (
account_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id         TEXT NOT NULL REFERENCES pharos_uam.employee(employee_id) ON DELETE RESTRICT,
    application         TEXT NOT NULL CHECK (btrim(application) <> ''),
    environment         TEXT NOT NULL CHECK (environment IN ('Prod','UAT','Dev')),
    external_account_id TEXT NOT NULL CHECK (btrim(external_account_id) <> ''),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_account_external UNIQUE(application,environment,external_account_id),
    CONSTRAINT uq_account_scope UNIQUE(account_id,application,environment)
);

CREATE TABLE pharos_uam.role (
role_id             BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_role PRIMARY KEY,
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
    permission_mapping_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (permission_mapping_status IN ('PENDING','ACTIVE')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_role_entitlement UNIQUE(application,environment,entitlement_value),
    CONSTRAINT uq_role_scope UNIQUE(role_id,application,environment),
    CONSTRAINT ck_role_name_scope CHECK (starts_with(role_name,'APP_Pharos_' || environment || '_'))
);

CREATE TABLE pharos_uam.role_owner (
role_id             BIGINT NOT NULL REFERENCES pharos_uam.role(role_id) ON DELETE RESTRICT,
    owner_id            TEXT NOT NULL CHECK (btrim(owner_id) <> ''),
    first_name          TEXT NOT NULL CHECK (btrim(first_name) <> ''),
    last_name           TEXT NOT NULL CHECK (btrim(last_name) <> ''),
    email               TEXT NOT NULL CHECK (btrim(email) <> ''),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(role_id,owner_id)
);

CREATE TABLE pharos_uam.account_role (
assignment_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id          BIGINT NOT NULL,
    role_id             BIGINT NOT NULL,
    application         TEXT NOT NULL,
    environment         TEXT NOT NULL,
    assigned_at         TIMESTAMPTZ NOT NULL,
    removed_at          TIMESTAMPTZ,
    created_event_id    UUID NOT NULL REFERENCES pharos_uam.ipass_event(event_id) ON DELETE RESTRICT,
    removed_event_id    UUID REFERENCES pharos_uam.ipass_event(event_id) ON DELETE RESTRICT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_account_role_account FOREIGN KEY(account_id,application,environment)
        REFERENCES pharos_uam.account(account_id,application,environment) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_role FOREIGN KEY(role_id,application,environment)
        REFERENCES pharos_uam.role(role_id,application,environment) ON DELETE RESTRICT,
    CONSTRAINT ck_account_role_removal CHECK (
        (removed_at IS NULL AND removed_event_id IS NULL) OR
        (removed_at IS NOT NULL AND removed_event_id IS NOT NULL AND removed_at >= assigned_at)
    )
);


CREATE INDEX ix_account_employee ON pharos_uam.account(employee_id,environment);
CREATE UNIQUE INDEX uq_account_role_current ON pharos_uam.account_role(account_id,role_id) WHERE removed_at IS NULL;
CREATE INDEX ix_account_role_history ON pharos_uam.account_role(account_id,assigned_at DESC);
CREATE INDEX ix_account_role_role ON pharos_uam.account_role(role_id);
CREATE INDEX ix_account_role_creation_event ON pharos_uam.account_role(created_event_id);
CREATE INDEX ix_account_role_removal_event ON pharos_uam.account_role(removed_event_id) WHERE removed_event_id IS NOT NULL;
CREATE TRIGGER trg_account_updated_at BEFORE UPDATE ON pharos_uam.account FOR EACH ROW EXECUTE FUNCTION pharos_uam.set_updated_at();
CREATE TRIGGER trg_role_updated_at BEFORE UPDATE ON pharos_uam.role FOR EACH ROW EXECUTE FUNCTION pharos_uam.set_updated_at();
CREATE TRIGGER trg_role_owner_updated_at BEFORE UPDATE ON pharos_uam.role_owner FOR EACH ROW EXECUTE FUNCTION pharos_uam.set_updated_at();

-- Replay authoritative snapshots with new event UUIDs to populate accounts,
-- entitlement IDs and owners. Old role IDs are not assumed to be Okta group IDs.
-- Do not delete legacy tables until history and any permission mappings are reviewed.
COMMIT;
