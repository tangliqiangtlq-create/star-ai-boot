REVOKE FILE ON *.* FROM 'mp_dba'@'%';
REVOKE PROCESS ON *.* FROM 'mp_dba'@'%';
FLUSH PRIVILEGES;
INSERT INTO sys_audit_log(id,create_by,create_time,content) VALUES (UUID_SHORT(),'D12_SCRIPT',NOW(),'D12 REVOKE FILE,PROCESS FROM mp_dba@%');
