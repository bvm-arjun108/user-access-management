INSERT INTO pharos_uam.role(application,environment,entitlement_value,entitlement_type,role_name,display_name,
role_description,is_pharos_group,is_available_for_request,is_privileged)
VALUES (:application,:environment,:entitlement,:entitlementType,:name,:displayName,:description,:pharos,:requestable,:privileged)
ON CONFLICT(application,environment,entitlement_value) DO UPDATE SET
entitlement_type=EXCLUDED.entitlement_type,display_name=EXCLUDED.display_name,
role_description=EXCLUDED.role_description,is_pharos_group=EXCLUDED.is_pharos_group,
is_available_for_request=EXCLUDED.is_available_for_request,is_privileged=EXCLUDED.is_privileged
WHERE role.role_name=EXCLUDED.role_name
RETURNING role_id,permission_mapping_status
