package org.jeecg.modules.system.service;

import cn.hutool.json.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.system.vo.SysUserCacheInfo;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.model.SysUserSysDepartModel;
import org.jeecg.modules.system.vo.lowapp.DepartAndUserInfo;
import org.jeecg.modules.system.vo.lowapp.UpdateDepartInfo;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.ModelAndView;

/**
 * User table service @Author scott
 *
 * @since 2018-12-20
 */
public interface ISysUserService extends IService<SysUser> {

  /**
   * QueryUserDataList
   *
   * @param req
   * @param queryWrapper
   * @param pageSize
   * @param pageNo
   * @return
   */
  Result<IPage<SysUser>> queryPageList(
      HttpServletRequest req, QueryWrapper<SysUser> queryWrapper, Integer pageSize, Integer pageNo);

  /**
   * Reset password
   *
   * @param username
   * @param oldpassword
   * @param newpassword
   * @param confirmpassword
   * @return
   */
  public Result<?> resetPassword(
      String username, String oldpassword, String newpassword, String confirmpassword);

  /**
   * Change password
   *
   * @param sysUser
   * @return
   */
  public Result<?> changePassword(SysUser sysUser);

  /**
   * DeleteUser
   *
   * @param userId
   * @return
   */
  public boolean deleteUser(String userId);

  /**
   * Batch deleteUser
   *
   * @param userIds
   * @return
   */
  public boolean deleteBatchUsers(String userIds);

  /**
   * Query by username
   *
   * @param username username
   * @return SysUser
   */
  public SysUser getUserByName(String username);

  /**
   * C6-safe variant of {@link #getUserByName(String)} for already-authenticated callers (e.g.
   * /sys/user/getUserInfo). Returns a SysUser with only the firewall-allowed columns populated
   * (no password/salt/reason/is_sender/wallet_address/financial_state/kyc_attachment_*).
   * Use {@link #getUserByName(String)} for credential verification at /sys/login (anonymous).
   */
  public SysUser getUserByNameForInfo(String username);

  /**
   * Add user and user-role relations
   *
   * @param user
   * @param roles
   */
  public void addUserWithRole(SysUser user, String roles);

  /**
   * Update user and user-role relations
   *
   * @param user
   * @param roles
   */
  public void editUserWithRole(SysUser user, String roles);

  /**
   * Get authorized roles for user
   *
   * @param username
   * @return
   */
  public List<String> getRole(String username);

  /**
   * Get dynamic home page by login user roles
   *
   * @param username
   * @param version front-end UI version
   * @return
   */

  /**
   * Query user info including departments
   *
   * @param username
   * @return
   */
  @Deprecated
  public SysUserCacheInfo getCacheUser(String username);

  /**
   * ByDepartmentIdQuery
   *
   * @param page
   * @param departId Departmentid
   * @param username UserAccountName
   * @return
   */
  public IPage<SysUser> getUserByDepId(Page<SysUser> page, String departId, String username);

  /**
   * Query by department ids
   *
   * @param page
   * @param departIds department id set
   * @param username user account name
   * @return
   */
  public IPage<SysUser> getUserByDepIds(
      Page<SysUser> page, List<String> departIds, String username);

  /**
   * Query department names by userIds (comma-separated)
   *
   * @param userIds
   * @return
   */
  public Map<String, String> getDepNamesByUserIds(List<String> userIds);

  /**
   * Query by department id and QueryWrapper
   *
   * @param page
   * @param departId
   * @param queryWrapper
   * @return
   */
  // update-begin-author:taoyan date:2022-9-13 for: VUEN-2245 vulnerability pending 20220906 ----SQL injection
  // Unused method; commented out
  // public IPage<SysUser> getUserByDepartIdAndQueryWrapper(Page<SysUser> page, String departId,
  // QueryWrapper<SysUser> queryWrapper);
  // update-end-author:taoyan date:2022-9-13 for: VUEN-2245 vulnerability pending 20220906 ----SQL injection; unused method commented out

  /**
   * Query users by orgCode including child departments
   *
   * @param orgCode
   * @param userParams user query params, nullable
   * @param page pagination params
   * @return
   */
  IPage<SysUserSysDepartModel> queryUserByOrgCode(String orgCode, SysUser userParams, IPage page);

  /**
   * ByRoleIdQuery
   *
   * @param page
   * @param roleId Roleid
   * @param username UserAccountName
   * @return
   */
  public IPage<SysUser> getUserByRoleId(Page<SysUser> page, String roleId, String username);

  /**
   * ByMultipleRoleIdsQuery (foreach bind, no SQL string concat)
   *
   * @param page
   * @param roleIds Role id list
   * @param username User account name
   * @return
   */
  IPage<SysUser> getUserByRoleIds(Page<SysUser> page, List<String> roleIds, String username);

  /**
   * Get user ids by multiple role ids (foreach bind for Controller inSql replacement)
   * @param roleIds role id list
   * @return user id list
   */
  List<String> getUserIdsByRoleIds(List<String> roleIds);

  /**
   * Get user roles by username
   *
   * @param username username
   * @return role set
   */
  Set<String> getUserRolesSet(String username);

  /**
   * Get user roles by username
   *
   * @param userId user id
   * @return role set
   */
  Set<String> getUserRoleSetById(String userId);

  /**
   * Get user permissions by username
   *
   * @param userId user id
   * @return permission set
   */
  Set<String> getUserPermissionsSet(String userId);

  /**
   * Set department id by username
   *
   * @param username
   * @param orgCode
   */
  void updateUserDepart(String username, String orgCode, Integer loginTenantId);

  /**
   * Get username and password by phone
   *
   * @param phone phone
   * @return SysUser
   */
  public SysUser getUserByPhone(String phone);

  /**
   * Get user by email
   *
   * @param email email
   * @return SysUser
   */
  public SysUser getUserByEmail(String email);

  /**
   * Add user and user-department relations
   *
   * @param user
   * @param selectedParts
   */
  void addUserWithDepart(SysUser user, String selectedParts);

  /**
   * Edit user and user-department relations
   *
   * @param user
   * @param departs
   */
  void editUserWithDepart(SysUser user, String departs);

  /**
   * Validate whether user is active
   *
   * @param sysUser
   * @return
   */
  Result checkUserIsEffective(SysUser sysUser);

  /**
   * Query soft-deleted users
   *
   * @return List<SysUser>
   */
  List<SysUser> queryLogicDeleted();

  /**
   * Query soft-deleted users (optional conditions)
   *
   * @param wrapper
   * @return List<SysUser>
   */
  List<SysUser> queryLogicDeleted(LambdaQueryWrapper<SysUser> wrapper);

  /**
   * Restore soft-deleted users
   *
   * @param userIds user id set
   * @param updateEntity
   * @return boolean
   */
  boolean revertLogicDeleted(List<String> userIds, SysUser updateEntity);

  /**
   * Hard-delete soft-deleted users
   *
   * @param userIds user id set
   * @return boolean
   */
  boolean removeLogicDeleted(List<String> userIds);

  /**
   * Normalize empty phone/email strings to null
   *
   * @return boolean
   */
  @Transactional(rollbackFor = Exception.class)
  boolean updateNullPhoneEmail();

  /**
   * Save third-party user info
   *
   * @param sysUser
   */
  void saveThirdUser(SysUser sysUser);

  /**
   * Query by department ids
   *
   * @param departIds department id set
   * @param username user account name
   * @return
   */
  List<SysUser> queryByDepIds(List<String> departIds, String username);

  /**
   * Save user
   *
   * @param user user
   * @param selectedRoles selected role ids, comma-separated
   * @param selectedDeparts selected department ids, comma-separated
   * @param relTenantIds tenant ids
   */
  void saveUser(SysUser user, String selectedRoles, String selectedDeparts, String relTenantIds);

  /**
   * Edit user
   *
   * @param user user
   * @param roles selected role ids, comma-separated
   * @param departs selected department ids, comma-separated
   * @param relTenantIds tenant ids
   */
  void editUser(SysUser user, String roles, String departs, String relTenantIds);

  /**
   * Convert userId to username
   *
   * @param userIdList
   * @return List<String>
   */
  List<String> userIdToUsername(Collection<String> userIdList);

  /**
   * Get user info with encrypted fields [encrypted user info]
   *
   * @param username
   * @return
   */
  LoginUser getEncodeUserInfo(String username);

  /**
   * User resignation
   *
   * @param username
   */
  void userQuit(String username);

  /**
   * Get resigned user list
   *
   * @param tenantId tenant id
   * @return
   */
  List<SysUser> getQuitList(Integer tenantId);

  /**
   * Update delete status and resignation status
   *
   * @param userIds user id set
   * @param sysUser
   * @return boolean
   */
  void updateStatusAndFlag(List<String> userIds, SysUser sysUser);

  /**
   * Set login tenant
   *
   * @param sysUser
   * @return
   */
  Result<JSONObject> setLoginTenant(
      SysUser sysUser, JSONObject obj, String username, Result<JSONObject> result);

  // --- author:taoyan date:20221231 for: QQYUN-3515 org management under app; detail impl ---
  /**
   * Batch edit user info
   *
   * @param json
   */
  void batchEditUsers(JSONObject json);

  /**
   * Query users and departments by keyword
   *
   * @param keyword
   * @return
   */
  DepartAndUserInfo searchByKeyword(String keyword);

  /**
   * Query department change info
   *
   * @param departId
   * @return
   */
  UpdateDepartInfo getUpdateDepartInfo(String departId);

  /**
   * Update department-related info
   *
   * @param updateDepartInfo
   */
  void doUpdateDepartInfo(UpdateDepartInfo updateDepartInfo);

  /**
   * Set / unset department leaders
   *
   * @param json
   */
  void changeDepartChargePerson(JSONObject json);

  // --- author:taoyan date:20221231 for: QQYUN-3515 org management under app; detail impl ---

  /**
   * EditTenantUser
   *
   * @param sysUser
   * @param tenantId
   * @param departs
   */
  void editTenantUser(SysUser sysUser, String tenantId, String departs, String roles);

  /**
   * Update user account status
   *
   * @param id account id
   * @param status account status
   */
  void updateStatus(String id, String status);

  /**
   * Export users under app to Excel
   *
   * @param request
   * @return
   */
  ModelAndView exportAppUser(HttpServletRequest request);

  /**
   * Import users under app
   *
   * @param request
   * @return
   */
  Result<?> importAppUser(HttpServletRequest request);

  /**
   * Verify whether user is admin
   *
   * @param ids
   */
  void checkUserAdminRejectDel(String ids);

  /**
   * Update phone
   *
   * @param json
   * @param username
   */
  void changePhone(JSONObject json, String username);

  /**
   * Send SMS verification code
   *
   * @param jsonObject
   * @param username username
   * @param ipAddress IP address
   */
  void sendChangePhoneSms(JSONObject jsonObject, String username, String ipAddress);

  cn.hutool.json.JSONObject sumRegisterCounts();

  JSONArray getRegisterNumTrend(Integer lastNum, String dateType);

  // Get roles by userId from cache (method name carries cache name)
  List<SysRole> getRolesByUserId(String userId);

  // Refresh cache for getRolesByUserId
  void refreshRolesByUserId(String userId);

  @Transactional(rollbackFor = Exception.class)
  void updateAccountWithRoles(SysUser user);

  @Transactional(rollbackFor = Exception.class)
  java.util.Set<String> frozenBatchUpdateStatuses(String idsComma, String status);

  String getLoggedInUserMaskedEmail();

  void sendLoggedInUserTwoFactorEmailOtp();
}
