UPDATE pharos_uam.ipass_event SET processing_status='APPLIED', processed_at=clock_timestamp(),
warnings=CAST(:warnings AS jsonb), error_code=NULL,error_message=NULL WHERE event_id=:id
