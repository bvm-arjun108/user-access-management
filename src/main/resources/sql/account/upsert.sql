INSERT INTO pharos_uam.account(employee_id,application,environment,external_account_id)
VALUES (:employee,:application,:environment,:external)
ON CONFLICT(application,environment,external_account_id) DO UPDATE SET updated_at=clock_timestamp()
WHERE account.employee_id=EXCLUDED.employee_id
RETURNING account_id
