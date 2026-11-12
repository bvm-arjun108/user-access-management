# Database schema

`01_create_schema.sql` creates all six tables and their integrity constraints. There are no triggers or stored functions. Only primary-key/unique-constraint indexes and the unique current-assignment index are retained. The application SQL maintains updated_at.

Compose mounts this folder into `/docker-entrypoint-initdb.d`. PostgreSQL runs the SQL files in filename order when initializing an empty database.

For schema changes, edit the create script and recreate the development database:

```bash
docker compose down -v
docker compose up -d
```

The reset deletes local database data. No ALTER, upgrade, or migration scripts are needed at this stage. The API itself does not execute DDL.
