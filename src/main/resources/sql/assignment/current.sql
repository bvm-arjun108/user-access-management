SELECT assigned_at FROM pharos_uam.account_role WHERE account_id=:account AND role_id=:role AND removed_at IS NULL
