SELECT jsonb_build_object('employee',to_jsonb(e),'accounts',COALESCE((
 SELECT jsonb_agg(jsonb_build_object('accountId',a.external_account_id,'application',a.application,'environment',a.environment,
 'assignments',COALESCE((
 SELECT jsonb_agg(jsonb_build_object('assignmentId',ar.assignment_id,'entitlementValue',r.entitlement_value,
 'entitlementType',r.entitlement_type,'roleName',r.role_name,'displayName',r.display_name,'roleDescription',r.role_description,
 'isPharosGroup',r.is_pharos_group,'isAvailableForRequest',r.is_available_for_request,'isPrivileged',r.is_privileged,
 'owners',COALESCE((SELECT jsonb_agg(jsonb_build_object('ownerId',o.owner_id,'firstName',o.first_name,'lastName',o.last_name,'email',o.email)
 ORDER BY o.owner_id) FROM pharos_uam.role_owner o WHERE o.role_id=r.role_id),'[]'::jsonb),
 'assignedAt',ar.assigned_at,'removedAt',ar.removed_at,
 'assignmentStatus',CASE WHEN ar.removed_at IS NULL THEN 'PROVISIONED' ELSE 'REMOVED' END,
 'permissionMappingStatus',r.permission_mapping_status,
 'authorizationEligible',e.employment_status='ACTIVE' AND ar.removed_at IS NULL AND r.permission_mapping_status='ACTIVE') ORDER BY ar.assignment_id)
 FROM pharos_uam.account_role ar JOIN pharos_uam.role r ON r.role_id=ar.role_id WHERE ar.account_id=a.account_id),'[]'::jsonb))
 ORDER BY a.account_id) FROM pharos_uam.account a WHERE a.employee_id=e.employee_id AND a.environment=:environment),'[]'::jsonb))::text AS data
FROM pharos_uam.employee e WHERE e.employee_id=:employee
