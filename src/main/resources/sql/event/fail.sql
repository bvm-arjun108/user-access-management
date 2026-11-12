UPDATE pharos_uam.ipass_event SET processing_status='FAILED',processed_at=clock_timestamp(),
error_code=:code,error_message=:message WHERE event_id=:id AND processing_status <> 'APPLIED'
