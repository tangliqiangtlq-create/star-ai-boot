package org.jeecg.common.system.api;

import org.springframework.web.bind.annotation.PostMapping;

import cn.hutool.json.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.api.dto.DataLogDTO;
import org.jeecg.common.api.dto.DenyAuditDTO;
import org.jeecg.common.api.dto.LogDTO;
import org.jeecg.common.api.dto.OnlineAuthDTO;
import org.jeecg.common.api.dto.message.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.ServiceNameConstants;
import org.jeecg.common.constant.enums.EmailTemplateEnum;
import org.jeecg.common.desensitization.annotation.SensitiveDecode;
import org.jeecg.common.system.api.factory.SysBaseAPIFallbackFactory;
import org.jeecg.common.system.vo.*;
import org.jeecg.config.CustomFeignConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 1. Cloud interface count 43; local: 35; common: 9; one special queryAllRole counts as two - compared with local version -
 * removed methods: addLog, getDatabaseType, queryAllDepart, queryAllUser(Wrapper wrapper), queryAllUser(String[]
 * 2. @ConditionalOnMissingClass("org.jeecg.modules.system.service.impl.SysBaseApiImpl")=>
 * when an implementation class exists, do not instantiate the Feign interface
 *
 * @author: jeecg-boot
 */
@Component
@FeignClient(
    contextId = "sysBaseRemoteApi",
    value = ServiceNameConstants.SERVICE_SYSTEM,
    fallbackFactory = SysBaseAPIFallbackFactory.class,
    configuration = CustomFeignConfig.class)
@ConditionalOnMissingClass("org.jeecg.modules.system.service.impl.SysBaseApiImpl")
public interface ISysBaseAPI extends CommonAPI {

  /**
   * 1 Send system message
   *
   * @param message set params via constructor; defaults category (message type) to 2 for system message if unset
   */

  /**
   * 2 Send message with business params
   *
   * @param message set params via constructor
   */
  @PostMapping("/sys/api/sendBusAnnouncement")
  void sendBusAnnouncement(@RequestBody BusMessageDTO message);

  /**
   * 3 Send message via template
   *
   * @param message set params via constructor
   */
  @PostMapping("/sys/api/sendTemplateAnnouncement")
  void sendTemplateAnnouncement(@RequestBody TemplateMessageDTO message);

  /**
   * 4 Send message via template with business params
   *
   * @param message set params via constructor
   */
  @PostMapping("/sys/api/sendBusTemplateAnnouncement")
  void sendBusTemplateAnnouncement(@RequestBody BusTemplateMessageDTO message);

  /**
   * 5 Generate push content via message-center template
   *
   * @param templateDTO set params via constructor
   * @return
   */
  @PostMapping("/sys/api/parseTemplateByCode")
  String parseTemplateByCode(@RequestBody TemplateDTO templateDTO);

  /**
   * 6ByUseridQueryUserInfo
   *
   * @param id
   * @return
   */
  @SensitiveDecode
  @PostMapping("/sys/api/getUserById")
  LoginUser getUserById(@RequestParam("id") String id);

  /**
   * 7 Query role set by username
   *
   * @param username
   * @return
   */
  @PostMapping("/sys/api/getRolesByUsername")
  List<String> getRolesByUsername(@RequestParam("username") String username);

  /**
   * 7 Query role set by username
   *
   * @param userId
   * @return
   */
  @PostMapping("/sys/api/getRolesByUserId")
  List<String> getRolesByUserId(@RequestParam("userId") String userId);

  /**
   * 8 Query department set by username
   *
   * @param username
   * @return department id
   */
  @PostMapping("/sys/api/getDepartIdsByUsername")
  List<String> getDepartIdsByUsername(@RequestParam("username") String username);

  /**
   * 8 Query department set by username
   *
   * @param userId
   * @return department id
   */
  @PostMapping("/sys/api/getDepartIdsByUserId")
  List<String> getDepartIdsByUserId(@RequestParam("userId") String userId);

  /**
   * 8.2 Query department parent id set by username
   *
   * @param username
   * @return department parentIds
   */
  @PostMapping("/sys/api/getDepartParentIdsByUsername")
  Set<String> getDepartParentIdsByUsername(@RequestParam("username") String username);

  /**
   * 8.3 Query department parent id set
   *
   * @param depIds
   * @return department parentIds
   */
  @PostMapping("/sys/api/getDepartParentIdsByDepIds")
  Set<String> getDepartParentIdsByDepIds(@RequestParam("depIds") Set<String> depIds);

  /**
   * 9 Query department name by username
   *
   * @param username
   * @return department name
   */
  @PostMapping("/sys/api/getDepartNamesByUsername")
  List<String> getDepartNamesByUsername(@RequestParam("username") String username);

  /**
   * 10GetDataDictionary
   *
   * @param code
   * @return
   */
  @Override
  @PostMapping("/sys/api/queryDictItemsByCode")
  List<DictModel> queryDictItemsByCode(@RequestParam("code") String code);

  /**
   * Get valid dictionary items
   *
   * @param code
   * @return
   */
  @Override
  @PostMapping("/sys/api/queryEnableDictItemsByCode")
  public List<DictModel> queryEnableDictItemsByCode(@RequestParam("code") String code);

  /**
   * 11 query all parent dicts, sorted by create_time
   *
   * @return List<DictModel> dict value collection
   */
  @PostMapping("/sys/api/queryAllDict")
  List<DictModel> queryAllDict();

  /**
   * 12 query all category dicts
   *
   * @return
   */
  @PostMapping("/sys/api/queryAllSysCategory")
  List<SysCategoryModel> queryAllSysCategory();

  /**
   * 13GetTableDataDictionary
   *
   * @param tableFilterSql
   * @param text
   * @param code
   * @return
   */
  @Override
  @PostMapping("/sys/api/queryTableDictItemsByCode")
  List<DictModel> queryTableDictItemsByCode(
      @RequestParam("tableFilterSql") String tableFilterSql,
      @RequestParam("text") String text,
      @RequestParam("code") String code);

  /**
   * 14 query all departments as dict info id -->value, departName -->text
   *
   * @return
   */
  @PostMapping("/sys/api/queryAllDepartBackDictModel")
  List<DictModel> queryAllDepartBackDictModel();

  /**
   * 15 update message read status by busType and busId
   *
   * @param busType business type
   * @param busId business id
   */
  @PostMapping("/sys/api/updateSysAnnounReadFlag")
  public void updateSysAnnounReadFlag(
      @RequestParam("busType") String busType, @RequestParam("busId") String busId);

  /**
   * 16 Query table dict with data filtering support
   *
   * @param table
   * @param text
   * @param code
   * @return
   */
  @PostMapping("/sys/api/queryFilterTableDictInfo")
  List<DictModel> queryFilterTableDictInfo(
      @RequestParam("table") String table,
      @RequestParam("text") String text,
      @RequestParam("code") String code);

  /**
   * 17 Query dict by specified table text/code; includes text and value
   *
   * @param table
   * @param text
   * @param code
   * @param keyArray
   * @return
   */
  @Deprecated
  @PostMapping("/sys/api/queryTableDictByKeys")
  public List<String> queryTableDictByKeys(
      @RequestParam("table") String table,
      @RequestParam("text") String text,
      @RequestParam("code") String code,
      @RequestParam("keyArray") String[] keyArray);

  /**
   * 18 Query all users; returns ComboModel
   *
   * @return
   */
  @PostMapping("/sys/api/queryAllUserBackCombo")
  public List<ComboModel> queryAllUserBackCombo();

  /**
   * 19 Paginate users; returns JSONObject
   *
   * @param userIds multiple user ids
   * @param pageNo current page number
   * @param pageSize items per page
   * @return
   */
  @PostMapping("/sys/api/queryAllUser")
  public JSONObject queryAllUser(
      @RequestParam(name = "userIds", required = false) String userIds,
      @RequestParam(name = "pageNo", required = false) Integer pageNo,
      @RequestParam(name = "pageSize", required = false) Integer pageSize);

  /**
   * 20 Get all roles with params
   *
   * @param roleIds default selected roles
   * @return
   */
  @PostMapping("/sys/api/queryAllRole")
  public List<ComboModel> queryAllRole(
      @RequestParam(name = "roleIds", required = false) String[] roleIds);

  /**
   * 21 Query role id set by username
   *
   * @param username
   * @return
   */
  @PostMapping("/sys/api/getRoleIdsByUsername")
  public List<String> getRoleIdsByUsername(@RequestParam("username") String username);

  /**
   * 21.1 Query role id set by user ID
   *
   * @param userId
   * @return
   */
  @PostMapping("/sys/api/getRoleIdsByUserId")
  public List<String> getRoleIdsByUserId(@RequestParam("userId") String userId);

  /**
   * 22 Query department id by department code
   *
   * @param orgCode
   * @return
   */
  @PostMapping("/sys/api/getDepartIdsByOrgCode")
  public String getDepartIdsByOrgCode(@RequestParam("orgCode") String orgCode);

  /**
   * 23 Query all departments
   *
   * @return
   */
  @PostMapping("/sys/api/getAllSysDepart")
  public List<SysDepartModel> getAllSysDepart();

  /**
   * 24 Find parent department
   *
   * @param departId
   * @return
   */
  @PostMapping("/sys/api/getParentDepartId")
  DictModel getParentDepartId(@RequestParam("departId") String departId);

  /**
   * 25 Get department head by department id
   *
   * @param deptId
   * @return
   */
  @PostMapping("/sys/api/getDeptHeadByDepId")
  public List<String> getDeptHeadByDepId(@RequestParam("deptId") String deptId);

  /**
   * 26 Send message to specified users
   *
   * @param userIds
   * @param cmd
   */
  @PostMapping("/sys/api/sendWebSocketMsg")
  public void sendWebSocketMsg(
      @RequestParam("userIds") String[] userIds, @RequestParam("cmd") String cmd);

  /**
   * 27 Get all participating users by id
   *
   * @param userIds multiple user ids
   * @return
   */
  @PostMapping("/sys/api/queryAllUserByIds")
  public List<UserAccountInfo> queryAllUserByIds(@RequestParam("userIds") String[] userIds);

  /**
   * 28 Push meeting check-in info to preview userIds
   *
   * @return
   * @param userId
   */
  @PostMapping("/sys/api/meetingSignWebsocket")
  void meetingSignWebsocket(@RequestParam("userId") String userId);

  /**
   * 29 Get all participating users by name
   *
   * @param userNames multiple usernames
   * @return
   */
  @PostMapping("/sys/api/queryUserByNames")
  List<UserAccountInfo> queryUserByNames(@RequestParam("userNames") String[] userNames);

  /**
   * 30 Get user role set
   *
   * @param username
   * @return
   */
  @PostMapping("/sys/api/getUserRoleSet")
  Set<String> getUserRoleSet(@RequestParam("username") String username);

  /**
   * 30 Get user role set
   *
   * @param userId
   * @return
   */
  @PostMapping("/sys/api/getUserRoleSetById")
  Set<String> getUserRoleSetById(@RequestParam("userId") String userId);

  /**
   * 31 Get user permission set
   *
   * @param userId
   * @return
   */
  @PostMapping("/sys/api/getUserPermissionSet")
  Set<String> getUserPermissionSet(@RequestParam("userId") String userId);

  /**
   * 32 Check online access permission
   *
   * @param onlineAuthDTO
   * @return
   */
  @PostMapping("/sys/api/hasOnlineAuth")
  boolean hasOnlineAuth(@RequestBody OnlineAuthDTO onlineAuthDTO);

  /**
   * 33ApproveDepartmentidGetDepartmentAllInfo
   *
   * @param id Departmentid
   * @return SysDepartModel DepartmentInfo
   */
  @PostMapping("/sys/api/selectAllById")
  SysDepartModel selectAllById(@RequestParam("id") String id);

  /**
   * 34 Query all user ids under the user's company by user id
   *
   * @param userId
   * @return
   */
  @PostMapping("/sys/api/queryDeptUsersByUserId")
  List<String> queryDeptUsersByUserId(@RequestParam("userId") String userId);

  // ---

  /**
   * 35QueryUserRoleInfo
   *
   * @param username
   * @return
   */
  @Override
  @PostMapping("/sys/api/queryUserRoles")
  Set<String> queryUserRoles(@RequestParam("username") String username);

  /**
   * 35QueryUserRoleInfo
   *
   * @param userId
   * @return
   */
  @Override
  @PostMapping("/sys/api/queryUserRolesById")
  Set<String> queryUserRolesById(@RequestParam("userId") String userId);

  /**
   * 36QueryUserPermissionInfo
   *
   * @param userId
   * @return
   */
  @Override
  @PostMapping("/sys/api/queryUserAuths")
  Set<String> queryUserAuths(@RequestParam("userId") String userId);

  /**
   * 37 Query DynamicDataSourceModel stored in DB by id
   *
   * @param dbSourceId
   * @return
   */
  @Override
  @PostMapping("/sys/api/getDynamicDbSourceById")
  DynamicDataSourceModel getDynamicDbSourceById(@RequestParam("dbSourceId") String dbSourceId);

  /**
   * 38 Query DynamicDataSourceModel stored in DB by code
   *
   * @param dbSourceCode
   * @return
   */
  @Override
  @PostMapping("/sys/api/getDynamicDbSourceByCode")
  DynamicDataSourceModel getDynamicDbSourceByCode(
      @RequestParam("dbSourceCode") String dbSourceCode);

  /**
   * 39 Query user info by username (defined in CommonAPI)
   *
   * @param username
   * @return LoginUser user info
   */
  @Override
  @SensitiveDecode
  @PostMapping("/sys/api/getUserByName")
  LoginUser getUserByName(@RequestParam("username") String username);

  /**
   * P3: verify a plaintext login password against the stored credential inside the system
   * service. Cross-service callers (dcs) MUST use this instead of fetching the password hash via
   * {@link #getUserByName(String)} — the hash must never cross the Feign boundary.
   *
   * <p>S2S-only endpoint ({@code /sys/api/verifyPassword}); the underlying credential SQL is
   * firewall-pinned ({@code SensitiveTableBlacklistInterceptor#S2S_CREDENTIAL_VERIFY_MAPPER_IDS})
   * and only executes for trusted {@code s2s:<module>} sessions.
   *
   * @param username    login username
   * @param rawPassword plaintext password supplied for re-verification
   * @return {@code true} only when the account exists and the password matches (modern
   *         SM3PWD/BCrypt hash or legacy V0 PBE fallback); {@code false} otherwise, including
   *         every error case — never throws on bad credentials
   */
  @Override
  @PostMapping("/sys/api/verifyPassword")
  boolean verifyPassword(
      @RequestParam("username") String username,
      @RequestParam("rawPassword") String rawPassword);

  /**
   * 39 Query user ID by username (defined in CommonAPI)
   *
   * @param username
   * @return user ID
   */
  @Override
  @PostMapping("/sys/api/getUserIdByName")
  String getUserIdByName(@RequestParam("username") String username);

  /**
   * 40 Translate dict table entry
   *
   * @param table
   * @param text
   * @param code
   * @param key
   * @return
   */
  @Override
  @PostMapping("/sys/api/translateDictFromTable")
  String translateDictFromTable(
      @RequestParam("table") String table,
      @RequestParam("text") String text,
      @RequestParam("code") String code,
      @RequestParam("key") String key);

  /**
   * 41 Translate plain dict entry
   *
   * @param code
   * @param key
   * @return
   */
  @Override
  @PostMapping("/sys/api/translateDict")
  String translateDict(@RequestParam("code") String code, @RequestParam("key") String key);

  /**
   * 42 Query data permission
   *
   * @param component
   * @param requestPath
   * @param username user real name
   * @return
   */
  @Override
  @PostMapping("/sys/api/queryPermissionDataRule")
  List<SysPermissionDataRuleModel> queryPermissionDataRule(
      @RequestParam("component") String component,
      @RequestParam("requestPath") String requestPath,
      @RequestParam("username") String username);

  /**
   * 43QueryUserInfo
   *
   * @param username
   * @return
   */
  @Override
  @PostMapping("/sys/api/getCacheUser")
  SysUserCacheInfo getCacheUser(@RequestParam("username") String username);

  /**
   * 36 Query multiple users by comma-separated usernames
   *
   * @param usernames
   * @return
   */
  @PostMapping("/sys/api/queryUsersByUsernames")
  List<JSONObject> queryUsersByUsernames(@RequestParam("usernames") String usernames);

  /**
   * 37 Query multiple users by comma-separated user IDs
   *
   * @param ids
   * @return
   */
  @PostMapping("/sys/api/queryUsersByIds")
  List<JSONObject> queryUsersByIds(@RequestParam("ids") String ids);

  /**
   * 38 Query multiple departments by comma-separated org codes
   *
   * @param orgCodes
   * @return
   */
  @PostMapping("/sys/api/queryDepartsByOrgcodes")
  List<JSONObject> queryDepartsByOrgcodes(@RequestParam("orgCodes") String orgCodes);

  //    /**
  //     * 39 Query multiple departments by comma-separated org codes and return department info
  //     * @param ids
  //     * @return
  //     */
  //     @PostMapping("/sys/api/queryDepartsByOrgIds")
  //    List<JSONObject> queryDepartsByOrgIds(@RequestParam("ids") String ids);

  /**
   * 40 Send email message
   *
   * @param email
   * @param title
   * @param content
   */
  @PostMapping("/sys/api/sendEmailMsg")
  void sendEmailMsg(
      @RequestParam("email") String email,
      @RequestParam("title") String title,
      @RequestParam("content") String content);

  /**
   * Send HTML template email message
   *
   * @param email
   * @param title
   * @param emailTemplateEnum email template enum
   * @param params template parameters
   */
  @PostMapping("/sys/api/sendHtmlTemplateEmail")
  void sendHtmlTemplateEmail(
      @RequestParam("email") String email,
      @RequestParam("title") String title,
      @RequestParam("emailEnum") EmailTemplateEnum emailTemplateEnum,
      @RequestParam("params") JSONObject params);

  /**
   * 41 Get sub-departments and all user ids under company
   *
   * @param orgCode department code
   * @return List<Map>
   */
  @PostMapping("/sys/api/getDeptUserByOrgCode")
  List<Map> getDeptUserByOrgCode(@RequestParam("orgCode") String orgCode);

  /**
   * 42 Query category dict translation
   *
   * @param ids multiple category dict ids
   * @return List<String>
   */
  @PostMapping("/sys/api/loadCategoryDictItem")
  List<String> loadCategoryDictItem(@RequestParam("ids") String ids);

  /**
   * 44 Reverse-translate category dict for import
   *
   * @param names names, comma-separated
   */
  @PostMapping("/sys/api/loadCategoryDictItemByNames")
  List<String> loadCategoryDictItemByNames(
      @RequestParam("names") String names, @RequestParam("delNotExist") boolean delNotExist);

  /**
   * 43 Load dict text by dict code
   *
   * @param dictCode order: tableName,text,code
   * @param keys keys to query
   * @return
   */
  @PostMapping("/sys/api/loadDictItem")
  List<String> loadDictItem(
      @RequestParam("dictCode") String dictCode, @RequestParam("keys") String keys);

  /**
   * Copy all dict configs under app to new tenant
   *
   * @param originalAppId original low-code app ID
   * @param appId new low-code app ID
   * @param tenantId new tenant ID
   * @return Map<String, String> Map<original dict code, new dict code>
   */
  @PostMapping("/sys/api/copyLowAppDict")
  Map<String, String> copyLowAppDict(
      @RequestParam("originalAppId") String originalAppId,
      @RequestParam("appId") String appId,
      @RequestParam("tenantId") String tenantId);

  /**
   * 44 Query dict items by dict code
   *
   * @param dictCode order: tableName,text,code
   * @param dictCode keys to query
   * @return
   */
  @PostMapping("/sys/api/getDictItems")
  List<DictModel> getDictItems(@RequestParam("dictCode") String dictCode);

  /**
   * 45 Query dict items for multiple dict codes
   *
   * @param dictCodeList
   * @return key = dictCode; value = corresponding dict items
   */
  @PostMapping("/sys/api/getManyDictItems")
  Map<String, List<DictModel>> getManyDictItems(
      @RequestParam("dictCodeList") List<String> dictCodeList);

  /**
   * 46 [JSearchSelectTag dropdown search component] Large dict table async load; frontend filters by input
   *
   * @param dictCode dict code format: table,text,code
   * @param keyword filter keyword
   * @param pageSize items per page
   * @return
   */
  @PostMapping("/sys/api/loadDictItemByKeyword")
  List<DictModel> loadDictItemByKeyword(
      @RequestParam("dictCode") String dictCode,
      @RequestParam("keyword") String keyword,
      @RequestParam(value = "pageSize", required = false) Integer pageSize);

  /**
   * 47 Query multiple departments by comma-separated department ids
   *
   * @param ids
   * @return
   */
  @PostMapping("/sys/api/queryDepartsByIds")
  List<JSONObject> queryDepartsByIds(@RequestParam("ids") String ids);

  /**
   * 48 Translate plain dict by multiple dictCodes and keys (comma-separated)
   *
   * @param dictCodes
   * @param keys
   * @return
   */
  @Override
  @PostMapping("/sys/api/translateManyDict")
  Map<String, List<DictModel>> translateManyDict(
      @RequestParam("dictCodes") String dictCodes, @RequestParam("keys") String keys);

  // update-begin---author:chenrui ---date:20231221  for: [issues/#5643] Fix cross-database table dictionary query issue in distributed environment ------------

  /**
   * 49 Translate dict table entries (batch supported)
   *
   * @param table
   * @param text
   * @param code
   * @param keys multiple values comma-separated
   * @param ds
   * @return
   */
  @Override
  @PostMapping("/sys/api/translateDictFromTableByKeys")
  List<DictModel> translateDictFromTableByKeys(
      @RequestParam("table") String table,
      @RequestParam("text") String text,
      @RequestParam("code") String code,
      @RequestParam("keys") String keys,
      @RequestParam("ds") String ds);

  // update-end---author:chenrui ---date:20231221  for: [issues/#5643] Fix cross-database table dictionary query issue in distributed environment ------------

  /** Send template message */
  @PostMapping("/sys/api/sendTemplateMessage")
  void sendTemplateMessage(@RequestBody MessageDTO message);

  /**
   * Get template content
   *
   * @param code
   * @return
   */
  @PostMapping("/sys/api/getTemplateContent")
  String getTemplateContent(@RequestParam("code") String code);

  /**
   * AddDataLog
   *
   * @param dataLogDto
   */
  @PostMapping("/sys/api/saveDataLog")
  void saveDataLog(@RequestBody DataLogDTO dataLogDto);

  /**
   * Persist an operation log into {@code mpay_system.sys_log}.
   *
   * <p>Under the per-service sharding scheme, dcs/processengine must NOT write {@code sys_log} to
   * their own schema copies. They should call this endpoint via Feign so the system service
   * performs the local mapper write into {@code mpay_system.sys_log}. This keeps the operation-log
   * table sharded to the system service only.
   *
   * @param logDTO
   */
  @Override
  @PostMapping("/sys/api/addLog")
  void addLog(@RequestBody LogDTO logDTO);

  /**
   * Persist a denied-invocation audit record (cross-service).
   *
   * <p>{@code sys_deny_audit} is sharded to the system service. dcs/processengine dropped their
   * local mirrors, so the H5 JDBC-level guard, the H8 call-audit aspect and the F-class ledger
   * audit call this endpoint instead of writing the table locally.
   *
   * @param denyAuditDTO
   */
  @Override
  @PostMapping("/sys/api/recordDenyAudit")
  void recordDenyAudit(@RequestBody DenyAuditDTO denyAuditDTO);

  /**
   * H6 startup gate probe: is {@code sys_deny_audit} reachable through this service's write path?
   *
   * <p>The owner answers "table exists in my schema"; a non-owner shard forwards the question to
   * the owner, so it can verify deny-audit persistence without keeping a foreign table locally.
   *
   * @return true when deny-audit records can be persisted
   */
  @Override
  @PostMapping("/sys/api/denyAuditHealthy")
  boolean isDenyAuditOwnerAvailable();

  /**
   * Update avatar
   *
   * @param loginUser
   * @return
   */
  @PutMapping("/sys/api/updateAvatar")
  void updateAvatar(@RequestBody LoginUser loginUser);

  @PostMapping("/sys/api/sendAppChatSocket")
  void sendAppChatSocket(@RequestParam(name = "userId") String userId);

  /**
   * ByRoleidQueryRolecode
   *
   * @param id
   * @return
   */
  @PostMapping("/sys/api/getRoleCode")
  String getRoleCodeById(String id);

  /**
   * Query role info by roleCode; comma-separated for multiple
   *
   * @param roleCodes
   * @return
   */
  @PostMapping("/sys/api/queryRoleDictByCode")
  List<DictModel> queryRoleDictByCode(@RequestParam(name = "roleCodes") String roleCodes);

  /**
   * Query users by advanced query conditions
   *
   * @param superQuery
   * @param matchType
   * @return
   */
  @PostMapping("/sys/api/queryUserBySuperQuery")
  List<JSONObject> queryUserBySuperQuery(
      @RequestParam(name = "superQuery") String superQuery,
      @RequestParam(name = "matchType") String matchType);

  /**
   * ByIDConditionQueryUser
   *
   * @param id
   * @return JSONObject
   */
  @PostMapping("/sys/api/queryUserById")
  JSONObject queryUserById(String id);

  /**
   * Query departments by advanced query conditions
   *
   * @param superQuery
   * @param matchType
   * @return
   */
  @PostMapping("/sys/api/queryDeptBySuperQuery")
  List<JSONObject> queryDeptBySuperQuery(
      @RequestParam(name = "superQuery") String superQuery,
      @RequestParam(name = "matchType") String matchType);

  /**
   * Query roles by advanced query conditions
   *
   * @param superQuery
   * @param matchType
   * @return
   */
  @PostMapping("/sys/api/queryRoleBySuperQuery")
  List<JSONObject> queryRoleBySuperQuery(
      @RequestParam(name = "superQuery") String superQuery,
      @RequestParam(name = "matchType") String matchType);

  /**
   * ByTenantIDQueryUserID
   *
   * @param tenantId TenantID
   * @return List<String>
   */
  @PostMapping("/sys/api/selectUserIdByTenantId")
  List<String> selectUserIdByTenantId(@RequestParam("tenantId") String tenantId);

  /**
   * ByDepartmentIDQueryUserID
   *
   * @param deptIds
   * @return
   */
  @PostMapping("/sys/api/queryUserIdsByDeptIds")
  List<String> queryUserIdsByDeptIds(List<String> deptIds);

  /**
   * Query user accounts by department IDs
   *
   * @param deptIds
   * @return
   */
  @PostMapping("/sys/api/queryUserAccountsByDeptIds")
  List<String> queryUserAccountsByDeptIds(List<String> deptIds);

  /**
   * ByRoleCode QueryUserID
   *
   * @param roleCodes
   * @return
   */
  @PostMapping("/sys/api/queryUserIdsByRoleds")
  List<String> queryUserIdsByRoleds(@RequestParam("roleCodes") List<String> roleCodes);

  /**
   * Query user IDs by position IDs
   *
   * @param positionIds
   * @return
   */
  @PostMapping("/sys/api/queryUserIdsByPositionIds")
  List<String> queryUserIdsByPositionIds(List<String> positionIds);

  /**
   * All user accounts under department and sub-departments
   *
   * @param orgCode department code
   * @return
   */
  @PostMapping("/sys/api/getUserAccountsByDepCode")
  public List<String> getUserAccountsByDepCode(@RequestParam("orgCode") String orgCode);

  /**
   * Check whether tables and fields in query SQL are in whitelist
   *
   * @param selectSql
   * @return
   */
  @PostMapping("/sys/api/dictTableWhiteListCheckBySql")
  boolean dictTableWhiteListCheckBySql(@RequestParam("selectSql") String selectSql);

  /**
   * Validate whether table or dict code is in whitelist
   *
   * @param tableOrDictCode table name or dictCode
   * @param fields must be null if dictCode is passed
   * @return
   */
  @PostMapping("/sys/api/dictTableWhiteListCheckByDict")
  boolean dictTableWhiteListCheckByDict(
      @RequestParam("tableOrDictCode") String tableOrDictCode,
      @RequestParam(value = "fields", required = false) String... fields);

  @PostMapping("/sys/user/add")
  Result addUser(@RequestBody cn.hutool.json.JSONObject user);

  @PostMapping("/sys/user/list")
  Result<Page<LoginUser>> queryUserPageList(
      @RequestParam String id,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize);

  @PostMapping("/sys/user/sumRegisterCounts")
  Result<cn.hutool.json.JSONObject> sumRegisterCounts();

  @PostMapping("/sys/user/listSysUserRole")
  Result<JSONArray> listSysUserRole(@RequestBody List<String> userIds);

  @PostMapping("/sys/verifyUsernameAndPassword")
  Result<Boolean> verifyUsernameAndPassword(@RequestBody JSONObject sysLoginModel);

  @PostMapping("/sys/user/listAll")
  Result<Page<cn.hutool.json.JSONObject>> queryAllUserPageList(
      @RequestParam Map<String, String> parameter);

  @PostMapping("/sys/user/getAgentId")
  Result<String> getAgentId();

  @PostMapping("/sys/user/getUserAgent")
  Result<cn.hutool.json.JSONObject> getUserAgent(@RequestParam String userId);

  @PostMapping(value = "/sys/common/uploadTemp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  Result<String> uploadTemp(@RequestPart("file") MultipartFile file, @RequestPart String fileName);

  @PostMapping(value = "/sys/common/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  Result<String> upload(
      @RequestPart("file") MultipartFile file,
      @RequestPart("usageScenarios") org.jeecg.common.enem.UsageScenarios usageScenarios);

  @PostMapping(value = "/sys/api/uploadFileBytes", consumes = MediaType.APPLICATION_JSON_VALUE)
  Result<String> uploadFileBytes(
      @RequestBody org.jeecg.common.api.dto.FileBytesUploadDTO uploadDTO);

  @PostMapping("/sys/api/downloadFile")
  void downloadFile(@RequestParam("objectId") String objectId, @RequestParam("file") File file);

  @PostMapping("/sys/api/shard/listUserIdsByKeyword")
  java.util.List<String> listUserIdsByKeyword(
      @RequestParam(value = "usernameLike", required = false) String usernameLike,
      @RequestParam(value = "realnameLike", required = false) String realnameLike,
      @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted);

  @PostMapping("/sys/api/shard/listUserIdsByInviterId")
  java.util.List<String> listUserIdsByInviterId(@RequestParam("inviterId") String inviterId);

  @PostMapping("/sys/api/shard/getUserLightMapByIds")
  @SuppressWarnings("rawtypes")
  java.util.Map getUserLightMapByIds(@RequestBody java.util.Collection<String> userIds);

  @PostMapping("/sys/api/shard/listUserIdsByRoleCodes")
  java.util.List<String> listUserIdsByRoleCodes(@RequestBody java.util.Collection<String> roleCodes);

  @PostMapping("/sys/api/shard/countUserRegistrationByDateRange")
  Long countUserRegistrationByDateRange(
      @RequestParam("startTime") String startTime,
      @RequestParam("endTime") String endTime,
      @RequestParam(value = "roleCode", required = false) String roleCode);

  default Object getOssFileMeta(@RequestParam("objectId") String objectId) {
    return null;
  }
}
