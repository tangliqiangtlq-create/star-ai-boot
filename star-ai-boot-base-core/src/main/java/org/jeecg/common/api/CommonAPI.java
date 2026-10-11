package org.jeecg.common.api;

import org.jeecg.common.api.dto.DenyAuditDTO;
import org.jeecg.common.api.dto.LogDTO;
import org.jeecg.common.system.vo.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Common APIs
 * @author: jeecg-boot
 */
public interface CommonAPI {

    /**
     * 1QueryUserRoleInfo
     * @param username
     * @return
     */
    Set<String> queryUserRoles(String username);
    
    /**
     * 1QueryUserRoleInfo
     * @param userId
     * @return
     */
    Set<String> queryUserRolesById(String userId);


    /**
     * 2QueryUserPermissionInfo
     * @param userId
     * @return
     */
    Set<String> queryUserAuths(String userId);

    /**
     * 3 Query DynamicDataSourceModel stored in DB by id
     *
     * @param dbSourceId
     * @return
     */
    DynamicDataSourceModel getDynamicDbSourceById(String dbSourceId);

    /**
     * 4 Query DynamicDataSourceModel stored in DB by code
     *
     * @param dbSourceCode
     * @return
     */
    DynamicDataSourceModel getDynamicDbSourceByCode(String dbSourceCode);

    /**
     * 5 Query user info by username
     * @param username
     * @return
     */
    public LoginUser getUserByName(String username);

    /**
     * P3: verify a plaintext login password inside the system service.
     * Cross-service callers MUST use this instead of reading the password hash from
     * {@link #getUserByName(String)} — hashes must never cross service boundaries.
     * Fail-closed: returns false for any mismatch, missing account, or error.
     *
     * @param username    login username
     * @param rawPassword plaintext password supplied for re-verification
     * @return true only when the stored credential matches
     */
    boolean verifyPassword(String username, String rawPassword);

    /**
     * 5 Query user id by username
     * @param username
     * @return
     */
    public String getUserIdByName(String username);


    /**
     * 6 Translate dict table entry
     * @param table
     * @param text
     * @param code
     * @param key
     * @return
     */
    String translateDictFromTable(String table, String text, String code, String key);

    /**
     * 7 Translate common dict entry
     * @param code
     * @param key
     * @return
     */
    String translateDict(String code, String key);

    /**
     * 8 Query data permission
     * @param component component
     * @param username username
     * @param requestPath frontend request path
     * @return
     */
    List<SysPermissionDataRuleModel> queryPermissionDataRule(String component, String requestPath, String username);


    /**
     * 9QueryUserInfo
     * @param username
     * @return
     */
    SysUserCacheInfo getCacheUser(String username);

    /**
     * 10GetDataDictionary
     * @param code
     * @return
     */
    public List<DictModel> queryDictItemsByCode(String code);

    /**
     * Get valid data dictionary items
     * @param code
     * @return
     */
    public List<DictModel> queryEnableDictItemsByCode(String code);

    /**
     * 13GetTableDataDictionary
     * @param tableFilterSql
     * @param text
     * @param code
     * @return
     */
    List<DictModel> queryTableDictItemsByCode(String tableFilterSql, String text, String code);

    /**
     * 14 Translate common dict by multiple dictCodes and keys, comma-separated
     * @param dictCodes e.g. user_status,sex
     * @param keys e.g. 1,2,0
     * @return
     */
    Map<String, List<DictModel>> translateManyDict(String dictCodes, String keys);

    //update-begin---author:chenrui ---date:20231221  for: [issues/#5643] fix table-dict cross-DB query under distributed mode ------------
    /**
     * 15 Translate dict table entries; supports batch
     * @param table
     * @param text
     * @param code
     * @param keys comma-separated
     * @param dataSource data source
     * @return
     */
    List<DictModel> translateDictFromTableByKeys(String table, String text, String code, String keys, String dataSource);
    //update-end---author:chenrui ---date:20231221  for: [issues/#5643] fix table-dict cross-DB query under distributed mode ------------

    /**
     * 16 Persist an operation log record (cross-service).
     * <p>Under the per-service sharding scheme, dcs/processengine must NOT write {@code sys_log}
     * to their local schema copies; they should call this method so the record is written by the
     * system service into {@code mpay_system.sys_log}. In the system service itself this method
     * resolves to a local mapper write; in other services it goes out through Feign.
     * @param logDTO
     */
    void addLog(LogDTO logDTO);

    /**
     * 17 Persist a denied-invocation audit record (cross-service).
     *
     * <p>{@code sys_deny_audit} is owned by the system shard ({@code mpay_system}); dcs and
     * processengine no longer keep a local mirror (see {@code SHARD-BAN T5-06/07}, gate 1.14).
     * Every deny-audit writer (H5 level guard, H8 call audit, F-class ledger audit) MUST go
     * through this method instead of writing SQL against the local schema:
     * <ul>
     *   <li>system service → in-process write into {@code mpay_system.sys_deny_audit}
     *       (batch-100 async pipeline of {@code SysDenyAuditServiceImpl});
     *   <li>dcs / processengine → Feign S2S call, owner performs the write.
     * </ul>
     *
     * <p>Implementations must be fail-soft: an audit record is never allowed to break the caller's
     * business flow, so persistence/transport errors are logged and swallowed.
     *
     * @param denyAuditDTO record to persist (null is ignored)
     */
    void recordDenyAudit(DenyAuditDTO denyAuditDTO);

    /**
     * 18 Whether this service can persist deny-audit records right now.
     *
     * <p>Used by the H6 startup gate: the owner answers "is {@code sys_deny_audit} present in my
     * schema", a non-owner shard answers by probing the owner over S2S. This lets dcs/processengine
     * verify the deny-audit write path without keeping a foreign table locally.
     *
     * @return true when the deny-audit table is reachable through this service's write path
     */
    boolean isDenyAuditOwnerAvailable();

}
