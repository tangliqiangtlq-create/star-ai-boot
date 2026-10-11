package org.jeecg.common.system.api;

import com.alibaba.fastjson.JSONObject;
import java.io.File;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.api.dto.DataLogDTO;
import org.jeecg.common.api.dto.OnlineAuthDTO;
import org.jeecg.common.api.dto.message.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.enums.EmailTemplateEnum;
import org.jeecg.common.enem.UsageScenarios;
import org.jeecg.common.system.vo.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description Common business API for other independent modules @Author scott @Date 2019-4-20 @Version V1.0
 */
public interface ISysBaseAPI extends CommonAPI {

  // =======OLD system message push APIs============================
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
  void sendBusAnnouncement(BusMessageDTO message);

  /**
   * 3 Send message via template
   *
   * @param message set params via constructor
   */
  void sendTemplateAnnouncement(TemplateMessageDTO message);

  /**
   * 4 Send message via template with business params
   *
   * @param message set params via constructor
   */
  void sendBusTemplateAnnouncement(BusTemplateMessageDTO message);

  /**
   * 5 Generate push content via message-center template
   *
   * @param templateDTO set params via constructor
   * @return
   */
  String parseTemplateByCode(TemplateDTO templateDTO);

  // =======OLD system message push APIs============================

  // =======TY NEW custom message push APIs: email, DingTalk, WeChat Work, system message============================
  /**
   * NEW Send template message [new; supports custom push types: email, DingTalk, WeCom, system message]
   *
   * @param message
   */
  void sendTemplateMessage(MessageDTO message);

  /**
   * NEW Get template content by template code [new; supports custom push types]
   *
   * @param templateCode
   * @return
   */
  String getTemplateContent(String templateCode);

  // =======TY NEW custom message push APIs: email, DingTalk, WeChat Work, system message============================

  /**
   * 6ByUseridQueryUserInfo
   *
   * @param id
   * @return
   */
  LoginUser getUserById(String id);

  /**
   * 7 Query role set by username
   *
   * @param username
   * @return
   */
  List<String> getRolesByUsername(String username);

  /**
   * 7 Query role set by username
   *
   * @param userId
   * @return
   */
  List<String> getRolesByUserId(String userId);

  /**
   * 8 Query department set by username
   *
   * @param username
   * @return department id
   */
  List<String> getDepartIdsByUsername(String username);

  /**
   * 8 Query department set by username
   *
   * @param userId
   * @return department id
   */
  List<String> getDepartIdsByUserId(String userId);

  /**
   * 8.2 Query department parent id set by username
   *
   * @param username
   * @return department parentIds
   */
  Set<String> getDepartParentIdsByUsername(String username);

  /**
   * 8.2 Query department parent id set
   *
   * @param depIds
   * @return department parentIds
   */
  Set<String> getDepartParentIdsByDepIds(Set<String> depIds);

  /**
   * 9 Query department name by username
   *
   * @param username
   * @return department name
   */
  List<String> getDepartNamesByUsername(String username);

  /**
   * 11 query all parent dicts, sorted by create_time
   *
   * @return List<DictModel> dict collection
   */
  public List<DictModel> queryAllDict();

  /**
   * 12 query all category dicts
   *
   * @return
   */
  public List<SysCategoryModel> queryAllSysCategory();

  /**
   * 14 query all departments as dict info id -->value, departName -->text
   *
   * @return
   */
  public List<DictModel> queryAllDepartBackDictModel();

  /**
   * 15 Mark message as read by business type and business id
   *
   * @param busType
   * @param busId
   */
  public void updateSysAnnounReadFlag(String busType, String busId);

  /**
   * 16 Query table dict with data filtering support
   *
   * @param table
   * @param text
   * @param code
   * @return
   */
  public List<DictModel> queryFilterTableDictInfo(String table, String text, String code);

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
  public List<String> queryTableDictByKeys(
      String table, String text, String code, String[] keyArray);

  /**
   * 18 Query all users; returns ComboModel
   *
   * @return
   */
  public List<ComboModel> queryAllUserBackCombo();

  /**
   * 19 Paginate users; returns JSONObject
   *
   * @param userIds multiple user ids
   * @param pageNo current page number
   * @param pageSize items per page
   * @return
   */
  public JSONObject queryAllUser(String userIds, Integer pageNo, Integer pageSize);

  /**
   * 20 Get all roles
   *
   * @return
   */
  public List<ComboModel> queryAllRole();

  /**
   * 21 Get all roles with params
   *
   * @param roleIds default selected roles
   * @return
   */
  public List<ComboModel> queryAllRole(String[] roleIds);

  /**
   * 22 Query role id set by username
   *
   * @param username
   * @return
   */
  public List<String> getRoleIdsByUsername(String username);

  /**
   * 22.1 Query role id set by user ID
   *
   * @param userId
   * @return
   */
  public List<String> getRoleIdsByUserId(String userId);

  /**
   * 23 Query department id by department code
   *
   * @param orgCode
   * @return
   */
  public String getDepartIdsByOrgCode(String orgCode);

  /**
   * 24 Query all departments
   *
   * @return
   */
  public List<SysDepartModel> getAllSysDepart();

  /**
   * 25 Find parent department
   *
   * @param departId
   * @return
   */
  DictModel getParentDepartId(String departId);

  /**
   * 26 Get department head by department id
   *
   * @param deptId
   * @return
   */
  public List<String> getDeptHeadByDepId(String deptId);

  /**
   * 27 Send message to specified users
   *
   * @param userIds
   * @param cmd
   */
  public void sendWebSocketMsg(String[] userIds, String cmd);

  /**
   * 28 Get all participating users by id
   *
   * @param userIds multiple user ids
   * @return
   */
  public List<UserAccountInfo> queryAllUserByIds(String[] userIds);

  /**
   * 29 Push meeting check-in info to preview userIds
   *
   * @return
   * @param userId
   */
  void meetingSignWebsocket(String userId);

  /**
   * 30 Get all participating users by name
   *
   * @param userNames multiple user accounts
   * @return
   */
  List<UserAccountInfo> queryUserByNames(String[] userNames);

  /**
   * Query users by advanced query conditions
   *
   * @param superQuery
   * @param matchType
   * @return
   */
  List<JSONObject> queryUserBySuperQuery(String superQuery, String matchType);

  /**
   * ByIDQueryUser
   *
   * @param id
   * @return
   */
  JSONObject queryUserById(String id);

  /**
   * Query departments by advanced query conditions
   *
   * @param superQuery
   * @param matchType
   * @return
   */
  List<JSONObject> queryDeptBySuperQuery(String superQuery, String matchType);

  /**
   * Query roles by advanced query conditions
   *
   * @param superQuery
   * @param matchType
   * @return
   */
  List<JSONObject> queryRoleBySuperQuery(String superQuery, String matchType);

  /**
   * ByTenantIDQueryUserID
   *
   * @param tenantId TenantID
   * @return List<String>
   */
  List<String> selectUserIdByTenantId(String tenantId);

  /**
   * 31 Get user role set
   *
   * @param username
   * @return
   */
  Set<String> getUserRoleSet(String username);

  /**
   * 31 Get user role set
   *
   * @param useId
   * @return
   */
  Set<String> getUserRoleSetById(String useId);

  /**
   * 32 Get user permission set
   *
   * @param userId
   * @return
   */
  Set<String> getUserPermissionSet(String userId);

  /**
   * 33 Check online access permission
   *
   * @param onlineAuthDTO
   * @return
   */
  boolean hasOnlineAuth(OnlineAuthDTO onlineAuthDTO);

  /**
   * 34ApproveDepartmentidGetDepartmentAllInfo
   *
   * @param id Departmentid
   * @return SysDepartModelObject
   */
  SysDepartModel selectAllById(String id);

  /**
   * 35 Query all user ids under the user's company by user id
   *
   * @param userId
   * @return
   */
  List<String> queryDeptUsersByUserId(String userId);

  /**
   * 36 Query multiple users by comma-separated usernames
   *
   * @param usernames
   * @return
   */
  List<JSONObject> queryUsersByUsernames(String usernames);

  /**
   * 37 Query multiple users by comma-separated user IDs
   *
   * @param ids
   * @return
   */
  List<JSONObject> queryUsersByIds(String ids);

  /**
   * 38 Query multiple departments by comma-separated org codes
   *
   * @param orgCodes
   * @return
   */
  List<JSONObject> queryDepartsByOrgcodes(String orgCodes);

  /**
   * 39 Query multiple departments by comma-separated department ids
   *
   * @param ids
   * @return
   */
  List<JSONObject> queryDepartsByIds(String ids);

  /**
   * 40 Send email message
   *
   * @param email
   * @param title
   * @param content
   */
  void sendEmailMsg(String email, String title, String content);

  /**
   * 40 Send template email message
   *
   * @param email recipient email
   * @param title email subject
   * @param emailTemplateEnum email template enum
   * @param params template params
   */
  void sendHtmlTemplateEmail(
      String email, String title, EmailTemplateEnum emailTemplateEnum, JSONObject params);

  /**
   * 41 Get sub-departments and all user info under company
   *
   * @param orgCode
   * @return List<Map>
   */
  List<Map> getDeptUserByOrgCode(String orgCode);

  /**
   * Query category dictionary translations
   *
   * @param ids multiple category dictionary ids
   * @return List<String>
   */
  List<String> loadCategoryDictItem(String ids);

  /**
   * Reverse-translate category dict for import
   *
   * @param names names, comma-separated
   */
  List<String> loadCategoryDictItemByNames(String names, boolean delNotExist);

  /**
   * Load dict text by dict code
   *
   * @param dictCode order: tableName,text,code
   * @param keys keys to query
   * @return
   */
  List<String> loadDictItem(String dictCode, String keys);

  /**
   * Copy all dict configs under app to new tenant
   *
   * @param originalAppId original low-code app ID
   * @param appId new low-code app ID
   * @param tenantId new tenant ID
   * @return Map<String, String> Map<original dict code, new dict code>
   */
  Map<String, String> copyLowAppDict(String originalAppId, String appId, String tenantId);

  /**
   * Query dict items by dict code
   *
   * @param dictCode order: tableName,text,code
   * @param dictCode key to query
   * @return
   */
  List<DictModel> getDictItems(String dictCode);

  /**
   * Query multiple dict items by multiple dict codes
   *
   * @param dictCodeList
   * @return key = dictCode; value = corresponding dict items
   */
  Map<String, List<DictModel>> getManyDictItems(List<String> dictCodeList);

  /**
   * [JSearchSelectTag dropdown search component API] Large dict tables use async load; frontend filters data by input
   *
   * @param dictCode dict code format: table,text,code
   * @param keyword filter keyword
   * @param pageSize page size
   * @return
   */
  List<DictModel> loadDictItemByKeyword(String dictCode, String keyword, Integer pageSize);

  /**
   * AddDataLog
   *
   * @param dataLogDto
   */
  void saveDataLog(DataLogDTO dataLogDto);

  /**
   * Update avatar
   *
   * @param loginUser
   */
  void updateAvatar(LoginUser loginUser);

  /**
   * Push chat refresh message to app websocket
   *
   * @param userId
   */
  void sendAppChatSocket(String userId);

  /**
   * ByRoleidQueryRolecode
   *
   * @param id
   * @return
   */
  String getRoleCodeById(String id);

  /**
   * Query role info by roleCode; comma-separated for multiple
   *
   * @param roleCodes
   * @return
   */
  List<DictModel> queryRoleDictByCode(String roleCodes);

  /**
   * ByDepartmentIDQueryUserID
   *
   * @param deptIds
   * @return
   */
  List<String> queryUserIdsByDeptIds(List<String> deptIds);

  /**
   * Query user accounts by department IDs
   *
   * @param deptIds
   * @return
   */
  List<String> queryUserAccountsByDeptIds(List<String> deptIds);

  /**
   * ByRoleCode QueryUserID
   *
   * @param roleCodes
   * @return
   */
  List<String> queryUserIdsByRoleds(List<String> roleCodes);

  /**
   * Query user IDs by position IDs
   *
   * @param positionIds
   * @return
   */
  List<String> queryUserIdsByPositionIds(List<String> positionIds);

  /**
   * All user accounts under department and sub-departments
   *
   * @param orgCode department code
   * @return
   */
  public List<String> getUserAccountsByDepCode(String orgCode);

  /**
   * Check whether tables and fields in query SQL are in whitelist
   *
   * @param selectSql
   * @return
   */
  boolean dictTableWhiteListCheckBySql(String selectSql);

  /**
   * Validate whether table or dict code is in whitelist
   *
   * @param tableOrDictCode table name or dictCode
   * @param fields must be null if dictCode is passed
   * @return
   */
  boolean dictTableWhiteListCheckByDict(String tableOrDictCode, String... fields);

  Result<?> upload(MultipartFile file, UsageScenarios usageScenarios);

  /**
   * Upload file via Base64 byte stream (inter-service call; avoids multipart encoding issues).
   * Returns objectId without domain.
   */
  Result<String> uploadFileBytes(org.jeecg.common.api.dto.FileBytesUploadDTO uploadDTO);

  void downloadFile(String objectId, File file);

  default Object getOssFileMeta(String objectId) {
    return null;
  }

  // =======SHARD-ADAPT T7：分库后 DCS→SYSTEM 先查ID再IN 的跨模块入口============================

  /**
   * SHARD-ADAPT T7 模式1：按用户名 / 真实名模糊查询命中的用户 ID 集合（仅返回 id）。
   *
   * @param usernameLike 用户名模糊条件，可为空
   * @param realnameLike 真实名模糊条件，可为空
   * @param includeDeleted 是否包含已逻辑删除（{@code del_flag != 0}）的用户。
   *     {@code false} = 仅未删除用户（等价原 SQL 的 {@code and del_flag = 0}）；
   *     {@code true} = 不过滤删除标记（等价原 SQL 未带 del_flag 条件，保证历史/审计记录仍可按用户名命中）
   * @return 命中的用户 ID 列表（恒非 null；两者皆空时返回空集合）
   */
  List<String> listUserIdsByKeyword(
      String usernameLike, String realnameLike, boolean includeDeleted);

  /**
   * SHARD-ADAPT T7 模式1：按邀请人 ID 查询被邀请的用户 ID 集合。
   *
   * @param inviterId 邀请人用户 ID
   * @return 命中的用户 ID 列表（恒非 null）
   */
  List<String> listUserIdsByInviterId(String inviterId);

  /**
   * SHARD-ADAPT T7 模式1：按角色编码批量查询命中的用户 ID 集合。
   *
   * @param roleCodes 角色编码集合
   * @return 命中的用户 ID 列表（恒非 null）
   */
  List<String> listUserIdsByRoleCodes(Collection<String> roleCodes);

  /**
   * SHARD-ADAPT T7：统计指定时间区间内注册（创建）的用户数，可按角色编码过滤。
   *
   * @param startTime 起始时间，格式 {@code yyyy-MM-dd HH:mm:ss}
   * @param endTime 结束时间，格式 {@code yyyy-MM-dd HH:mm:ss}
   * @param roleCode 角色编码，可为空（为空时统计全部）
   * @return 注册用户数（恒非 null）
   */
  Long countUserRegistrationByDateRange(String startTime, String endTime, String roleCode);
}
