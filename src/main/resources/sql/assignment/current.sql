SELECT assigned_at FROM pharos_uam.okta_account_role_assignment WHERE account_id=:account AND role_id=:role AND removed_at IS NULL
