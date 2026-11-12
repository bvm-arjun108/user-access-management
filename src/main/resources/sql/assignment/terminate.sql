UPDATE pharos_uam.account_role ar SET removed_at=clock_timestamp(),removed_event_id=:event
FROM pharos_uam.account a WHERE a.account_id=ar.account_id
AND a.employee_id=:employee AND a.environment=:environment AND ar.removed_at IS NULL
