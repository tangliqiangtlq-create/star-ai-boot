-- Note: The frontend directory for this page is views/${entityPackage}
-- To use a different directory, update the component field in this SQL

<#assign id = '${.now?string["yyyyMMddhhmmSSsss"]}0'>

INSERT INTO sys_permission(id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, hide_tab, description, status, del_flag, rule_flag, create_by, create_time, update_by, update_time, internal_or_external) 
VALUES ('${id}', NULL, '${tableVo.ftlDescription}', '/${entityPackage}/${entityName?uncap_first}List', '${entityPackage}/${entityName}List', NULL, NULL, 0, NULL, '1', 0.00, 0, NULL, 1, 0, 0, 0, 0, NULL, '1', 0, 0, 'admin', '${.now?string["yyyy-MM-dd HH:mm:ss"]}', NULL, NULL, 0);

-- Permission control SQL
-- Add
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('${.now?string["yyyyMMddhhmmSSsss"]}1', '${id}', 'Add ${tableVo.ftlDescription}', NULL, NULL, 0, NULL, NULL, 2, '${entityPackage}:${tableName}:add', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '${.now?string["yyyy-MM-dd HH:mm:ss"]}', NULL, NULL, 0, 0, '1', 0);
-- Edit
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('${.now?string["yyyyMMddhhmmSSsss"]}2', '${id}', 'Edit ${tableVo.ftlDescription}', NULL, NULL, 0, NULL, NULL, 2, '${entityPackage}:${tableName}:edit', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '${.now?string["yyyy-MM-dd HH:mm:ss"]}', NULL, NULL, 0, 0, '1', 0);
-- Delete
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('${.now?string["yyyyMMddhhmmSSsss"]}3', '${id}', 'Delete ${tableVo.ftlDescription}', NULL, NULL, 0, NULL, NULL, 2, '${entityPackage}:${tableName}:delete', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '${.now?string["yyyy-MM-dd HH:mm:ss"]}', NULL, NULL, 0, 0, '1', 0);
-- Batch delete
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('${.now?string["yyyyMMddhhmmSSsss"]}4', '${id}', 'Batch delete ${tableVo.ftlDescription}', NULL, NULL, 0, NULL, NULL, 2, '${entityPackage}:${tableName}:deleteBatch', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '${.now?string["yyyy-MM-dd HH:mm:ss"]}', NULL, NULL, 0, 0, '1', 0);
-- Export Excel
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('${.now?string["yyyyMMddhhmmSSsss"]}5', '${id}', 'Export Excel ${tableVo.ftlDescription}', NULL, NULL, 0, NULL, NULL, 2, '${entityPackage}:${tableName}:exportXls', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '${.now?string["yyyy-MM-dd HH:mm:ss"]}', NULL, NULL, 0, 0, '1', 0);
-- Import Excel
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('${.now?string["yyyyMMddhhmmSSsss"]}6', '${id}', 'Import Excel ${tableVo.ftlDescription}', NULL, NULL, 0, NULL, NULL, 2, '${entityPackage}:${tableName}:importExcel', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '${.now?string["yyyy-MM-dd HH:mm:ss"]}', NULL, NULL, 0, 0, '1', 0);
