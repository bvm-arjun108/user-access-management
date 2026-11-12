# Postman

Import the collection and Local environment. No token is required.

Run the collection in order against http://localhost:8086. The first request creates fresh employee/event IDs. It covers provisioning, role changes, retries, termination, correction, and rehire.

CLI: `newman run postman/Pharos-UAM.postman_collection.json -e postman/Local.postman_environment.json`.

Collection-level checks verify X-Correlation-ID on every response and matching correlationId in error bodies.
