UPDATE pharos_uam.okta_account_role_assignment ar SET removed_at=clock_timestamp(),removal_event_id=:event
FROM pharos_uam.okta_account a WHERE a.okta_account_id=ar.okta_account_id
AND a.employee_id=:employee AND ar.removed_at IS NULL
