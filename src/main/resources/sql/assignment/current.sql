SELECT assigned_at FROM pharos_uam.employee_role_assignment WHERE employee_id=:employee AND role_id=:role AND removed_at IS NULL
