SELECT grantee,
       table_schema,
       table_name,
       privilege_type
FROM information_schema.table_privileges
WHERE grantee like 'brbl%' AND grantee <> 'brbl_admin'
ORDER BY grantee, table_name, privilege_type;