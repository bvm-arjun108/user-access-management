UPDATE pharos_uam.account_role_assignment ar SET removed_at=clock_timestamp(),removal_event_id=:event
FROM pharos_uam.okta_account a WHERE a.account_id=ar.account_id
AND a.employee_id=:employee AND a.environment=:environment AND ar.removed_at IS NULL
