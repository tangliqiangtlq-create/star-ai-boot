package org.jeecg.common.system.api.fallback;

import cn.hutool.json.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.dto.DataLogDTO;
import org.jeecg.common.api.dto.DenyAuditDTO;
import org.jeecg.common.api.dto.LogDTO;
import org.jeecg.common.api.dto.OnlineAuthDTO;
import org.jeecg.common.api.dto.message.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.enums.EmailTemplateEnum;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.vo.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fallback method entry: checks whether token is unset
 *
 * @author: jeecg-boot
 */
@Slf4j
public class SysBaseAPIFallback implements ISysBaseAPI {

  @Setter private Throwable cause;

  @Override
  public void sendBusAnnouncement(BusMessageDTO message) {
    log.error("SendMessageFailed {}", cause);
  }

  @Override
  public void sendTemplateAnnouncement(TemplateMessageDTO message) {
    log.error("SendMessageFailed {}", cause);
  }

  @Override
  public void sendBusTemplateAnnouncement(BusTemplateMessageDTO message) {
    log.error("SendMessageFailed {}", cause);
  }

  @Override
  public String parseTemplateByCode(TemplateDTO templateDTO) {
    log.error("Failed to get message content via template {}", cause);
    return null;
  }

  @Override
  public LoginUser getUserById(String id) {
    return null;
  }

  @Override
  public List<String> getRolesByUsername(String username) {
    return null;
  }

  @Override
  public List<String> getRolesByUserId(String userId) {
    return null;
  }

  @Override
  public List<String> getDepartIdsByUsername(String username) {
    return null;
  }

  @Override
  public List<String> getDepartIdsByUserId(String userId) {
    return null;
  }

  @Override
  public Set<String> getDepartParentIdsByUsername(String username) {
    return null;
  }

  @Override
  public Set<String> getDepartParentIdsByDepIds(Set<String> depIds) {
    return null;
  }

  @Override
  public List<String> getDepartNamesByUsername(String username) {
    return null;
  }

  @Override
  public List<DictModel> queryDictItemsByCode(String code) {
    return null;
  }

  @Override
  public List<DictModel> queryEnableDictItemsByCode(String code) {
    return null;
  }

  @Override
  public List<DictModel> queryAllDict() {
    log.error("feginAPIqueryAllDictFailed：", cause);
    return null;
  }

  @Override
  public List<SysCategoryModel> queryAllSysCategory() {
    return null;
  }

  @Override
  public List<DictModel> queryTableDictItemsByCode(
      String tableFilterSql, String text, String code) {
    return null;
  }

  @Override
  public List<DictModel> queryAllDepartBackDictModel() {
    return null;
  }

  @Override
  public void updateSysAnnounReadFlag(String busType, String busId) {}

  @Override
  public List<DictModel> queryFilterTableDictInfo(String table, String text, String code) {
    return null;
  }

  @Override
  public List<String> queryTableDictByKeys(
      String table, String text, String code, String[] keyArray) {
    log.error("queryTableDictByKeysQuery failed {}", cause);
    return null;
  }

  @Override
  public List<ComboModel> queryAllUserBackCombo() {
    return null;
  }

  @Override
  public JSONObject queryAllUser(String userIds, Integer pageNo, Integer pageSize) {
    return null;
  }

  @Override
  public List<ComboModel> queryAllRole(String[] roleIds) {
    log.error("GetRoleInfoFailed {}", cause);
    return null;
  }

  @Override
  public List<String> getRoleIdsByUsername(String username) {
    return null;
  }

  @Override
  public List<String> getRoleIdsByUserId(String userId) {
    return null;
  }

  @Override
  public String getDepartIdsByOrgCode(String orgCode) {
    return null;
  }

  @Override
  public List<SysDepartModel> getAllSysDepart() {
    return null;
  }

  @Override
  public DictModel getParentDepartId(String departId) {
    return null;
  }

  @Override
  public List<String> getDeptHeadByDepId(String deptId) {
    return null;
  }

  @Override
  public void sendWebSocketMsg(String[] userIds, String cmd) {}

  @Override
  public List<UserAccountInfo> queryAllUserByIds(String[] userIds) {
    return null;
  }

  @Override
  public void meetingSignWebsocket(String userId) {}

  @Override
  public List<UserAccountInfo> queryUserByNames(String[] userNames) {
    return null;
  }

  @Override
  public Set<String> getUserRoleSet(String username) {
    return null;
  }

  @Override
  public Set<String> getUserRoleSetById(String userId) {
    return null;
  }

  @Override
  public Set<String> getUserPermissionSet(String userId) {
    return null;
  }

  @Override
  public boolean hasOnlineAuth(OnlineAuthDTO onlineAuthDTO) {
    return false;
  }

  @Override
  public SysDepartModel selectAllById(String id) {
    return null;
  }

  @Override
  public List<String> queryDeptUsersByUserId(String userId) {
    return null;
  }

  @Override
  public Set<String> queryUserRoles(String username) {
    return null;
  }

  @Override
  public Set<String> queryUserRolesById(String userId) {
    return null;
  }

  @Override
  public Set<String> queryUserAuths(String userId) {
    return null;
  }

  @Override
  public DynamicDataSourceModel getDynamicDbSourceById(String dbSourceId) {
    return null;
  }

  @Override
  public DynamicDataSourceModel getDynamicDbSourceByCode(String dbSourceCode) {
    return null;
  }

  @Override
  public LoginUser getUserByName(String username) {
    log.error("jeecg-system service node unreachable, failed to fetch login user info: ", cause);
    return null;
  }

  @Override
  public boolean verifyPassword(String username, String rawPassword) {
    // Fail-closed: never report credential success when the system node is unreachable.
    log.error("jeecg-system service node unreachable, password verification denied: ", cause);
    return false;
  }

  @Override
  public String getUserIdByName(String username) {
    return null;
  }

  @Override
  public String translateDictFromTable(String table, String text, String code, String key) {
    return null;
  }

  @Override
  public String translateDict(String code, String key) {
    return null;
  }

  @Override
  public List<SysPermissionDataRuleModel> queryPermissionDataRule(
      String component, String requestPath, String username) {
    return null;
  }

  @Override
  public SysUserCacheInfo getCacheUser(String username) {
    log.error("GetUserInfoFailed {}", cause);
    return null;
  }

  @Override
  public List<JSONObject> queryUsersByUsernames(String usernames) {
    return null;
  }

  @Override
  public List<JSONObject> queryUsersByIds(String ids) {
    return null;
  }

  @Override
  public List<JSONObject> queryDepartsByOrgcodes(String orgCodes) {
    return null;
  }

  @Override
  public List<JSONObject> queryDepartsByIds(String ids) {
    return null;
  }

  @Override
  public Map<String, List<DictModel>> translateManyDict(String dictCodes, String keys) {
    return null;
  }

  // update-begin---author:chenrui ---date:20231221  for: [issues/#5643] Fix cross-database table dictionary query issue in distributed environment ------------
  @Override
  public List<DictModel> translateDictFromTableByKeys(
      String table, String text, String code, String keys, String dataSource) {
    return null;
  }

  // update-end---author:chenrui ---date:20231221  for: [issues/#5643] Fix cross-database table dictionary query issue in distributed environment ------------

  @Override
  public void sendTemplateMessage(MessageDTO message) {}

  @Override
  public String getTemplateContent(String code) {
    return null;
  }

  @Override
  public void saveDataLog(DataLogDTO dataLogDto) {}

  @Override
  public void addLog(LogDTO logDTO) {
    // Don't throw — callers (e.g. AutoLogAspect) wrap this in async flush and never want logs to
    // break business. A missing log record is acceptable; an exception propagating into the
    // caller's business flow is not.
    log.warn("addLog Feign call failed, fallback triggered; log dropped: {}", cause);
  }

  @Override
  public void recordDenyAudit(DenyAuditDTO denyAuditDTO) {
    // Deny-audit is a security record, but it must stay fail-soft at the transport layer: H5/H8/F
    // writers run inside request handling and an audit outage may not fail the business response.
    // The outage is surfaced through a WARN log (and the caller-side Micrometer counters).
    log.warn("recordDenyAudit Feign call failed, fallback triggered; deny-audit dropped: reasonCode={}, cause={}",
        denyAuditDTO == null ? null : denyAuditDTO.getReasonCode(), cause);
  }

  @Override
  public boolean isDenyAuditOwnerAvailable() {
    // Fail-closed: an unreachable owner must not be reported as healthy.
    log.warn("isDenyAuditOwnerAvailable Feign call failed, fallback triggered; reporting unavailable: {}", cause);
    return false;
  }

  @Override
  public void sendEmailMsg(String email, String title, String content) {}

  @Override
  public void sendHtmlTemplateEmail(
      String email, String title, EmailTemplateEnum emailTemplateEnum, JSONObject params) {}

  @Override
  public List<Map> getDeptUserByOrgCode(String orgCode) {
    return null;
  }

  //    @Override
  //    public List<JSONObject> queryDepartsByOrgIds(String ids) {
  //        return null;
  //    }

  @Override
  public List<String> loadCategoryDictItem(String ids) {
    return null;
  }

  @Override
  public List<String> loadCategoryDictItemByNames(String names, boolean delNotExist) {
    return null;
  }

  @Override
  public List<String> loadDictItem(String dictCode, String keys) {
    return null;
  }

  @Override
  public Map<String, String> copyLowAppDict(String originalAppId, String appId, String tenantId) {
    return null;
  }

  @Override
  public List<DictModel> getDictItems(String dictCode) {
    return null;
  }

  @Override
  public Map<String, List<DictModel>> getManyDictItems(List<String> dictCodeList) {
    return null;
  }

  @Override
  public List<DictModel> loadDictItemByKeyword(String dictCode, String keyword, Integer pageSize) {
    return null;
  }

  @Override
  public void updateAvatar(LoginUser loginUser) {}

  @Override
  public void sendAppChatSocket(String userId) {}

  @Override
  public String getRoleCodeById(String id) {
    return null;
  }

  @Override
  public List<DictModel> queryRoleDictByCode(String roleCodes) {
    return null;
  }

  @Override
  public List<JSONObject> queryUserBySuperQuery(String superQuery, String matchType) {
    return null;
  }

  @Override
  public JSONObject queryUserById(String id) {
    return null;
  }

  @Override
  public List<JSONObject> queryDeptBySuperQuery(String superQuery, String matchType) {
    return null;
  }

  @Override
  public List<JSONObject> queryRoleBySuperQuery(String superQuery, String matchType) {
    return null;
  }

  @Override
  public List<String> selectUserIdByTenantId(String tenantId) {
    return null;
  }

  @Override
  public List<String> queryUserIdsByDeptIds(List<String> deptIds) {
    return null;
  }

  @Override
  public List<String> queryUserAccountsByDeptIds(List<String> deptIds) {
    return null;
  }

  @Override
  public List<String> queryUserIdsByRoleds(List<String> roleCodes) {
    return null;
  }

  @Override
  public List<String> queryUserIdsByPositionIds(List<String> positionIds) {
    return null;
  }

  @Override
  public List<String> getUserAccountsByDepCode(String orgCode) {
    return null;
  }

  @Override
  public boolean dictTableWhiteListCheckBySql(String selectSql) {
    return false;
  }

  @Override
  public boolean dictTableWhiteListCheckByDict(String tableOrDictCode, String... fields) {
    return false;
  }

  @Override
  public Result addUser(cn.hutool.json.JSONObject user) {
    return null;
  }

  @Override
  public Result<Page<LoginUser>> queryUserPageList(String id, Integer pageNo, Integer pageSize) {
    return null;
  }

  @Override
  public Result<cn.hutool.json.JSONObject> sumRegisterCounts() {
    return null;
  }

  @Override
  public Result<JSONArray> listSysUserRole(List<String> userIds) {
    return null;
  }

  @Override
  public Result<Boolean> verifyUsernameAndPassword(JSONObject sysLoginModel) {
    return null;
  }

  @Override
  public Result<Page<cn.hutool.json.JSONObject>> queryAllUserPageList(
      Map<String, String> parameter) {
    return null;
  }

  @Override
  public Result<String> getAgentId() {
    return null;
  }

  @Override
  public Result<cn.hutool.json.JSONObject> getUserAgent(String userId) {
    return null;
  }

  @Override
  public Result uploadTemp(MultipartFile file, String fileName) {
    return null;
  }

  @Override
  public Result<String> upload(
      MultipartFile file, org.jeecg.common.enem.UsageScenarios usageScenarios) {
    return null;
  }

  @Override
  public Result<String> uploadFileBytes(org.jeecg.common.api.dto.FileBytesUploadDTO uploadDTO) {
    return null;
  }

  @Override
  public java.util.List<String> listUserIdsByKeyword(
      String usernameLike, String realnameLike, boolean includeDeleted) {
    return java.util.Collections.emptyList();
  }

  @Override
  public java.util.List<String> listUserIdsByInviterId(String inviterId) {
    return java.util.Collections.emptyList();
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public java.util.Map getUserLightMapByIds(java.util.Collection userIds) {
    return java.util.Collections.emptyMap();
  }

  @Override
  public java.util.List<String> listUserIdsByRoleCodes(java.util.Collection<String> roleCodes) {
    return java.util.Collections.emptyList();
  }

  @Override
  public Long countUserRegistrationByDateRange(
      String startTime, String endTime, String roleCode) {
    return 0L;
  }

  @Override
  public Object getOssFileMeta(String objectId) {
    return null;
  }

  @Override
  public void downloadFile(String objectId, java.io.File localFile) {
  }
}
