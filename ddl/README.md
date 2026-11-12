# Manual schema setup

Use exactly one path:

- Empty database: run `01_create_schema.sql`.
- Existing four-table database: stop ingestion and run `02_upgrade_existing_schema.sql` once. Do not run the fresh-install script first.

Both scripts are transactional. They never drop data. Upgrade preserves old `role` and `employee_role` tables as `legacy_role` and `legacy_employee_role`, and keeps employees/events. New account IDs, entitlement values and owners cannot be inferred from the old data: obtain fresh iPass snapshots with new event UUIDs after upgrade. Review any old granular permission mappings before activating the new role catalog. The upgraded schema permits missing new profile fields on legacy rows; the API requires them on every new active snapshot.

Fresh setup from the repository root:

```bash
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U uam -d uam < ddl/01_create_schema.sql
```

The application does not create, update or migrate tables. No authentication is configured.
