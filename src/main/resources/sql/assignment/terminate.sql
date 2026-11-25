UPDATE pharos_uam.employee_role_assignment SET removed_at=clock_timestamp(),removal_event_id=:event
WHERE employee_id=:employee AND removed_at IS NULL
