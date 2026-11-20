INSERT INTO pharos_uam.okta_account(employee_id,application,okta_account_id)
VALUES (:employee,:application,:external)
ON CONFLICT(application,okta_account_id) DO UPDATE SET updated_at=clock_timestamp()
WHERE okta_account.employee_id=EXCLUDED.employee_id
RETURNING account_id
