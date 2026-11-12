DELETE FROM pharos_uam.ipass_role_owner ro WHERE ro.role_id=:role
AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(CAST(:owners AS jsonb)) item WHERE item->>'ownerId'=ro.owner_id)
