UPDATE pharos_uam.okta_account_role_assignment ar SET removed_at=clock_timestamp(),removal_event_id=:event
WHERE ar.account_id=:account AND ar.removed_at IS NULL
AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(CAST(:roles AS jsonb)) item
 JOIN pharos_uam.ipass_role r ON r.entitlement_value=item->>'entitlementValue'
 WHERE r.role_id=ar.role_id AND r.application=ar.application AND r.environment=ar.environment)
