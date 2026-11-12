INSERT INTO pharos_uam.role_owner(role_id,owner_id,first_name,last_name,email)
VALUES (:role,:owner,:first,:last,:email)
ON CONFLICT(role_id,owner_id) DO UPDATE SET first_name=EXCLUDED.first_name,last_name=EXCLUDED.last_name,email=EXCLUDED.email
