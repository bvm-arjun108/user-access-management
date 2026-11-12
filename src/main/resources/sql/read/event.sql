SELECT jsonb_build_object('eventId',event_id,'eventType',event_type,'employeeId',employee_id,
'environment',environment,'application',application,'oktaAccountId',okta_account_id,'eventEffectiveAt',event_effective_at,'receivedAt',received_at,'processedAt',processed_at,
'processingStatus',processing_status,'errorCode',error_code,'errorMessage',error_message)::text AS data
FROM pharos_uam.ipass_event WHERE event_id=:id AND environment=:environment
