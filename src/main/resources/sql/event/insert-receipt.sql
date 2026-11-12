INSERT INTO pharos_uam.ipass_event(event_id,event_type,employee_id,environment,application,okta_account_id,event_effective_at,payload)
VALUES (:id,:type,:employee,:environment,:application,:oktaAccount,:occurred,CAST(:payload AS jsonb)) ON CONFLICT(event_id) DO NOTHING
