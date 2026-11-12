INSERT INTO pharos_uam.ipass_event(event_id,event_type,employee_id,environment,application,external_account_id,occurred_at,payload)
VALUES (:id,:type,:employee,:environment,:application,:externalAccount,:occurred,CAST(:payload AS jsonb)) ON CONFLICT(event_id) DO NOTHING
