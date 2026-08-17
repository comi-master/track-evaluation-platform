INSERT INTO sys_user_role(user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.code = 'RESEARCHER'
WHERE u.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role existing_role WHERE existing_role.user_id = u.id
  );
