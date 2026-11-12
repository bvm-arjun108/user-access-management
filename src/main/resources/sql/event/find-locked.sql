SELECT *, payload = CAST(:payload AS jsonb) AS payload_matches
FROM pharos_uam.ipass_event WHERE event_id=:id FOR UPDATE
