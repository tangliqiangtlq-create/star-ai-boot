package org.jeecg.modules.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.exceptions.ClientException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.util.RoleConfigUtil;
import org.jeecg.common.api.dto.message.MessageDTO;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.FillRuleConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.constant.enums.DySmsEnum;
import org.jeecg.common.constant.enums.SysAnnmentTypeEnum;
import org.jeecg.common.desensitization.annotation.SensitiveEncode;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.security.C3FieldReflectionInspector;
import org.jeecg.common.util.security.OpaqueMqCodec;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.system.vo.SysUserCacheInfo;
import org.jeecg.common.util.security.LedgerWriteGuard;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.DySmsHelper;
import org.jeecg.common.util.DySmsLimit;
import org.jeecg.common.util.FillRuleUtil;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.ImportExcelUtil;
import org.jeecg.common.util.LocaleResolver;
import org.jeecg.common.util.PasswordUtil;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.OTPHasher;
import org.jeecg.common.util.RequestUtils;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.UUIDGenerator;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.common.util.RocketMQUtil;
import org.jeecg.config.BusinessRuleConfig;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.base.service.IBusinessRuleService;
import org.jeecg.modules.message.handle.impl.SystemSendMsgHandle;
import org.jeecg.modules.system.entity.SysDepart;
import org.jeecg.modules.system.entity.SysPermission;
import org.jeecg.modules.system.entity.SysPosition;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysTenant;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserDepart;
import org.jeecg.modules.system.entity.SysUserPosition;
import org.jeecg.modules.system.entity.SysUserRole;
import org.jeecg.modules.system.entity.SysUserTenant;
import org.jeecg.modules.system.mapper.SysDepartMapper;
import org.jeecg.modules.system.mapper.SysPermissionMapper;
import org.jeecg.modules.system.mapper.SysPositionMapper;
import org.jeecg.modules.system.mapper.SysRoleMapper;
import org.jeecg.modules.system.mapper.SysTenantMapper;
import org.jeecg.modules.system.mapper.SysUserDepartMapper;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.jeecg.modules.system.mapper.SysUserSafeQuery;
import org.jeecg.modules.system.mapper.SysUserPositionMapper;
import org.jeecg.modules.system.mapper.SysUserRoleMapper;
import org.jeecg.modules.system.mapper.SysUserTenantMapper;
import org.jeecg.modules.system.model.SysUserSysDepartModel;
import org.jeecg.modules.system.service.ISysDictItemService;
import org.jeecg.modules.system.service.ISysDictService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.system.vo.SysUserDepVo;
import org.jeecg.modules.system.vo.SysUserPositionVo;
import org.jeecg.modules.system.vo.UserAvatar;
import org.jeecg.modules.system.vo.lowapp.AppExportUserVo;
import org.jeecg.modules.system.vo.lowapp.DepartAndUserInfo;
import org.jeecg.modules.system.vo.lowapp.DepartInfo;
import org.jeecg.modules.system.vo.lowapp.UpdateDepartInfo;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.MessageSource;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Lazy;
import org.jeecg.modules.system.shard.client.IDcsShardClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

/** User table service implementation @Author: scott @Date: 2018-12-20 */
@Service
@Slf4j
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
    implements ISysUserService {

  private static final Set<String> REQUIRED_FIELDS_FOR_USER_EDIT =
      Set.of("username", "realname", "avatar", "birthday", "sex", "email", "emailNotice", "phone", "status", "allowLogin", "workNo", "telephone", "activitiSync", "userIdentity", "departIds", "kycStatus", "kycAttachment1", "kycAttachment2", "kycAttachment3", "customerType", "walletAddress", "reason", "lastPasswordChangeTime", "financialState", "inviterId", "phoneCode", "idType", "newSubmit", "lastLoginTime", "updateBy", "updateTime");

  private static final Set<String> REQUIRED_FIELDS_FOR_WORKNO =
      Set.of("workNo", "updateBy", "updateTime");

  private static final Set<String> REQUIRED_FIELDS_FOR_DEPART_IDS =
      Set.of("userIdentity", "departIds", "updateBy", "updateTime");

  private static final String SHARD_SYNC_USER_CHANNEL = "shard-sync:sys-user-change";

  @Autowired
  private SysUserMapper userMapper;
  @Autowired
  private SysPermissionMapper sysPermissionMapper;
  @Autowired
  private SysUserRoleMapper sysUserRoleMapper;
  @Autowired
  private SysUserDepartMapper sysUserDepartMapper;
  @Autowired
  private SysDepartMapper sysDepartMapper;
  @Autowired
  private SysRoleMapper sysRoleMapper;
  @Resource
  private BaseCommonService baseCommonService;
  @Autowired
  private SysUserTenantMapper relationMapper;
  @Autowired
  private SysUserTenantMapper userTenantMapper;
  @Autowired
  private SysUserPositionMapper sysUserPositionMapper;
  @Autowired
  private SysPositionMapper sysPositionMapper;
  @Autowired
  private SystemSendMsgHandle systemSendMsgHandle;
  @Autowired
  private RedisUtil redisUtil;
  @Autowired
  private OTPHasher otpHasher;
  @Autowired
  private MessageSource messageSource;
  @Resource
  private IBusinessRuleService businessRuleService;
  @Lazy
  @Autowired
  private CommonAPI commonAPI;
  @Autowired
  private SysTenantMapper sysTenantMapper;
  @Autowired(required = false)
  private org.jeecg.common.system.passwordreset.PasswordResetLinkFacade passwordResetLinkFacade;
  @Autowired
  private SessionKickService sessionKickService;
  @Lazy
  @Autowired(required = false)
  private IDcsShardClient dcsApi;
  @Autowired
  private org.springframework.data.redis.core.RedisTemplate<String, Object> shardRedisTemplate;

  @Override
  public Result<IPage<SysUser>> queryPageList(
      HttpServletRequest req,
      QueryWrapper<SysUser> queryWrapper,
      Integer pageSize,
      Integer pageNo) {
    Result<IPage<SysUser>> result = new Result<IPage<SysUser>>();

    ISysUserService sysUserService = SpringContextUtils.getBean(ISysUserService.class);

    // user id
    String code = req.getParameter("code");

    // P1/P2 (CodePlan §4.1.2/§4.2.1): never SELECT * here — the response feeds the user
    // management page and C6 validateSysUserColumns rejects non-whitelisted columns for
    // authenticated non-admin callers. List C6-safe columns explicitly and append computed cols.
    List<String> columns = new ArrayList<>();
    Collections.addAll(columns, SysUserSafeQuery.COLUMNS);

    columns.add("0 AS beneficiary_count");
    columns.add("0 AS deposit_bank_count");

    String userId = StpUtil.getLoginIdAsString();

    if (oConvertUtils.isNotEmpty(code)) {
      queryWrapper.in("id", Arrays.asList(code.split(",")));
      pageSize = code.split(",").length;
    }
    String status = req.getParameter("status");
    if (oConvertUtils.isNotEmpty(status)) {
      queryWrapper.eq("status", Integer.parseInt(status));
    }

    queryWrapper.select(columns);

    // TODO external mock-login temp accounts hidden from list
    queryWrapper.ne("username", "_reserve_user_external");
    Page<SysUser> page = new Page<SysUser>(pageNo, pageSize);

    IPage<SysUser> pageList = this.page(page, queryWrapper);

    List<String> userIds = pageList.getRecords().stream().map(SysUser::getId).collect(Collectors.toList());

    Map<String, Integer> benCountMap = Collections.<String, Integer>emptyMap();
    Map<String, Integer> depCountMap = Collections.<String, Integer>emptyMap();
    if (!userIds.isEmpty() && dcsApi != null) {
      try {
        Result<Map<String, Integer>> br = dcsApi.countBeneficiaryByUserIds(userIds);
        if (br != null && br.isSuccess() && br.getResult() != null) {
          benCountMap = br.getResult();
        }
      } catch (Exception e) {
        log.warn("countBeneficiaryByUserIds remote failed", e);
      }
      try {
        Result<Map<String, Integer>> dr = dcsApi.countDepositBankByUserIds(userIds);
        if (dr != null && dr.isSuccess() && dr.getResult() != null) {
          depCountMap = dr.getResult();
        }
      } catch (Exception e) {
        log.warn("countDepositBankByUserIds remote failed", e);
      }
    }
    final Map<String, Integer> fBen = benCountMap;
    final Map<String, Integer> fDep = depCountMap;

    if (!userIds.isEmpty()) {

      pageList
        .getRecords()
        .forEach(
            item -> {
              item.setRoleList(sysUserService.getRolesByUserId(item.getId()));

              item.setBeneficiaryCount(fBen.getOrDefault(item.getId(), 0));
              item.setDepositBankCount(fDep.getOrDefault(item.getId(), 0));
            });
    }

    // P0: strip sensitive columns (password/salt/reason/walletAddress/financialState/
    // kycAttachment1-3/lastPasswordChangeTime/isSender) before returning to frontend.
    pageList.getRecords().forEach(SysUser::clearSensitiveForResponse);

    result.setSuccess(true);
    result.setResult(pageList);
    // log.info(pageList.toString());
    return result;
  }

  @SneakyThrows
  @Override
  @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, allEntries = true)
  public Result<?> resetPassword(
      String username, String oldpassword, String newpassword, String confirmpassword) {

    HttpServletRequest request = RequestUtils.getCurrentRequest();

    Locale locale = LocaleResolver.getCurrLocale();

    if (businessRuleService.isAccountLocked(username, request)) {

      log.info("User: {} is locked.", username);
      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    SysUser user = userMapper.getUserByName(username);
    if (!org.jeecg.common.util.CredentialGuard.isPresentLoginPassword(oldpassword)
        || !org.jeecg.common.util.CredentialGuard.hasStoredPasswordHash(user.getPassword())
        || !SmCryptoUtil.checkPassword(oldpassword, user.getPassword())) {

      businessRuleService.registerFailedAttempt(BusinessRuleConfig.PASSWORD, username, request);

      log.info("User: {} password is incorrect.", username);

      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }
    if (oldpassword.equals(newpassword)) {
      log.info("new password is same as old password.");
      return Result.error(
          messageSource.getMessage("resetPassword.sameAsOldPassword", null, locale));
    }
    if (oConvertUtils.isEmpty(newpassword)) {
      log.info("new password is null.");

      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }
    if (!newpassword.equals(confirmpassword)) {
      log.info("new password is not equal confirm password.");
      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    // B5-3: reject weak newpassword at reset entry (belt & suspenders; also checked in Controller)
    if (org.jeecg.common.util.CredentialGuard.isGenericWeakPassword(newpassword)) {
      log.warn("User: {} tried to set weak password on reset.", username);
      return Result.error(
          messageSource.getMessage("common.weak_password", null, "Password too weak.", locale));
    }

    String password = SmCryptoUtil.hashPassword(newpassword);
    this.userMapper.update(
        new SysUser().setPassword(password),
        new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, user.getId()));

    // Clear stale password-failure / captcha counters so next login is a clean first attempt
    businessRuleService.resetFailedAttempts(BusinessRuleConfig.PASSWORD, username, request);

    StpUtil.kickout(user.getId());

    return Result.ok(messageSource.getMessage("resetPassword.success", null, locale));
  }

  @Override
  @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, allEntries = true)
  public Result<?> changePassword(SysUser sysUser) {
    String password = sysUser.getPassword();
    String passwordEncode = SmCryptoUtil.hashPassword(password);
    sysUser.setPassword(passwordEncode);
    this.userMapper.updateById(sysUser);

    Locale locale = LocaleResolver.getCurrLocale();
    return Result.ok(messageSource.getMessage("sysUser.changePasswordSuccess", null, locale));
  }

  @Override
  @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, allEntries = true)
  @Transactional(rollbackFor = Exception.class)
  public boolean deleteUser(String userId) {
    this.checkUserAdminRejectDel(userId);
    SysUser ghost = new SysUser(); ghost.setId(userId);
    this.removeById(userId);
    this.publishShardSync(ghost, "DELETE");
    return false;
  }

  @Override
  @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, allEntries = true)
  @Transactional(rollbackFor = Exception.class)
  public boolean deleteBatchUsers(String userIds) {
    this.checkUserAdminRejectDel(userIds);
    List<String> idList = Arrays.asList(userIds.split(","));
    this.removeByIds(idList);
    for (String uid : idList) {
      SysUser ghost = new SysUser(); ghost.setId(uid);
      this.publishShardSync(ghost, "DELETE");
    }
    return false;
  }

  @Override
  public SysUser getUserByName(String username) {
    SysUser sysUser = userMapper.getUserByName(username);
    // Query user's tenant ids
    if (sysUser != null) {
      List<Integer> list = userTenantMapper.getTenantIdsByUserId(sysUser.getId());
      if (oConvertUtils.isNotEmpty(list)) {
        sysUser.setRelTenantIds(StringUtils.join(list.toArray(), SymbolConstant.COMMA));
      } else {
        sysUser.setRelTenantIds("");
      }
    }
    return sysUser;
  }

  @Override
  public SysUser getUserByNameForInfo(String username) {
    SysUser sysUser = userMapper.getUserByNameForInfo(username);
    // Query user's tenant ids
    if (sysUser != null) {
      List<Integer> list = userTenantMapper.getTenantIdsByUserId(sysUser.getId());
      if (oConvertUtils.isNotEmpty(list)) {
        sysUser.setRelTenantIds(StringUtils.join(list.toArray(), SymbolConstant.COMMA));
      } else {
        sysUser.setRelTenantIds("");
      }
    }
    return sysUser;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void addUserWithRole(SysUser user, String roles) {
    this.save(user);
    if (oConvertUtils.isNotEmpty(roles)) {
      String[] arr = roles.split(",");
      for (String roleId : arr) {
        SysUserRole userRole = new SysUserRole(user.getId(), roleId);
        sysUserRoleMapper.insert(userRole);
      }
    }
  }

  // C3-WHITELIST-CTRLLED: field-level only (not updateById whole entity)
  @Override
  @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, allEntries = true)
  @Transactional(rollbackFor = Exception.class)
  public void editUserWithRole(SysUser user, String roles) {
    LambdaUpdateWrapper<SysUser> uw1 = Wrappers.lambdaUpdate();
    uw1.eq(SysUser::getId, user.getId())
       .set(SysUser::getUsername, user.getUsername())
       .set(SysUser::getRealname, user.getRealname())
       .set(SysUser::getAvatar, user.getAvatar())
       .set(SysUser::getBirthday, user.getBirthday())
       .set(SysUser::getSex, user.getSex())
       .set(SysUser::getEmail, user.getEmail())
       .set(SysUser::getEmailNotice, user.getEmailNotice())
       .set(SysUser::getPhone, user.getPhone())
       .set(SysUser::getStatus, user.getStatus())
       .set(SysUser::getAllowLogin, user.getAllowLogin())
       .set(SysUser::getWorkNo, user.getWorkNo())
       .set(SysUser::getTelephone, user.getTelephone())
       .set(SysUser::getActivitiSync, user.getActivitiSync())
       .set(SysUser::getUserIdentity, user.getUserIdentity())
       .set(SysUser::getDepartIds, user.getDepartIds())
       .set(SysUser::getKycStatus, user.getKycStatus())
       .set(SysUser::getKycAttachment1, user.getKycAttachment1())
       .set(SysUser::getKycAttachment2, user.getKycAttachment2())
       .set(SysUser::getKycAttachment3, user.getKycAttachment3())
       .set(SysUser::getCustomerType, user.getCustomerType())
       .set(SysUser::getWalletAddress, user.getWalletAddress())
       .set(SysUser::getReason, user.getReason())
       .set(SysUser::getLastPasswordChangeTime, user.getLastPasswordChangeTime())
       .set(SysUser::getFinancialState, user.getFinancialState())
       .set(SysUser::getInviterId, user.getInviterId())
       .set(SysUser::getPhoneCode, user.getPhoneCode())
       .set(SysUser::getIdType, user.getIdType())
       .set(SysUser::getNewSubmit, user.getNewSubmit())
       .set(SysUser::getLastLoginTime, user.getLastLoginTime())
       .set(SysUser::getUpdateBy, StpUtil.getLoginIdAsString())
       .set(SysUser::getUpdateTime, new Date());
    Set<String> actual1 = C3FieldReflectionInspector.extractSetFieldNames(uw1);
    if (!actual1.containsAll(REQUIRED_FIELDS_FOR_USER_EDIT)) { Set<String> miss = new HashSet<>(REQUIRED_FIELDS_FOR_USER_EDIT); miss.removeAll(actual1); throw new JeecgBootException("C3 BLOCK MISSING_WRITE_FIELDS table=sys_user missing=" + miss); }
    // LedgerWriteGuard.assertLambdaUpdateWrapperFields("sys_user", uw1, "update_time", "update_by"); // table not whitelisted in LedgerWriteGuard
    baseMapper.update(null, uw1);
    // Delete then insert
    sysUserRoleMapper.delete(
        new QueryWrapper<SysUserRole>().lambda().eq(SysUserRole::getUserId, user.getId()));
    if (oConvertUtils.isNotEmpty(roles)) {
      String[] arr = roles.split(",");
      for (String roleId : arr) {
        SysUserRole userRole = new SysUserRole(user.getId(), roleId);
        sysUserRoleMapper.insert(userRole);
      }
    }
  }

  @Override
  public List<String> getRole(String username) {
    return sysUserRoleMapper.getRoleByUserName(username);
  }


  /**
   * Get user roles by username
   *
   * @param username username
   * @return role set
   */
  @Override
  public Set<String> getUserRolesSet(String username) {
    // Query roles owned by the user
    List<String> roles = sysUserRoleMapper.getRoleByUserName(username);
    log.info(
        "-------Fetch user role Rules from database------username: "
            + username
            + ",Roles size: "
            + (roles == null ? 0 : roles.size()));
    return new HashSet<>(roles);
  }

  /**
   * Get user roles by username
   *
   * @param userId user id
   * @return role set
   */
  @Override
  public Set<String> getUserRoleSetById(String userId) {
    // Query roles owned by the user
    List<String> roles = sysUserRoleMapper.getRoleCodeByUserId(userId);
    log.info(
        "-------Fetch user role Rules from database------userId: "
            + userId
            + ",Roles size: "
            + (roles == null ? 0 : roles.size()));
    return new HashSet<>(roles);
  }

  /**
   * Get user permissions by username
   *
   * @param userId user id
   * @return permission set
   */
  @Override
  public Set<String> getUserPermissionsSet(String userId) {
    Set<String> permissionSet = new HashSet<>();
    List<SysPermission> permissionList = sysPermissionMapper.queryByUser(userId);
    // ================= begin: when enabling tenant, default-add test role if missing================
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      if (permissionList == null) {
        permissionList = new ArrayList<>();
      }
      List<SysPermission> testRoleList = sysPermissionMapper.queryPermissionByTestRoleId();
      permissionList.addAll(testRoleList);
    }
    // ================= end: when enabling tenant, default-add test role if missing================
    for (SysPermission po : permissionList) {
      // TODO URL rule issue?
      // if (oConvertUtils.isNotEmpty(po.getUrl())) {
      // permissionSet.add(po.getUrl());
      // }
      if (oConvertUtils.isNotEmpty(po.getPerms())) {
        permissionSet.add(po.getPerms());
      }
    }
    log.info(
        "-------Fetch user permissions Perms from database------userId: "
            + userId
            + ",Perms size: "
            + (permissionSet == null ? 0 : permissionSet.size()));
    return permissionSet;
  }

  /**
   * Upgrade SpringBoot 2.6.6; circular deps disallowed
   *
   * @author:qinfeng
   * @update: 2022-04-07
   * @param username
   * @return
   */
  @Override
  public SysUserCacheInfo getCacheUser(String username) {
    SysUserCacheInfo info = new SysUserCacheInfo();
    info.setOneDepart(true);
    if (oConvertUtils.isEmpty(username)) {
      return null;
    }

    // QueryUserInfo
    SysUser sysUser = userMapper.getUserByName(username);
    if (sysUser != null) {
      info.setSysUserCode(sysUser.getUsername());
      info.setSysUserName(sysUser.getRealname());
      info.setSysOrgCode(sysUser.getOrgCode());
    }

    // Multi-department support in query
    List<SysDepart> list = sysDepartMapper.queryUserDeparts(sysUser.getId());
    List<String> sysMultiOrgCode = new ArrayList<String>();
    if (list == null || list.size() == 0) {
      // The current user has no department
      // sysMultiOrgCode.add("0");
    } else if (list.size() == 1) {
      sysMultiOrgCode.add(list.get(0).getOrgCode());
    } else {
      info.setOneDepart(false);
      for (SysDepart dpt : list) {
        sysMultiOrgCode.add(dpt.getOrgCode());
      }
    }
    info.setSysMultiOrgCode(sysMultiOrgCode);

    return info;
  }

  /**
   * Query based on department ID
   *
   * @param page
   * @param departId Department id
   * @param username user accountName
   * @return
   */
  @Override
  public IPage<SysUser> getUserByDepId(Page<SysUser> page, String departId, String username) {
    return userMapper.getUserByDepId(page, departId, username);
  }

  @Override
  public IPage<SysUser> getUserByDepIds(
      Page<SysUser> page, List<String> departIds, String username) {
    return userMapper.getUserByDepIds(page, departIds, username);
  }

  @Override
  public Map<String, String> getDepNamesByUserIds(List<String> userIds) {
    List<SysUserDepVo> list = this.baseMapper.getDepNamesByUserIds(userIds);

    Map<String, String> res = new HashMap(5);
    list.forEach(
        item -> {
          if (res.get(item.getUserId()) == null) {
            res.put(item.getUserId(), item.getDepartName());
          } else {
            res.put(item.getUserId(), res.get(item.getUserId()) + "," + item.getDepartName());
          }
        });
    return res;
  }

  // update-begin-author:taoyan date:2022-9-13 for: VUEN-2245 vulnerability pending 20220906
  // ----SQL injection
  // method unused; commented out
  /*
   * @Override
   * public IPage<SysUser> getUserByDepartIdAndQueryWrapper(Page<SysUser> page,
   * String departId, QueryWrapper<SysUser> queryWrapper) {
   * LambdaQueryWrapper<SysUser> lambdaQueryWrapper = queryWrapper.lambda();
   * 
   * lambdaQueryWrapper.eq(SysUser::getDelFlag, CommonConstant.DEL_FLAG_0);
   * lambdaQueryWrapper.inSql(SysUser::getId,
   * "SELECT user_id FROM sys_user_depart WHERE dep_id = '" + departId + "'");
   * 
   * return userMapper.selectPage(page, lambdaQueryWrapper);
   * }
   */
  // update-end-author:taoyan date:2022-9-13 for: VUEN-2245 vulnerability pending 20220906
  // ----SQL injection method unused; commented out

  @Override
  public IPage<SysUserSysDepartModel> queryUserByOrgCode(
      String orgCode, SysUser userParams, IPage page) {
    List<SysUserSysDepartModel> list = baseMapper.getUserByOrgCode(page, orgCode, userParams);
    // Query department by orgCode; pass position ids
    for (SysUserSysDepartModel model : list) {
      List<String> positionList = sysUserPositionMapper.getPositionIdByUserId(model.getId());
      model.setPost(CommonUtils.getSplitText(positionList, SymbolConstant.COMMA));
    }
    Integer total = baseMapper.getUserByOrgCodeTotal(orgCode, userParams);

    IPage<SysUserSysDepartModel> result = new Page<>(page.getCurrent(), page.getSize(), total);
    result.setRecords(list);

    return result;
  }

  /**
   * Query based on role ID
   *
   * @param page
   * @param roleId Role id
   * @param username user accountName
   * @return
   */
  @Override
  public IPage<SysUser> getUserByRoleId(Page<SysUser> page, String roleId, String username) {
    //update-begin---author:wangshuai ---date:20230220 for: [QQYUN-3980]org position feature
    // Add tenantId to position table
    // Add position-user relation table------------
    IPage<SysUser> userRoleList = userMapper.getUserByRoleId(page, roleId, username);
    List<SysUser> records = userRoleList.getRecords();
    if (null != records && records.size() > 0) {
      List<String> userIds = records.stream().map(SysUser::getId).collect(Collectors.toList());
      Map<String, String> useDepNames = this.getDepNamesByUserIds(userIds);
      for (SysUser sysUser : userRoleList.getRecords()) {
        // ConfigureDepartment
        sysUser.setOrgCodeTxt(useDepNames.get(sysUser.getId()));
        // Set user position ids
        this.userPositionId(sysUser);
      }
    }
    return userRoleList;
    //update-end---author:wangshuai ---date:20230220 for: [QQYUN-3980]org position feature
    // Add tenantId to position table
    // Add position-user relation table------------
  }

  @Override
  public IPage<SysUser> getUserByRoleIds(Page<SysUser> page, List<String> roleIds, String username) {
    IPage<SysUser> userRoleList = userMapper.getUserByRoleIds(page, roleIds, username);
    List<SysUser> records = userRoleList.getRecords();
    if (null != records && records.size() > 0) {
      List<String> userIds = records.stream().map(SysUser::getId).collect(Collectors.toList());
      Map<String, String> useDepNames = this.getDepNamesByUserIds(userIds);
      for (SysUser sysUser : userRoleList.getRecords()) {
        sysUser.setOrgCodeTxt(useDepNames.get(sysUser.getId()));
        this.userPositionId(sysUser);
      }
    }
    return userRoleList;
  }

  @Override
  public List<String> getUserIdsByRoleIds(List<String> roleIds) {
    return userMapper.getUserIdsByRoleIds(roleIds);
  }

  @Override
  @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, key = "#username")
  public void updateUserDepart(String username, String orgCode, Integer loginTenantId) {
    baseMapper.updateUserDepart(username, orgCode, loginTenantId);
  }

  @Override
  public SysUser getUserByPhone(String phone) {
    return userMapper.getUserByPhone(phone);
  }

  @Override
  public SysUser getUserByEmail(String email) {
    return userMapper.getUserByEmail(email);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void addUserWithDepart(SysUser user, String selectedParts) {
    // this.save(user); //already added once when saving role
    if (oConvertUtils.isNotEmpty(selectedParts)) {
      String[] arr = selectedParts.split(",");
      for (String deaprtId : arr) {
        SysUserDepart userDeaprt = new SysUserDepart(user.getId(), deaprtId);
        sysUserDepartMapper.insert(userDeaprt);
      }
    }
  }

  // C3-WHITELIST-CTRLLED: field-level only (not updateById whole entity)
  @Override
  @Transactional(rollbackFor = Exception.class)
  @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, allEntries = true)
  public void editUserWithDepart(SysUser user, String departs) {
    // Already updated once when updating role; may update again
    LambdaUpdateWrapper<SysUser> uw2 = Wrappers.lambdaUpdate();
    uw2.eq(SysUser::getId, user.getId())
       .set(SysUser::getUsername, user.getUsername())
       .set(SysUser::getRealname, user.getRealname())
       .set(SysUser::getAvatar, user.getAvatar())
       .set(SysUser::getBirthday, user.getBirthday())
       .set(SysUser::getSex, user.getSex())
       .set(SysUser::getEmail, user.getEmail())
       .set(SysUser::getEmailNotice, user.getEmailNotice())
       .set(SysUser::getPhone, user.getPhone())
       .set(SysUser::getStatus, user.getStatus())
       .set(SysUser::getAllowLogin, user.getAllowLogin())
       .set(SysUser::getWorkNo, user.getWorkNo())
       .set(SysUser::getTelephone, user.getTelephone())
       .set(SysUser::getActivitiSync, user.getActivitiSync())
       .set(SysUser::getUserIdentity, user.getUserIdentity())
       .set(SysUser::getDepartIds, user.getDepartIds())
       .set(SysUser::getKycStatus, user.getKycStatus())
       .set(SysUser::getKycAttachment1, user.getKycAttachment1())
       .set(SysUser::getKycAttachment2, user.getKycAttachment2())
       .set(SysUser::getKycAttachment3, user.getKycAttachment3())
       .set(SysUser::getCustomerType, user.getCustomerType())
       .set(SysUser::getWalletAddress, user.getWalletAddress())
       .set(SysUser::getReason, user.getReason())
       .set(SysUser::getLastPasswordChangeTime, user.getLastPasswordChangeTime())
       .set(SysUser::getFinancialState, user.getFinancialState())
       .set(SysUser::getInviterId, user.getInviterId())
       .set(SysUser::getPhoneCode, user.getPhoneCode())
       .set(SysUser::getIdType, user.getIdType())
       .set(SysUser::getNewSubmit, user.getNewSubmit())
       .set(SysUser::getLastLoginTime, user.getLastLoginTime())
       .set(SysUser::getUpdateBy, StpUtil.getLoginIdAsString())
       .set(SysUser::getUpdateTime, new Date());
    Set<String> actual2 = C3FieldReflectionInspector.extractSetFieldNames(uw2);
    if (!actual2.containsAll(REQUIRED_FIELDS_FOR_USER_EDIT)) { Set<String> miss = new HashSet<>(REQUIRED_FIELDS_FOR_USER_EDIT); miss.removeAll(actual2); throw new JeecgBootException("C3 BLOCK MISSING_WRITE_FIELDS table=sys_user missing=" + miss); }
    // LedgerWriteGuard.assertLambdaUpdateWrapperFields("sys_user", uw2, "update_time", "update_by"); // table not whitelisted in LedgerWriteGuard
    baseMapper.update(null, uw2);
    String[] arr = {};
    if (oConvertUtils.isNotEmpty(departs)) {
      arr = departs.split(",");
    }
    // Query linked departments
    List<SysUserDepart> userDepartList = sysUserDepartMapper.selectList(
        new QueryWrapper<SysUserDepart>().lambda().eq(SysUserDepart::getUserId, user.getId()));
    if (userDepartList != null && userDepartList.size() > 0) {
      for (SysUserDepart depart : userDepartList) {
        
      }
    }
    // Delete then insert
    sysUserDepartMapper.delete(
        new QueryWrapper<SysUserDepart>().lambda().eq(SysUserDepart::getUserId, user.getId()));
    if (oConvertUtils.isNotEmpty(departs)) {
      for (String departId : arr) {
        SysUserDepart userDepart = new SysUserDepart(user.getId(), departId);
        sysUserDepartMapper.insert(userDepart);
      }
    }
  }

  /**
   * Check whether user is valid
   *
   * @param sysUser
   * @return
   */
  @SneakyThrows
  @Override
  public Result<?> checkUserIsEffective(SysUser sysUser) {
    Result<?> result = new Result<Object>();

    Locale locale = LocaleResolver.getCurrLocale(); // Get current locale

    // Case 1: user not found
    if (sysUser == null) {
      String message = messageSource.getMessage("common.authentication_failed_msg", null, locale);
      result.error500(message);
      baseCommonService.addLog("UserLogin failed，User does not exist！", CommonConstant.LOG_TYPE_1, null);
      return result;
    }

    // Case 2: user already cancelled
    if (CommonConstant.DEL_FLAG_1.equals(sysUser.getDelFlag())) {
      String message = messageSource.getMessage("common.authentication_failed_msg", null, locale);
      baseCommonService.addLog(
          "User login failed, username:" + sysUser.getUsername() + "Deregistered!", CommonConstant.LOG_TYPE_1, null);
      result.error500(message);
      return result;
    }

    // Case 3: user is frozen
    if (CommonConstant.USER_FREEZE.equals(sysUser.getStatus())) {
      String message =
          messageSource.getMessage(
              "SysUserServiceImpl.checkUserIsEffective.userFrozen", null, locale);
      baseCommonService.addLog(
          "User login failed, username:" + sysUser.getUsername() + "Frozen!", CommonConstant.LOG_TYPE_1, null);
      result.error500(message);
      return result;
    }

    // Case 4: user not allowed to login
    if (sysUser.getAllowLogin() != null && sysUser.getAllowLogin() == 2) {
      String message = messageSource.getMessage("common.user_not_allowed_login", null, locale);
      baseCommonService.addLog(
          "User login failed, username:" + sysUser.getUsername() + "Login not allowed!", CommonConstant.LOG_TYPE_1, null);
      result.error500(message);
      return result;
    }

    return result;
  }

  @Override
  public List<SysUser> queryLogicDeleted() {
    //update-begin---author:wangshuai ---date:20221116 for: recycle-bin query non-resigned------------
    LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
    wrapper.ne(SysUser::getStatus, CommonConstant.USER_QUIT);
    return this.queryLogicDeleted(wrapper);
    //update-end---author:wangshuai ---date:20221116 for: recycle-bin query non-resigned--------------
  }

  @Override
  public List<SysUser> queryLogicDeleted(LambdaQueryWrapper<SysUser> wrapper) {
    if (wrapper == null) {
      wrapper = new LambdaQueryWrapper<>();
    }
    wrapper.eq(SysUser::getDelFlag, CommonConstant.DEL_FLAG_1);
    return userMapper.selectLogicDeleted(wrapper);
  }

  @Override
  @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, allEntries = true)
  public boolean revertLogicDeleted(List<String> userIds, SysUser updateEntity) {
    return userMapper.revertLogicDeleted(userIds, updateEntity) > 0;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeLogicDeleted(List<String> userIds) {
    // 1. Delete user
    int line = userMapper.deleteLogicDeleted(userIds);
    // 2. delete user-department relations
    line += sysUserDepartMapper.delete(
        new LambdaQueryWrapper<SysUserDepart>().in(SysUserDepart::getUserId, userIds));
    // 3. delete user-role relations
    line += sysUserRoleMapper.delete(
        new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
    // 4.also delete third-party app user
    try {
    // NOTE: cleaned - dingtalkService.removeThirdAppUser(userIds);
    // NOTE: cleaned - wechatEnterpriseService.removeThirdAppUser(userIds);
    } catch (Exception e) {
      log.error("Failed to synchronously delete third-party App user: ", e);
    }
    // 5. delete third-user table after step 4 which needs it

    // 6. delete tenant-user relation rows
    line += userTenantMapper.delete(
        new LambdaQueryWrapper<SysUserTenant>().in(SysUserTenant::getUserId, userIds));

    return line != 0;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean updateNullPhoneEmail() {
    userMapper.updateNullByEmptyString("email");
    userMapper.updateNullByEmptyString("phone");
    return true;
  }

  @Override
  public void saveThirdUser(SysUser sysUser) {
    // Save user
    String userid = UUIDGenerator.generate();
    sysUser.setId(userid);
    baseMapper.insert(sysUser);
    // Get third-party roles
    SysRole sysRole = sysRoleMapper.selectOne(
        new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "third_role"));
    // SaveUserRole
    SysUserRole userRole = new SysUserRole();
    userRole.setRoleId(sysRole.getId());
    userRole.setUserId(userid);
    sysUserRoleMapper.insert(userRole);
  }

  @Override
  public List<SysUser> queryByDepIds(List<String> departIds, String username) {
    return userMapper.queryByDepIds(departIds, username);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void saveUser(
      SysUser user, String selectedRoles, String selectedDeparts, String relTenantIds) {
    // step.1 Save user
    this.save(user);
    // Read front-end tenant ids on user save and add to tenant
    this.saveUserTenant(user.getId(), relTenantIds);
    // step.2 save roles
    if (oConvertUtils.isNotEmpty(selectedRoles)) {
      String[] arr = selectedRoles.split(",");
      for (String roleId : arr) {
        SysUserRole userRole = new SysUserRole(user.getId(), roleId);
        sysUserRoleMapper.insert(userRole);
      }
    }

    // step.3 save belonging departments
    if (oConvertUtils.isNotEmpty(selectedDeparts)) {
      String[] arr = selectedDeparts.split(",");
      for (String deaprtId : arr) {
        SysUserDepart userDeaprt = new SysUserDepart(user.getId(), deaprtId);
        sysUserDepartMapper.insert(userDeaprt);
      }
    }

    // step.4 save positions
    this.saveUserPosition(user.getId(), user.getPost());
    this.publishShardSync(user, "INSERT");
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  @Caching(evict = {
      @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, allEntries = true),
      @CacheEvict(value = "dcs_SysUserServiceImpl_getRolesByUserId", key = "#user.id")
  })
  // C3-WHITELIST-CTRLLED: field-level only (not updateById whole entity)
  public void editUser(SysUser user, String roles, String departs, String relTenantIds) {
    // Read front-end tenant ids on user edit
    this.editUserTenants(user.getId(), relTenantIds);
    // step.1 update user basic info
    LambdaUpdateWrapper<SysUser> uw3 = Wrappers.lambdaUpdate();
    uw3.eq(SysUser::getId, user.getId())
       .set(SysUser::getUsername, user.getUsername())
       .set(SysUser::getRealname, user.getRealname())
       .set(SysUser::getAvatar, user.getAvatar())
       .set(SysUser::getBirthday, user.getBirthday())
       .set(SysUser::getSex, user.getSex())
       .set(SysUser::getEmail, user.getEmail())
       .set(SysUser::getEmailNotice, user.getEmailNotice())
       .set(SysUser::getPhone, user.getPhone())
       .set(SysUser::getStatus, user.getStatus())
       .set(SysUser::getAllowLogin, user.getAllowLogin())
       .set(SysUser::getWorkNo, user.getWorkNo())
       .set(SysUser::getTelephone, user.getTelephone())
       .set(SysUser::getActivitiSync, user.getActivitiSync())
       .set(SysUser::getUserIdentity, user.getUserIdentity())
       .set(SysUser::getDepartIds, user.getDepartIds())
       .set(SysUser::getKycStatus, user.getKycStatus())
       .set(SysUser::getKycAttachment1, user.getKycAttachment1())
       .set(SysUser::getKycAttachment2, user.getKycAttachment2())
       .set(SysUser::getKycAttachment3, user.getKycAttachment3())
       .set(SysUser::getCustomerType, user.getCustomerType())
       .set(SysUser::getWalletAddress, user.getWalletAddress())
       .set(SysUser::getReason, user.getReason())
       .set(SysUser::getLastPasswordChangeTime, user.getLastPasswordChangeTime())
       .set(SysUser::getFinancialState, user.getFinancialState())
       .set(SysUser::getInviterId, user.getInviterId())
       .set(SysUser::getPhoneCode, user.getPhoneCode())
       .set(SysUser::getIdType, user.getIdType())
       .set(SysUser::getNewSubmit, user.getNewSubmit())
       .set(SysUser::getLastLoginTime, user.getLastLoginTime())
       .set(SysUser::getUpdateBy, StpUtil.getLoginIdAsString())
       .set(SysUser::getUpdateTime, new Date());
    Set<String> actual3 = C3FieldReflectionInspector.extractSetFieldNames(uw3);
    if (!actual3.containsAll(REQUIRED_FIELDS_FOR_USER_EDIT)) { Set<String> miss = new HashSet<>(REQUIRED_FIELDS_FOR_USER_EDIT); miss.removeAll(actual3); throw new JeecgBootException("C3 BLOCK MISSING_WRITE_FIELDS table=sys_user missing=" + miss); }
    // LedgerWriteGuard.assertLambdaUpdateWrapperFields("sys_user", uw3, "update_time", "update_by"); // table not whitelisted in LedgerWriteGuard
    baseMapper.update(null, uw3);
    // step.2 update roles
    // Handle user roles: delete then insert
    sysUserRoleMapper.delete(
        new QueryWrapper<SysUserRole>().lambda().eq(SysUserRole::getUserId, user.getId()));
    if (oConvertUtils.isNotEmpty(roles)) {
      String[] arr = roles.split(",");
      for (String roleId : arr) {
        SysUserRole userRole = new SysUserRole(user.getId(), roleId);
        sysUserRoleMapper.insert(userRole);
      }
    }

    // step.3 update departments
    String[] arr = {};
    if (oConvertUtils.isNotEmpty(departs)) {
      arr = departs.split(",");
    }
    // Query linked departments
    List<SysUserDepart> userDepartList = sysUserDepartMapper.selectList(
        new QueryWrapper<SysUserDepart>().lambda().eq(SysUserDepart::getUserId, user.getId()));
    if (userDepartList != null && userDepartList.size() > 0) {
      for (SysUserDepart depart : userDepartList) {
        
      }
    }
    // Delete then insert
    sysUserDepartMapper.delete(
        new QueryWrapper<SysUserDepart>().lambda().eq(SysUserDepart::getUserId, user.getId()));
    if (oConvertUtils.isNotEmpty(departs)) {
      for (String departId : arr) {
        SysUserDepart userDepart = new SysUserDepart(user.getId(), departId);
        sysUserDepartMapper.insert(userDepart);
      }
    }
    // step.4 update phone and email
    // Normalize empty phone/email strings to null
    userMapper.updateNullByEmptyString("email");
    userMapper.updateNullByEmptyString("phone");

    // step.5 update positions
    this.editUserPosition(user.getId(), user.getPost());

    this.publishShardSync(user, "UPDATE");
  }

  @Override
  public List<String> userIdToUsername(Collection<String> userIdList) {
    LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.in(SysUser::getId, userIdList);
    List<SysUser> userList = super.list(queryWrapper);
    return userList.stream().map(SysUser::getUsername).collect(Collectors.toList());
  }

  @Override
  @Cacheable(cacheNames = CacheConstant.SYS_USERS_CACHE, key = "#username")
  @SensitiveEncode
  public LoginUser getEncodeUserInfo(String username) {
    if (oConvertUtils.isEmpty(username)) {
      return null;
    }
    LoginUser loginUser = new LoginUser();
    // L27-3: this method runs on the POST-login path (StpInterfaceImpl.isDisabled via
    // CommonAPI.getUserByName, and CommonController file-permission token verification).
    // It must NOT use getUserByName (SELECT *) — C6 (SensitiveTableBlacklistInterceptor.
    // validateSysUserColumns) blocks SELECT * / non-whitelisted columns for logged-in
    // non-owner-admin sessions, which silently 403'd /api/sys/common/file. Use the explicit
    // C6-safe projection instead; credentials are not needed here (LoginUser.password/salt are
    // WRITE_ONLY and dcs verifies via /sys/api/verifyPassword).
    SysUser sysUser = userMapper.getUserByNameForInfo(username);
    // Query user's tenant ids
    this.setUserTenantIds(sysUser);
    // Set position id
    this.userPositionId(sysUser);
    if (sysUser == null) {
      return null;
    }
    BeanUtils.copyProperties(sysUser, loginUser);
    // Query current login user's department id
    loginUser.setOrgId(this.getDepartIdByOrCode(sysUser.getOrgCode()));
    // Query current login user's role codes (comma-separated)
    loginUser.setRoleCode(this.getJoinRoleCodeByUserId(sysUser.getId()));
    return loginUser;
  }

  @Override
  @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, allEntries = true)
  @Transactional(rollbackFor = Exception.class)
  public void userQuit(String username) {
    SysUser sysUser = userMapper.getUserByName(username);
    if (null == sysUser) {
      throw new JeecgBootException("Resignation failed, user no longer exists");
    }
    // update-begin---author:wangshuai ---date:20230111
    //for: [QQYUN-3951]tenant user resignation refactor------------
    int tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
    // Update user-tenant status to resigned
    if (tenantId == 0) {
      throw new JeecgBootException("Resignation failed, tenant does not exist");
    }
    LambdaQueryWrapper<SysUserTenant> query = new LambdaQueryWrapper<>();
    query.eq(SysUserTenant::getUserId, sysUser.getId());
    query.eq(SysUserTenant::getTenantId, tenantId);
    SysUserTenant userTenant = new SysUserTenant();
    userTenant.setStatus(CommonConstant.USER_TENANT_QUIT);
    userTenantMapper.update(userTenant, query);
  }

  @Override
  public List<SysUser> getQuitList(Integer tenantId) {
    return userMapper.getTenantQuitList(tenantId);
  }

  @Override
  public void updateStatusAndFlag(List<String> userIds, SysUser sysUser) {
    userMapper.updateStatusAndFlag(userIds, sysUser);
  }

  /**
   * Set login tenant
   *
   * @param sysUser
   * @return
   */
  @Override
  public Result<JSONObject> setLoginTenant(
      SysUser sysUser, JSONObject obj, String username, Result<JSONObject> result) {
    //update-begin--Author:sunjianlei Date:20210802 for: get user tenant info
    // Which tenants the user has
    // List<SysTenant> tenantList = null;
    // update-begin---author:wangshuai ---date:20221223
    //for: [QQYUN-3371]tenant logic refactor to relation table------------
    // update-begin---author:wangshuai ---date:20230427
    //for: [QQYUN-5270]after leaving all tenants, re-login shows tenant frozen------------
    List<SysTenant> tenantList = relationMapper.getTenantNoCancel(sysUser.getId());
    obj.put("tenantList", tenantList);
    // update-end---author:wangshuai ---date:20230427
    //for: [QQYUN-5270]after leaving all tenants, re-login shows tenant frozen------------
    // if (null!=tenantIdList && tenantIdList.size()>0) {
    // //update-end---author:wangshuai ---date:20221223
    //for: [QQYUN-3371]tenant logic refactor to relation table--------------
    // //-------------------------------------------------------------------------------------
    // Query active tenant set
    // LambdaQueryWrapper<SysTenant> queryWrapper = new LambdaQueryWrapper<>();
    // queryWrapper.in(SysTenant::getId, tenantIdList);
    // queryWrapper.eq(SysTenant::getStatus,
    // Integer.valueOf(CommonConstant.STATUS_1));
    // tenantList = sysTenantMapper.selectList(queryWrapper);
    // //-------------------------------------------------------------------------------------
    //
    // if (tenantList.size() == 0) {
    // return result.error500("All tenants linked to this user are frozen; login denied!");
    // } else {
    // obj.put("tenantList", tenantList);
    // }
    // }
    // update-end---author:wangshuai ---date:20221223
    //for: [QQYUN-3371]tenant logic refactor to relation table--------------
    //update-end--Author:sunjianlei Date:20210802 for: get user tenant info

    // Login session tenant id; validity reset
    if (tenantList != null && tenantList.size() > 0) {
      if (tenantList.size() == 1) {
        sysUser.setLoginTenantId(tenantList.get(0).getId());
      } else {
        List<SysTenant> listAfterFilter = tenantList.stream()
            .filter(s -> s.getId().equals(sysUser.getLoginTenantId()))
            .collect(Collectors.toList());
        if (listAfterFilter == null || listAfterFilter.size() == 0) {
          // If last login tenant id is no longer owned, pick user's first tenant id
          sysUser.setLoginTenantId(tenantList.get(0).getId());
        }
      }
    } else {
      // When no tenant, set to 0
      sysUser.setLoginTenantId(0);
    }
    // Set user login cached tenant
    this.updateUserDepart(username, null, sysUser.getLoginTenantId());
    log.info(" Login API user tenant ID = {}", sysUser.getLoginTenantId());
    if (sysUser.getLoginTenantId() != null) {
      // On login, manually set session tenant id or tenant-isolated login query fails
      TenantContext.setTenant(sysUser.getLoginTenantId() + "");
    }
    return null;
  }

  /**
   * Get tenant id
   *
   * @param sysUser
   */
  private void setUserTenantIds(SysUser sysUser) {
    if (ObjectUtils.isNotEmpty(sysUser)) {
      List<Integer> list = relationMapper.getTenantIdsNoStatus(sysUser.getId());
      if (null != list && list.size() > 0) {
        sysUser.setRelTenantIds(StringUtils.join(list.toArray(), ","));
      } else {
        sysUser.setRelTenantIds("");
      }
    }
  }

  /**
   * SaveTenant
   *
   * @param userId
   * @param relTenantIds
   */
  private void saveUserTenant(String userId, String relTenantIds) {
    if (oConvertUtils.isNotEmpty(relTenantIds)) {
      String[] tenantIds = relTenantIds.split(SymbolConstant.COMMA);
      for (String tenantId : tenantIds) {
        SysUserTenant relation = new SysUserTenant();
        relation.setUserId(userId);
        relation.setTenantId(Integer.valueOf(tenantId));
        relation.setStatus(CommonConstant.STATUS_1);

        LambdaQueryWrapper sysUserTenantQueryWrapper = new LambdaQueryWrapper<SysUserTenant>()
            .eq(SysUserTenant::getUserId, userId)
            .eq(SysUserTenant::getTenantId, Integer.valueOf(tenantId));
        SysUserTenant tenantPresent = relationMapper.selectOne(sysUserTenantQueryWrapper);
        if (tenantPresent != null) {
          tenantPresent.setStatus(CommonConstant.STATUS_1);
          relationMapper.updateById(tenantPresent);
        } else {
          relationMapper.insert(relation);
        }
      }
    } else {
      // Whether to enable multi-tenant data isolation for the system module [SAAS multi-tenant mode]
      if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
        // update-begin---author:wangshuai ---date:20230220
        //for: if current user not in tenant, insert------------
        String tenantId = TenantContext.getTenant();
        if (oConvertUtils.isNotEmpty(tenantId)) {
          Integer count = relationMapper.userTenantIzExist(userId, Integer.parseInt(tenantId));
          if (count == 0) {
            SysUserTenant relation = new SysUserTenant();
            relation.setUserId(userId);
            relation.setTenantId(Integer.parseInt(tenantId));
            relation.setStatus(CommonConstant.STATUS_1);
            relationMapper.insert(relation);
          }
        }
        // update-end---author:wangshuai ---date:20230220
        //for: if current user not in tenant, insert------------
      }
    }
  }

  /**
   * EditTenant
   *
   * @param userId
   * @param relTenantIds
   */
  private void editUserTenants(String userId, String relTenantIds) {
    LambdaQueryWrapper<SysUserTenant> query = new LambdaQueryWrapper<>();
    query.eq(SysUserTenant::getUserId, userId);
    // Tenant ids in DB
    List<Integer> oldTenantIds = relationMapper.getTenantIdsByUserId(userId);
    // If incoming tenant ids empty, delete tenants
    if (oConvertUtils.isEmpty(relTenantIds) && CollectionUtils.isNotEmpty(oldTenantIds)) {
      this.deleteTenantByUserId(userId, null);
    } else if (oConvertUtils.isNotEmpty(relTenantIds) && CollectionUtils.isEmpty(oldTenantIds)) {
      // If incoming tenant ids non-empty but DB empty, insert
      this.saveUserTenant(userId, relTenantIds);
    } else {
      // Both non-empty; compare then add/remove
      if (oConvertUtils.isNotEmpty(relTenantIds) && CollectionUtils.isNotEmpty(oldTenantIds)) {
        // Diff new vs old tenant ids; delete extras
        String[] relTenantIdArray = relTenantIds.split(SymbolConstant.COMMA);
        List<String> relTenantIdList = Arrays.asList(relTenantIdArray);

        List<Integer> deleteTenantIdList = oldTenantIds.stream()
            .filter(item -> !relTenantIdList.contains(item.toString()))
            .collect(Collectors.toList());
        for (Integer tenantId : deleteTenantIdList) {
          this.deleteTenantByUserId(userId, tenantId);
        }
        // Diff old vs new tenant user ids; insert missing
        String tenantIds = relTenantIdList.stream()
            .filter(item -> !oldTenantIds.contains(Integer.valueOf(item)))
            .collect(Collectors.joining(","));
        this.saveUserTenant(userId, tenantIds);
      }
    }
  }

  /**
   * DeleteTenantApproveUserid
   *
   * @param tenantId
   * @param userId
   */
  private void deleteTenantByUserId(String userId, Integer tenantId) {
    LambdaQueryWrapper<SysUserTenant> query = new LambdaQueryWrapper<>();
    query.eq(SysUserTenant::getUserId, userId);
    if (oConvertUtils.isNotEmpty(tenantId)) {
      query.eq(SysUserTenant::getTenantId, tenantId);
    }
    relationMapper.delete(query);
  }

  @Override
  public void batchEditUsers(JSONObject json) {
    String userIds = json.getString("userIds");
    List<String> idList = JSONArray.parseArray(userIds, String.class);
    // Department
    String selecteddeparts = json.getString("selecteddeparts");
    // Position
    String post = json.getString("post");
    // Workplace? Field not present
    String workAddress = json.getString("workAddress");
    // Batch update user positions
    if (oConvertUtils.isNotEmpty(post)) {
      //update-begin---author:wangshuai ---date:20230220 for: [QQYUN-3980]org position feature
      // Add tenantId to position table
      // Add position-user relation table------------
      // Update position-user relation table
      for (String userId : idList) {
        this.editUserPosition(userId, post);
      }
      //update-end---author:wangshuai ---date:20230220 for: [QQYUN-3980]org position feature
      // Add tenantId to position table
      // Add position-user relation table------------
    }
    if (oConvertUtils.isNotEmpty(selecteddeparts)) {
      // Query department list for current tenant
      Integer currentTenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
      LambdaQueryWrapper<SysDepart> departQuery = new LambdaQueryWrapper<SysDepart>().eq(SysDepart::getTenantId,
          currentTenantId);
      List<SysDepart> departList = sysDepartMapper.selectList(departQuery);
      if (departList == null || departList.size() == 0) {
        log.error("batchEditUsers: no department found for tenant ID>" + currentTenantId);
        return;
      }
      List<String> departIdList = new ArrayList<String>();
      for (SysDepart depart : departList) {
        if (depart != null) {
          String id = depart.getId();
          if (oConvertUtils.isNotEmpty(id)) {
            departIdList.add(id);
          }
        }
      }
      // Delete person-department links
      LambdaQueryWrapper<SysUserDepart> query = new LambdaQueryWrapper<SysUserDepart>()
          .in(SysUserDepart::getUserId, idList)
          .in(SysUserDepart::getDepId, departIdList);
      sysUserDepartMapper.delete(query);

      String[] arr = selecteddeparts.split(",");

      // Then insert
      for (String deaprtId : arr) {
        for (String userId : idList) {
          SysUserDepart userDepart = new SysUserDepart(userId, deaprtId);
          sysUserDepartMapper.insert(userDepart);
        }
      }
    }
  }

  @Override
  public DepartAndUserInfo searchByKeyword(String keyword) {
    DepartAndUserInfo departAndUserInfo = new DepartAndUserInfo();
    if (oConvertUtils.isNotEmpty(keyword)) {
      LambdaQueryWrapper<SysUser> query1 = new LambdaQueryWrapper<SysUser>().like(SysUser::getRealname, keyword);
      String str = oConvertUtils.getString(TenantContext.getTenant(), "0");
      Integer tenantId = Integer.valueOf(str);
      if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
        List<String> userIds = userTenantMapper.getUserIdsByTenantId(tenantId);
        if (oConvertUtils.listIsNotEmpty(userIds)) {
          query1.in(SysUser::getId, userIds);
        } else {
          query1.eq(SysUser::getId, "");
        }
      }
      List<SysUser> list1 = this.baseMapper.selectList(query1);
      if (list1 != null && list1.size() > 0) {
        List<UserAvatar> userList = list1.stream().map(v -> new UserAvatar(v)).collect(Collectors.toList());
        departAndUserInfo.setUserList(userList);
      }

      LambdaQueryWrapper<SysDepart> query2 = new LambdaQueryWrapper<SysDepart>().like(SysDepart::getDepartName,
          keyword);
      if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
        query2.eq(SysDepart::getTenantId, tenantId);
      }
      List<SysDepart> list2 = sysDepartMapper.selectList(query2);
      if (list2 != null && list2.size() > 0) {
        List<DepartInfo> departList = new ArrayList<>();
        for (SysDepart depart : list2) {
          List<String> orgName = new ArrayList<>();
          List<String> orgId = new ArrayList<>();
          getParentDepart(depart, orgName, orgId);
          DepartInfo departInfo = new DepartInfo();
          departInfo.setId(depart.getId());
          departInfo.setOrgId(orgId);
          departInfo.setOrgName(orgName);
          departList.add(departInfo);
        }
        departAndUserInfo.setDepartList(departList);
      }
    }
    return departAndUserInfo;
  }

  @Override
  public UpdateDepartInfo getUpdateDepartInfo(String departId) {
    SysDepart depart = sysDepartMapper.selectById(departId);
    if (depart != null) {
      UpdateDepartInfo info = new UpdateDepartInfo(depart);
      List<SysDepart> subList = sysDepartMapper.queryDeptByPid(departId);
      if (subList != null && subList.size() > 0) {
        info.setHasSub(true);
      }
      // Get department leader info
      LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
          .eq(SysUser::getUserIdentity, 2)
          .like(SysUser::getDepartIds, depart.getId());
      List<SysUser> userList = this.baseMapper.selectList(query);
      if (userList != null && userList.size() > 0) {
        List<String> idList = userList.stream().map(i -> i.getId()).collect(Collectors.toList());
        info.setChargePersonList(idList);
      }
      return info;
    }
    return null;
  }

  @Override
  public void doUpdateDepartInfo(UpdateDepartInfo info) {
    String departId = info.getDepartId();
    SysDepart depart = sysDepartMapper.selectById(departId);
    if (depart != null) {
      // Update department info - parent and name
      if (!depart.getParentId().equals(info.getParentId())) {
        String pid = info.getParentId();
        SysDepart parentDepart = sysDepartMapper.selectById(pid);
        if (parentDepart != null) {
          String orgCode = getNextOrgCode(pid);
          depart.setOrgCode(orgCode);
          depart.setParentId(pid);
        }
      }
      depart.setDepartName(info.getDepartName());
      sysDepartMapper.updateById(depart);
      // Query this department's leaders first
      List<SysUser> departChargeUsers = queryDepartChargePersons(departId);
      List<String> departChargeUserIdList = departChargeUsers.stream().map(i -> i.getId()).collect(Collectors.toList());
      // Update department leaders
      List<String> userIdList = info.getChargePersonList();
      if (userIdList != null && userIdList.size() > 0) {
        for (String userId : userIdList) {
          // P2 (CodePlan §4.3.4): only whitelisted columns are needed (identity/departIds);
          // the following updateById only writes identity/depart_ids, so sensitive columns
          // must not be materialized here.
          SysUser user = this.baseMapper.selectInfoById(userId);
          if (user != null) {
            departChargeUserIdList.remove(user.getId());
            user.setUserIdentity(2);
            String departIds = user.getDepartIds();
            if (oConvertUtils.isEmpty(departIds)) {
              user.setDepartIds(departId);
            } else {
              List<String> list = new ArrayList<String>(Arrays.asList(departIds.split(",")));
              if (list.indexOf(departId) >= 0) {
                continue;
              } else {
                list.add(departId);
                String newDepartIds = String.join(",", list);
                user.setDepartIds(newDepartIds);
              }
            }
            this.baseMapper.updateById(user);
          }
        }
        //update-begin---author:wangshuai ---date:20230303 for: department leader cannot be deleted------------
        this.removeDepartmentManager(departChargeUserIdList, departChargeUsers, departId);
      } else {
        if (CollectionUtil.isNotEmpty(departChargeUsers)) {
          // Empty front-end user ids means clear all department leaders in DB
          this.removeDepartmentManager(departChargeUserIdList, departChargeUsers, departId);
        }
        //update-end---author:wangshuai ---date:20230303 for: department leader cannot be deleted------------

      }
    }
  }

  private List<SysUser> queryDepartChargePersons(String departId) {
    List<SysUser> result = new ArrayList<>();
    //update-begin---author:wangshuai ---date:20230303 for: department leader cannot be deleted------------
    LambdaQueryWrapper<SysUser> userQuery = new LambdaQueryWrapper<>();
    // P2 (CodePlan §4.3.4): only id/user_identity/depart_ids are read below — all whitelisted.
    userQuery
        .select(SysUser::getId, SysUser::getUserIdentity, SysUser::getDepartIds)
        .like(SysUser::getDepartIds, departId);
    List<SysUser> userList = userMapper.selectList(userQuery);
    if (userList != null && userList.size() > 0) {
      for (SysUser user : userList) {
        Integer identity = user.getUserIdentity();
        String deps = user.getDepartIds();
        if (identity != null && identity == 2) {
          if (oConvertUtils.isNotEmpty(deps)) {
            if (deps.indexOf(departId) >= 0) {
              result.add(user);
            }
            //update-end---author:wangshuai ---date:20230303 for: department leader cannot be deleted------------
          }
        }
      }
    }
    return result;
  }

  /**
   * Change parent department and update code
   *
   * @param parentId
   * @return
   */
  private String getNextOrgCode(String parentId) {
    JSONObject formData = new JSONObject();
    formData.put("parentId", parentId);
    String[] codeArray = (String[]) FillRuleUtil.executeRule(FillRuleConstant.DEPART, formData);
    return codeArray[0];
  }

  // C3-WHITELIST-CTRLLED: field-level only (not updateById whole entity)
  @Override
  public void changeDepartChargePerson(JSONObject json) {
    String userId = json.getString("userId");
    String departId = json.getString("departId");
    boolean status = json.getBoolean("status");
    SysUser user = this.getById(userId);
    if (user != null) {
      String ids = user.getDepartIds();
      Integer finalUserIdentity = user.getUserIdentity();
      String finalDepartIds = ids;
      if (status == true) {
        // Set department leaders
        if (oConvertUtils.isEmpty(ids)) {
          // Set as parent
          finalUserIdentity = CommonConstant.USER_IDENTITY_2;
          finalDepartIds = departId;
        } else {
          List<String> list = new ArrayList<String>(Arrays.asList(ids.split(",")));
          if (list.indexOf(departId) >= 0) {
            // No-op
          } else {
            list.add(departId);
            String newIds = String.join(",", list);
            // Set as parent
            finalUserIdentity = CommonConstant.USER_IDENTITY_2;
            finalDepartIds = newIds;
          }
        }
      } else {
        // Remove leaders
        if (oConvertUtils.isNotEmpty(ids)) {
          List<String> list = new ArrayList<String>();
          for (String temp : ids.split(",")) {
            if (oConvertUtils.isEmpty(temp)) {
              continue;
            }
            if (!temp.equals(departId)) {
              list.add(temp);
            }
          }
          String newIds = "";
          if (list.size() > 0) {
            newIds = String.join(",", list);
          } else {
            // Empty responsible depts means ordinary user
            finalUserIdentity = CommonConstant.USER_IDENTITY_1;
          }
          finalDepartIds = newIds;
        }
      }
      LambdaUpdateWrapper<SysUser> uw4 = Wrappers.lambdaUpdate();
      uw4.eq(SysUser::getId, userId)
         .set(SysUser::getUserIdentity, finalUserIdentity)
         .set(SysUser::getDepartIds, finalDepartIds)
         .set(SysUser::getUpdateBy, StpUtil.getLoginIdAsString())
         .set(SysUser::getUpdateTime, new Date());
      Set<String> actual4 = C3FieldReflectionInspector.extractSetFieldNames(uw4);
      if (!actual4.containsAll(REQUIRED_FIELDS_FOR_DEPART_IDS)) { Set<String> miss = new HashSet<>(REQUIRED_FIELDS_FOR_DEPART_IDS); miss.removeAll(actual4); throw new JeecgBootException("C3 BLOCK MISSING_WRITE_FIELDS table=sys_user missing=" + miss); }
      // LedgerWriteGuard.assertLambdaUpdateWrapperFields("sys_user", uw4, "update_time", "update_by"); // table not whitelisted in LedgerWriteGuard
      baseMapper.update(null, uw4);
    }
  }

  /**
   * Find parent department
   *
   * @param depart
   * @param orgName
   * @param orgId
   */
  private void getParentDepart(SysDepart depart, List<String> orgName, List<String> orgId) {
    String pid = depart.getParentId();
    orgName.add(0, depart.getDepartName());
    orgId.add(0, depart.getId());
    if (oConvertUtils.isNotEmpty(pid)) {
      SysDepart temp = sysDepartMapper.selectById(pid);
      getParentDepart(temp, orgName, orgId);
    }
  }

  // C3-WHITELIST-CTRLLED: field-level only (not updateById whole entity)
  @Override
  @Transactional(rollbackFor = Exception.class)
  @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, allEntries = true)
  public void editTenantUser(SysUser sysUser, String tenantId, String departs, String roles) {
    String finalWorkNo = sysUser.getWorkNo();
    String id = sysUser.getId();
    LambdaUpdateWrapper<SysUser> uw5 = Wrappers.lambdaUpdate();
    uw5.eq(SysUser::getId, id)
       .set(SysUser::getWorkNo, finalWorkNo)
       .set(SysUser::getUpdateBy, StpUtil.getLoginIdAsString())
       .set(SysUser::getUpdateTime, new Date());
    Set<String> actual5 = C3FieldReflectionInspector.extractSetFieldNames(uw5);
    if (!actual5.containsAll(REQUIRED_FIELDS_FOR_WORKNO)) { Set<String> miss = new HashSet<>(REQUIRED_FIELDS_FOR_WORKNO); miss.removeAll(actual5); throw new JeecgBootException("C3 BLOCK MISSING_WRITE_FIELDS table=sys_user missing=" + miss); }
    // LedgerWriteGuard.assertLambdaUpdateWrapperFields("sys_user", uw5, "update_time", "update_by"); // table not whitelisted in LedgerWriteGuard
    baseMapper.update(null, uw5);
    // update-begin---author:wangshuai ---date:20230424
    // for: [QQYUN-5251] people & dept: department cannot be deleted ------------
    if (oConvertUtils.isEmpty(departs)) {
      // Directly delete tenant departments under user
      sysUserDepartMapper.deleteUserDepart(sysUser.getId(), tenantId);
    } else {
      // Update departments under tenant user
      this.updateTenantDepart(sysUser, tenantId, departs);
    }
    // update-end---author:wangshuai ---date:20230424
    // for: [QQYUN-5251] people & dept: department cannot be deleted ------------
    // Update positions under user
    this.editUserPosition(sysUser.getId(), sysUser.getPost());
  }

  /**
   * Update account status
   *
   * @param id account id
   * @param status account status
   */
  @Override
  @CacheEvict(value = { CacheConstant.SYS_USERS_CACHE }, allEntries = true)
  public void updateStatus(String id, String status) {

    String text = commonAPI.translateDict("user_status", status);

    if (StrUtil.isBlank(text)) {
      log.error("Dictionary translation failed, dictionary name: user_status, dictionary value: {}", status);
      throw new JeecgBootException(I18nUtil.getMessage("sysUser.invalidUserStatus"));
    }

    Set<String> roles = getUserRoleSetById(id);

    if (RoleConfigUtil.rolesContainAdmin(commonAPI, roles)) {
      throw new JeecgBootException(I18nUtil.getMessage("sysUser.noPermissionModifyUserStatus"));
    }

    userMapper.update(
        new SysUser().setStatus(Integer.parseInt(status)),
        new UpdateWrapper<SysUser>().lambda().eq(SysUser::getId, id));

    // When status is USER_FREEZE(2), revoke all PENDING password-reset external links for the account
    if (String.valueOf(CommonConstant.USER_FREEZE).equals(status)) {
      SysUser user = this.getById(id);
      if (user != null && passwordResetLinkFacade != null && StrUtil.isNotBlank(user.getUsername())) {
        try {
          String op = currentLoginUserIdSafe();
          int n = passwordResetLinkFacade.revokeAllPendingByUsername(
              user.getUsername(), op, "Account status changed to " + text);
          log.info("[updateStatus] account={} disabled/frozen, auto-revoked {} password reset links", user.getUsername(), n);
        } catch (Exception e) {
          log.warn("[updateStatus] auto-revoke password reset links exception: {}");
        }
      }
    }
  }

  private static String currentLoginUserIdSafe() {
    try {
      return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
    } catch (Exception e) {
      return "SYS";
    }
  }

  /**
   * Update departments under tenant
   *
   * @param departs
   */
  public void updateTenantDepart(SysUser user, String tenantId, String departs) {
    List<String> departList = new ArrayList<>();
    long startTime = System.currentTimeMillis();
    if (oConvertUtils.isNotEmpty(departs)) {
      // Get department ids under current tenant from front-end
      departList = sysUserDepartMapper.getTenantDepart(
          Arrays.asList(departs.split(SymbolConstant.COMMA)), tenantId);
    }
    long endTime = System.currentTimeMillis();
    log.debug("Query user department elapsed: " + (endTime - startTime) + "ms");
    // Query departments linked to users under current tenant
    List<SysUserDepart> userDepartList = sysUserDepartMapper.getTenantUserDepart(user.getId(), tenantId);
    if (userDepartList != null && userDepartList.size() > 0 && departList.size() > 0) {
      for (SysUserDepart depart : userDepartList) {
        
      }
    }
    long endTime1 = System.currentTimeMillis();
    log.debug("Update department role elapsed: " + (endTime1 - startTime) + "ms");

    if (departList.size() > 0) {
      // Delete departments under user
      sysUserDepartMapper.deleteUserDepart(user.getId(), tenantId);
      for (String departId : departList) {
        // Add department
        SysUserDepart userDepart = new SysUserDepart(user.getId(), departId);
        sysUserDepartMapper.insert(userDepart);
      }
    }
    long endTime2 = System.currentTimeMillis();
    log.debug("Update user department elapsed: " + (endTime2 - startTime) + "ms");
  }

  /**
   * Save user positions
   *
   * @param userId
   * @param positionIds
   */
  private void saveUserPosition(String userId, String positionIds) {
    if (oConvertUtils.isNotEmpty(positionIds)) {
      String[] positionIdArray = positionIds.split(SymbolConstant.COMMA);
      for (String postId : positionIdArray) {
        SysUserPosition userPosition = new SysUserPosition();
        userPosition.setUserId(userId);
        userPosition.setPositionId(postId);
        sysUserPositionMapper.insert(userPosition);
      }
    }
  }

  /**
   * Edit user positions
   *
   * @param userId
   * @param positionIds
   */
  private void editUserPosition(String userId, String positionIds) {
    // Delete first
    LambdaQueryWrapper<SysUserPosition> query = new LambdaQueryWrapper<>();
    query.eq(SysUserPosition::getUserId, userId);
    sysUserPositionMapper.delete(query);
    // Then insert data
    this.saveUserPosition(userId, positionIds);
  }

  /**
   * Set user position ids (comma-joined)
   *
   * @param sysUser
   */
  private void userPositionId(SysUser sysUser) {
    if (null != sysUser) {
      List<String> positionList = sysUserPositionMapper.getPositionIdByUserId(sysUser.getId());
      sysUser.setPost(CommonUtils.getSplitText(positionList, SymbolConstant.COMMA));
    }
  }

  /**
   * Query user's current login department id
   *
   * @param orgCode
   */
  private @Nullable String getDepartIdByOrCode(String orgCode) {
    if (oConvertUtils.isEmpty(orgCode)) {
      return null;
    }
    LambdaQueryWrapper<SysDepart> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(SysDepart::getOrgCode, orgCode);
    queryWrapper.select(SysDepart::getId);
    SysDepart depart = sysDepartMapper.selectOne(queryWrapper);
    if (depart == null || oConvertUtils.isEmpty(depart.getId())) {
      return null;
    }
    return depart.getId();
  }

  /**
   * Query user's role codes (comma-separated)
   *
   * @param userId
   */
  private @Nullable String getJoinRoleCodeByUserId(String userId) {
    if (oConvertUtils.isEmpty(userId)) {
      return null;
    }
    // If SAAS mode on, filter by tenant id
    Integer tenantId = null;
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      // Enabled but no tenant id: default -1 so queries return nothing
      tenantId = oConvertUtils.getInt(TenantContext.getTenant(), -1);
    }
    List<SysRole> roleList = sysRoleMapper.getRoleCodeListByUserId(userId, tenantId);
    if (CollectionUtils.isEmpty(roleList)) {
      return null;
    }
    return roleList.stream()
        .map(SysRole::getRoleCode)
        .collect(Collectors.joining(SymbolConstant.COMMA));
  }

  /**
   * Remove department leaders
   *
   * @param departChargeUserIdList
   * @param departChargeUsers
   * @param departId
   */
  private void removeDepartmentManager(
      List<String> departChargeUserIdList, List<SysUser> departChargeUsers, String departId) {
    // Remove department leaders
    for (String chargeUserId : departChargeUserIdList) {
      for (SysUser chargeUser : departChargeUsers) {
        if (chargeUser.getId().equals(chargeUserId)) {
          String departIds = chargeUser.getDepartIds();
          List<String> list = new ArrayList<String>(Arrays.asList(departIds.split(",")));
          list.remove(departId);
          String newDepartIds = String.join(",", list);
          chargeUser.setDepartIds(newDepartIds);
          this.baseMapper.updateById(chargeUser);
          break;
        }
      }
    }
  }

  // ======================================= begin user-dept user list export
  // =========================================
  @Override
  public ModelAndView exportAppUser(HttpServletRequest request) {
    Integer tenantId = oConvertUtils.getInt(TenantContext.getTenant());
    // Step.1 Build query conditions; export selected department ids
    String departIds = request.getParameter("departIds");
    List<String> list = new ArrayList<>();
    if (oConvertUtils.isNotEmpty(departIds)) {
      list = Arrays.asList(departIds.split(SymbolConstant.COMMA));
    }
    // QueryUserData
    List<SysUser> userList = userMapper.getUserByDepartsTenantId(list, tenantId);
    // GetDepartmentName
    List<SysUserDepVo> userDepVos = sysDepartMapper.getUserDepartByTenantUserId(userList, tenantId);
    // Get positions
    List<SysUserPositionVo> positionVos = sysUserPositionMapper.getPositionIdByUsersTenantId(userList, tenantId);
    // step2 group by user id
    // Iterate users and assemble export rows
    List<AppExportUserVo> exportUserVoList = new ArrayList<>();
    for (SysUser sysUser : userList) {
      AppExportUserVo exportUserVo = new AppExportUserVo();
      BeanUtils.copyProperties(sysUser, exportUserVo);
      String departNames = userDepVos.stream()
          .filter(item -> item.getUserId().equals(sysUser.getId()))
          .map(SysUserDepVo::getDepartName)
          .collect(Collectors.joining(SymbolConstant.SEMICOLON));
      exportUserVo.setDepart(departNames);
      String posNames = positionVos.stream()
          .filter(item -> item.getUserId().equals(sysUser.getId()))
          .map(SysUserPositionVo::getName)
          .collect(Collectors.joining(SymbolConstant.SEMICOLON));
      exportUserVo.setPosition(posNames);
      exportUserVoList.add(exportUserVo);
    }
    // step3 Build Excel export params
    ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
    // Export file name
    mv.addObject(NormalExcelConstants.FILE_NAME, "UserList");
    mv.addObject(NormalExcelConstants.CLASS, AppExportUserVo.class);
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    ExportParams exportParams = new ExportParams(
        "Import rules:\n"
            + "1. When user number exists, data matches by user number; on success only position and employee number are updated;\n"
            + "2. When user number does not exist, import supports phone, email, name, department, position, employee number; phone is required;\n"
            + "3. Parent/child departments connected with /, e.g. Finance/Finance Dept I; multiple departments or positions connected with ;, e.g. Finance;R&D",
        "Exported by:" + user.getRealname(),
        "Export information");
    mv.addObject(NormalExcelConstants.PARAMS, exportParams);
    mv.addObject(NormalExcelConstants.DATA_LIST, exportUserVoList);
    return mv;
  }

  // ======================================= end user-department user-list export
  // =========================================

  // ======================================= begin user-department user-list import
  // =========================================
  @Override
  public Result<?> importAppUser(HttpServletRequest request) {
    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
    Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
    Integer tenantId = oConvertUtils.getInt(TenantContext.getTenant());
    SysTenant sysTenant = sysTenantMapper.selectById(tenantId);
    // Error message
    List<String> errorMessage = new ArrayList<>();
    int successLines = 0, errorLines = 0;
    for (Map.Entry<String, MultipartFile> entity : fileMap.entrySet()) {
      MultipartFile file = entity.getValue();
      ImportParams params = new ImportParams();
      params.setTitleRows(2);
      params.setHeadRows(1);
      params.setNeedSave(true);
      // Position map: key=name, value=id (avoid repeated queries)
      Map<String, String> positionMap = new HashMap<>();
      // Department map: key=name, value=SysDepart (avoid repeated queries)
      Map<String, SysDepart> departMap = new HashMap<>();
      try {
        List<AppExportUserVo> listSysUsers = ExcelImportUtil.importExcel(file.getInputStream(), AppExportUserVo.class,
            params);
        for (int i = 0; i < listSysUsers.size(); i++) {
          // Track current row number
          int lineNumber = i + 1;
          // Track whether edit or add
          boolean isEdit = false;
          AppExportUserVo sysUserExcel = listSysUsers.get(i);
          String id = sysUserExcel.getId();
          String workNo = sysUserExcel.getWorkNo();
          String email = sysUserExcel.getEmail();
          String phone = sysUserExcel.getPhone();
          String realname = sysUserExcel.getRealname();
          String depart = sysUserExcel.getDepart();
          String position = sysUserExcel.getPosition();
          SysUser sysUser = new SysUser();
          // If id exists, treat as update
          if (oConvertUtils.isNotEmpty(id)) {
            // P2 (CodePlan §4.3.4): existence check only — whitelisted columns suffice.
            SysUser user = userMapper.selectInfoById(id);
            if (null == user) {
              errorLines++;
              errorMessage.add("Row " + lineNumber + " row: user does not exist, check if number was modified, import ignored.");
              continue;
            }
            isEdit = true;
            sysUser.setId(id);
          } else {
            // Handle existing tenant/user; update when user already exists
            isEdit = false;
          }
          if (oConvertUtils.isNotEmpty(workNo)) {
            sysUser.setWorkNo(workNo);
          }
          try {
            if (isEdit) {
              userMapper.updateById(sysUser);
            } else {
              if (oConvertUtils.isEmpty(phone)) {
                errorMessage.add("Row " + lineNumber + " row: phone number is empty, import ignored.");
                errorLines++;
                continue;
              }
              SysUser userByPhone = userMapper.getUserByPhone(phone);
              if (null != userByPhone) {
                // If already in tenant, forbid import; otherwise update
                Integer tenantCount = userTenantMapper.userTenantIzExist(userByPhone.getId(), tenantId);
                if (tenantCount > 0) {
                  errorMessage.add(
                      "Row " + lineNumber + " row: member already exists in this organization; if not in list, verify if pending review or resigned, import ignored.");
                  errorLines++;
                  continue;
                }
                sysUser.setId(userByPhone.getId());
                userMapper.updateById(sysUser);
                this.addUserTenant(
                    sysUser.getId(), tenantId, userByPhone.getUsername(), sysTenant.getName());
              } else {
                // Default password is "tenant house number + phone"
                String password = sysTenant.getHouseNumber() + phone;
                String salt = oConvertUtils.randomGen(8);
                sysUser.setSalt(salt);
                // Hash password with salt
                String passwordEncode = org.jeecg.common.util.crypto.SmCryptoUtil.hashPassword(password);
                sysUser.setPassword(passwordEncode);
                sysUser.setUsername(phone);
                sysUser.setRealname(oConvertUtils.getString(realname, phone));
                sysUser.setEmail(email);
                sysUser.setPhone(phone);
                sysUser.setStatus(CommonConstant.DEL_FLAG_1);
                sysUser.setDelFlag(CommonConstant.DEL_FLAG_0);
                sysUser.setCreateTime(new Date());
                userMapper.insert(sysUser);
                this.addUserTenant(
                    sysUser.getId(), tenantId, sysUser.getUsername(), sysTenant.getName());
              }
            }
            // Add or edit position
            if (oConvertUtils.isNotEmpty(position)) {
              this.addOrEditPosition(sysUser.getId(), position, isEdit, tenantId, positionMap);
            }
            // Departments can be added only on create
            if (!isEdit) {
              // Create or edit department
              this.addOrEditDepart(sysUser.getId(), depart, tenantId, departMap);
            }
            successLines++;
          } catch (Exception e) {
            errorLines++;
            String message = e.getMessage().toLowerCase();

            // Infer error from index name
            if (message.contains(CommonConstant.SQL_INDEX_UNIQ_SYS_USER_USERNAME)) {
              errorMessage.add("Row " + lineNumber + " row: username already exists, import ignored.");
            } else if (message.contains(CommonConstant.SQL_INDEX_UNIQ_SYS_USER_WORK_NO)) {
              errorMessage.add("Row " + lineNumber + " row: employee number already exists, import ignored.");
            } else if (message.contains(CommonConstant.SQL_INDEX_UNIQ_SYS_USER_PHONE)) {
              errorMessage.add("Row " + lineNumber + " row: phone number already exists, import ignored.");
            } else if (message.contains(CommonConstant.SQL_INDEX_UNIQ_SYS_USER_EMAIL)) {
              errorMessage.add("Row " + lineNumber + " row: email already exists, import ignored.");
            } else if (message.contains(CommonConstant.SQL_INDEX_UNIQ_SYS_USER)) {
              errorMessage.add("Row " + lineNumber + " row: violates table uniqueness constraint.");
            } else {
              errorMessage.add("Row " + lineNumber + " row: unknown error, import ignored");
              log.error("operation failed", e);
            }
          }
        }
      } catch (Exception e) {
        errorMessage.add("Exception occurred: import failed");
        log.error("import excel failed", e);
        log.error("operation failed", e);
      } finally {
        try {
          file.getInputStream().close();
        } catch (IOException e) {
          log.error("operation failed", e);
        }
      }
    }
    try {
      return ImportExcelUtil.imporReturnRes(errorLines, successLines, errorMessage);
    } catch (IOException e) {
      log.error("operation failed", e);
    }
    return null;
  }

  /**
   * Add or edit position
   *
   * @param userId user id
   * @param position    position name joined by /
   * @param isEdit      add or edit flag
   * @param positionMap position map key=name, value=id
   */
  private void addOrEditPosition(
      String userId,
      String position,
      Boolean isEdit,
      Integer tenantId,
      Map<String, String> positionMap) {
    Page<SysPosition> page = new Page<>(1, 1);
    String[] positions = position.split(SymbolConstant.SEMICOLON);
    List<String> positionList = Arrays.asList(positions);
    positionList = positionList.stream().distinct().collect(Collectors.toList());
    // Delete positions under current tenant by name/tenantId/userId
    sysUserPositionMapper.deleteUserPosByNameAndTenantId(positionList, tenantId, userId);
    // Iterate rows to add or update
    for (String pos : positionList) {
      String posId = "";
      if (positionMap.containsKey(pos)) {
        posId = positionMap.get(pos);
      } else {
        List<String> namePage = sysPositionMapper.getPositionIdByName(pos, tenantId, page);
        if (CollectionUtil.isNotEmpty(namePage)) {
          posId = namePage.get(0);
          positionMap.put(pos, posId);
        }
      }

      // If position id set, insert relation directly
      if (oConvertUtils.isNotEmpty(posId)) {
        this.addSysUserPosition(userId, posId);
        continue;
      }

      // Positions are created only when not in edit mode
      if (!isEdit) {
        // Create position and user-position relation
        SysPosition sysPosition = new SysPosition();
        sysPosition.setName(pos);
        sysPosition.setCode(RandomUtil.randomString(10));
        sysPosition.setTenantId(tenantId);
        sysPositionMapper.insert(sysPosition);
        positionMap.put(pos, sysPosition.getId());
        this.addSysUserPosition(userId, sysPosition.getId());
      }
    }
  }

  /** Add user position */
  private void addSysUserPosition(String userId, String positionId) {
    Long count = sysUserPositionMapper.getUserPositionCount(userId, positionId);
    if (count == 0) {
      SysUserPosition userPosition = new SysUserPosition();
      userPosition.setUserId(userId);
      userPosition.setPositionId(positionId);
      sysUserPositionMapper.insert(userPosition);
    }
  }

  /**
   * Create or edit department
   *
   * @param userId user id
   * @param depart    department name
   * @param tenantId Tenant id
   * @param departMap department map; key=name, value=SysDepart.
   */
  private void addOrEditDepart(
      String userId, String depart, Integer tenantId, Map<String, SysDepart> departMap) {
    // Batch-link departments and users
    if (StringUtils.isNotEmpty(depart)) {
      Page<SysDepart> page = new Page<>(1, 1);
      // Split multiple departments
      String[] departNames = depart.split(SymbolConstant.SEMICOLON);
      List<String> departNameList = Arrays.asList(departNames);
      departNameList = departNameList.stream().distinct().collect(Collectors.toList());
      // Department id
      String parentId = "";
      for (String departName : departNameList) {
        String[] names = departName.split(SymbolConstant.SINGLE_SLASH);
        // Join department names
        String nameStr = "";
        for (int i = 0; i < names.length; i++) {
          String name = names[i];
          // Concatenate name
          if (oConvertUtils.isNotEmpty(nameStr)) {
            nameStr = nameStr + SymbolConstant.SINGLE_SLASH + name;
          } else {
            nameStr = name;
          }
          SysDepart sysDepart = null;
          // Check whether map contains department name
          if (departMap.containsKey(nameStr)) {
            sysDepart = departMap.get(nameStr);
          } else {
            // If missing, query DB
            List<SysDepart> departPageByName = sysDepartMapper.getDepartPageByName(page, name, tenantId, parentId);
            // Empty department means create one
            if (CollectionUtil.isEmpty(departPageByName)) {
              JSONObject formData = new JSONObject();
              formData.put("parentId", parentId);
              String[] codeArray = (String[]) FillRuleUtil.executeRule(FillRuleConstant.DEPART, formData);
              sysDepart = new SysDepart();
              sysDepart.setParentId(parentId);
              sysDepart.setOrgCode(codeArray[0]);
              sysDepart.setOrgType(codeArray[1]);
              sysDepart.setTenantId(tenantId);
              sysDepart.setDepartName(name);
              sysDepart.setIzLeaf(CommonConstant.IS_LEAF);
              sysDepart.setDelFlag(String.valueOf(CommonConstant.DEL_FLAG_0));
              sysDepart.setStatus(CommonConstant.STATUS_1);
              sysDepartMapper.insert(sysDepart);
            } else {
              sysDepart = departPageByName.get(0);
            }
            // If parent id set, mark parent as non-leaf
            if (oConvertUtils.isNotEmpty(parentId)) {
              sysDepartMapper.setMainLeaf(parentId, CommonConstant.NOT_LEAF);
            }
            parentId = sysDepart.getId();
            departMap.put(nameStr, sysDepart);
          }
          // On last segment, insert department-user relation
          if (i == names.length - 1) {
            Long count = sysUserDepartMapper.getCountByDepartIdAndUserId(userId, sysDepart.getId());
            if (count == 0) {
              SysUserDepart userDepart = new SysUserDepart(userId, sysDepart.getId());
              sysUserDepartMapper.insert(userDepart);
            }
          }
        }
      }
    }
  }

  /**
   * Add user-tenant relation
   *
   * @param userId
   * @param tenantId
   * @param invitedUsername invitee username
   * @param tenantName      tenant name
   */
  private void addUserTenant(
      String userId, Integer tenantId, String invitedUsername, String tenantName) {
    SysUserTenant userTenant = new SysUserTenant();
    userTenant.setTenantId(tenantId);
    userTenant.setUserId(userId);
    userTenant.setStatus(CommonConstant.USER_TENANT_INVITE);
    userTenantMapper.insert(userTenant);
    // update-begin---author:wangshuai ---date:20230710
    //for: [QQYUN-5731]no reminder when importing users------------
    // Send system notification
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    MessageDTO messageDTO = new MessageDTO();
    String title = sysUser.getRealname() + " invites you to join " + tenantName + "。";
    messageDTO.setTitle(title);
    Map<String, Object> data = new HashMap<>();
    // update-begin---author:wangshuai---date:2024-03-11---for: [QQYUN-8425] after user import succeeds
    // Message reminder navigate to approve page---
    data.put(CommonConstant.NOTICE_MSG_BUS_TYPE, SysAnnmentTypeEnum.TENANT_INVITE.getType());
    // update-end---author:wangshuai---date:2024-03-11---for: [QQYUN-8425] after user import succeeds
    // Message reminder navigate to approve page---
    messageDTO.setData(data);
    messageDTO.setContent(title);
    messageDTO.setToUser(invitedUsername);
    messageDTO.setFromUser("system");
    systemSendMsgHandle.sendMessage(messageDTO);
    // update-end---author:wangshuai ---date:20230710
    //for: [QQYUN-5731]no reminder when importing users------------
  }

  // ======================================= end user-department user-list import
  // =========================================

  @Override
  public void checkUserAdminRejectDel(String userIds) {
    LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
    query.in(SysUser::getId, Arrays.asList(userIds.split(SymbolConstant.COMMA)));
    query.eq(SysUser::getUsername, "admin");
    Long adminRoleCount = this.baseMapper.selectCount(query);
    // Greater than 0 means admin users exist; delete forbidden
    if (adminRoleCount > 0) {
      throw new JeecgBootException(I18nUtil.getMessage("sysUser.adminUserCannotDelete"));
    }
  }

  @Override
  public void changePhone(JSONObject json, String username) {
    String smscode = json.getString("smscode");
    String phone = json.getString("phone");
    String type = json.getString("type");
    if (oConvertUtils.isEmpty(phone)) {
      throw new JeecgBootException("Please enter original phone number!");
    }
    if (oConvertUtils.isEmpty(smscode)) {
      throw new JeecgBootException("Please enter verification code!");
    }
    // step1 Verify original phone matches current user
    SysUser sysUser = userMapper.getUserByNameAndPhone(phone, username);
    if (null == sysUser) {
      throw new JeecgBootException("Original phone number does not match; cannot change password!");
    }
    // step2 By type, verify old-phone or new-phone captcha
    // Verify original phone
    if (CommonConstant.VERIFY_ORIGINAL_PHONE.equals(type)) {
      this.verifyPhone(phone, smscode);
    } else if (CommonConstant.UPDATE_PHONE.equals(type)) {
      // Update phone number
      String newPhone = json.getString("newPhone");
      // Reject change when new phone equals old phone
      if (newPhone.equals(phone)) {
        throw new JeecgBootException("New phone number is the same as the old one, cannot change!");
      }
      this.verifyPhone(newPhone, smscode);
      // step3 After new-phone captcha succeeds, update phone
      sysUser.setPhone(newPhone);
      userMapper.updateById(sysUser);
    }
  }

  /**
   * Verify phone number
   *
   * @param phone
   * @param smsCode
   * @return
   */
  public void verifyPhone(String phone, String smsCode) {
    String phoneKey = CommonConstant.CHANGE_PHONE_REDIS_KEY_PRE + phone;
    Object phoneCode = redisUtil.get(phoneKey);
    if (null == phoneCode) {
      throw new JeecgBootException("Verification code expired; please resend the verification code!");
    }
    boolean otpOk = false;
    String sv = String.valueOf(phoneCode);
    if (smsCode.equals(sv)) {
      otpOk = true; // Transitional plain-equals compatibility: legacy captcha codes expire naturally within 11 minutes
    } else {
      otpOk = otpHasher.verify(smsCode, sv, "CHANGE_PHONE", phone);
    }
    if (!otpOk) {
      throw new JeecgBootException("SMS verification code does not match!");
    }
    // Clear phone captcha after verification
    redisUtil.removeAll(phoneKey);
  }

  @Override
  public void sendChangePhoneSms(JSONObject jsonObject, String username, String ipAddress) {
    String type = jsonObject.getString("type");
    String phone = jsonObject.getString("phone");
    if (oConvertUtils.isEmpty(phone)) {
      throw new JeecgBootException("Please enter phone number!");
    }
    // step1 By type, send captcha to old or new phone
    if (CommonConstant.VERIFY_ORIGINAL_PHONE.equals(type)) {
      // step2 Old-phone captcha must match current phone
      SysUser sysUser = userMapper.getUserByNameAndPhone(phone, username);
      if (null == sysUser) {
        throw new JeecgBootException("Old phone number does not match, cannot change phone number!");
      }
    } else if (CommonConstant.UPDATE_PHONE.equals(type)) {
      // step3 New phone must not already be registered
      SysUser userByPhone = userMapper.getUserByPhone(phone);
      if (null != userByPhone) {
        throw new JeecgBootException("Mobile number already registered, please try another!");
      }
    }
    // step4 Send SMS captcha
    this.sendPhoneSms(phone, ipAddress);
  }

  @Override
  public cn.hutool.json.JSONObject sumRegisterCounts() {

    cn.hutool.json.JSONObject json = new cn.hutool.json.JSONObject();

    long num = count(new QueryWrapper<SysUser>().isNotNull("customer_type"));

    List<SysUser> userList = page(
        new Page<SysUser>(1, 1),
        new QueryWrapper<SysUser>().isNotNull("customer_type").orderByAsc("create_time"))
        .getRecords();

    long avgDailyNum = 0L;

    if (!userList.isEmpty()) {
      Date startTime = userList.get(0).getCreateTime();

      long daysBetween = DateUtil.betweenDay(startTime, new Date(), true);

      daysBetween = daysBetween == 0 ? 1 : daysBetween;

      avgDailyNum = num / daysBetween;
    }

    json.set("num", num);
    json.set("avgDailyNum", avgDailyNum);

    return json;
  }

  @Override
  public cn.hutool.json.JSONArray getRegisterNumTrend(Integer lastNum, String dateType) {
    Date now = new Date();

    cn.hutool.json.JSONArray results = new cn.hutool.json.JSONArray();

    for (int i = lastNum; i > 0; i--) {
      Date start = null;
      Date end = null;

      cn.hutool.json.JSONObject result = new cn.hutool.json.JSONObject();

      if ("day".equals(dateType)) {
        start = DateUtil.beginOfDay(DateUtil.offsetDay(now, -i));
        end = DateUtil.endOfDay(DateUtil.offsetDay(now, -i));

        result.set("date", DateUtil.format(start, "yyyy-MM-dd"));

      } else if ("month".equals(dateType)) {
        start = DateUtil.beginOfMonth(DateUtil.offsetMonth(now, -i));
        end = DateUtil.endOfMonth(DateUtil.offsetMonth(now, -i));
        result.set("date", DateUtil.format(start, "yyyy-MM"));

      } else if ("year".equals(dateType)) {
        start = DateUtil.beginOfYear(DateUtil.offset(now, DateField.YEAR, -i));
        end = DateUtil.endOfYear(DateUtil.offset(now, DateField.YEAR, -i));

        result.set("date", DateUtil.format(start, "yyyy"));
      }

      Long num = count(
          new QueryWrapper<SysUser>()
              .isNotNull("customer_type")
              .between("create_time", start, end));

      result.set("num", num);

      results.add(result);
    }

    return results;
  }

  @Override
  @Cacheable(cacheNames = "dcs_SysUserServiceImpl_getRolesByUserId")
  public List<SysRole> getRolesByUserId(String userId) {

    return sysRoleMapper.getRoleCodeListByUserId(userId, null);
  }

  @Override
  // Evict @Cacheable(cacheNames = "dcs_SysUserServiceImpl_getRolesByUserId")
  @CacheEvict(cacheNames = "dcs_SysUserServiceImpl_getRolesByUserId")
  public void refreshRolesByUserId(String userId) {
    try {
      if (userId != null && !userId.isEmpty()) {
        sessionKickService.kickAllDevices(userId, "role_change");
      }
    } catch (Exception ignore) {
    }
  }

  /**
   * send SMS verification code
   *
   * @param phone
   */
  private void sendPhoneSms(String phone, String clientIp) {
    String redisKey = CommonConstant.CHANGE_PHONE_REDIS_KEY_PRE + phone;
    Object object = redisUtil.get(redisKey);

    if (object != null) {
      throw new JeecgBootException("Verification code is still valid within 10 minutes!");
    }

    // Add checks to prevent SMS API abuse
    if (!DySmsLimit.canSendSms(clientIp)) {
      log.warn("--------[WARNING] IP address:{}, too many SMS API requests-------", clientIp);
      throw new JeecgBootException("Too many SMS API requests, please try again later!", CommonConstant.PHONE_SMS_FAIL_CODE);
    }

    // B5-3: fixed OTP only when OTP_ALLOW_FIXED_CODE=true (never by profile alone)
    Environment environment = SpringContextUtils.getBean(Environment.class);
    String captcha = org.jeecg.common.util.security.OtpCodeGenerator.generateNumeric6(environment);
    JSONObject obj = new JSONObject();
    obj.put("code", captcha);
    try {
      boolean sendSmsSuccess = DySmsHelper.sendSms(phone, obj, DySmsEnum.LOGIN_TEMPLATE_CODE);
      if (!sendSmsSuccess) {
        throw new JeecgBootException("SMS verification code send failed, please try again later!");
      }
      String hash = otpHasher.hash(captcha, "CHANGE_PHONE", phone);
      redisUtil.set(redisKey, hash, 600);
    } catch (ClientException e) {
      log.error("operation failed", e);
      throw new JeecgBootException("SMS API not configured, please contact administrator!");
    }
  }

  // C3-WHITELIST-CTRLLED: field-level only (not updateById whole entity)
  @Override
  public void updateAccountWithRoles(SysUser user) {
    LambdaUpdateWrapper<SysUser> uw6 = Wrappers.lambdaUpdate();
    uw6.eq(SysUser::getId, user.getId())
       .set(SysUser::getUsername, user.getUsername())
       .set(SysUser::getRealname, user.getRealname())
       .set(SysUser::getAvatar, user.getAvatar())
       .set(SysUser::getBirthday, user.getBirthday())
       .set(SysUser::getSex, user.getSex())
       .set(SysUser::getEmail, user.getEmail())
       .set(SysUser::getEmailNotice, user.getEmailNotice())
       .set(SysUser::getPhone, user.getPhone())
       .set(SysUser::getStatus, user.getStatus())
       .set(SysUser::getAllowLogin, user.getAllowLogin())
       .set(SysUser::getWorkNo, user.getWorkNo())
       .set(SysUser::getTelephone, user.getTelephone())
       .set(SysUser::getActivitiSync, user.getActivitiSync())
       .set(SysUser::getUserIdentity, user.getUserIdentity())
       .set(SysUser::getDepartIds, user.getDepartIds())
       .set(SysUser::getKycStatus, user.getKycStatus())
       .set(SysUser::getKycAttachment1, user.getKycAttachment1())
       .set(SysUser::getKycAttachment2, user.getKycAttachment2())
       .set(SysUser::getKycAttachment3, user.getKycAttachment3())
       .set(SysUser::getCustomerType, user.getCustomerType())
       .set(SysUser::getWalletAddress, user.getWalletAddress())
       .set(SysUser::getReason, user.getReason())
       .set(SysUser::getLastPasswordChangeTime, user.getLastPasswordChangeTime())
       .set(SysUser::getFinancialState, user.getFinancialState())
       .set(SysUser::getInviterId, user.getInviterId())
       .set(SysUser::getPhoneCode, user.getPhoneCode())
       .set(SysUser::getIdType, user.getIdType())
       .set(SysUser::getNewSubmit, user.getNewSubmit())
       .set(SysUser::getLastLoginTime, user.getLastLoginTime())
       .set(SysUser::getUpdateBy, StpUtil.getLoginIdAsString())
       .set(SysUser::getUpdateTime, new Date());
    Set<String> actual6 = C3FieldReflectionInspector.extractSetFieldNames(uw6);
    if (!actual6.containsAll(REQUIRED_FIELDS_FOR_USER_EDIT)) { Set<String> miss = new HashSet<>(REQUIRED_FIELDS_FOR_USER_EDIT); miss.removeAll(actual6); throw new JeecgBootException("C3 BLOCK MISSING_WRITE_FIELDS table=sys_user missing=" + miss); }
    // LedgerWriteGuard.assertLambdaUpdateWrapperFields("sys_user", uw6, "update_time", "update_by"); // table not whitelisted in LedgerWriteGuard
    baseMapper.update(null, uw6);
    sysUserRoleMapper.delete(
        new QueryWrapper<SysUserRole>().eq("user_id", user.getId()));
    List<String> roleIds = user.getSelectRoleIds();
    if (roleIds != null && !roleIds.isEmpty()) {
      List<SysUserRole> batch = new ArrayList<>(roleIds.size());
      for (String rid : roleIds) {
        if (rid == null || rid.isEmpty()) {
          continue;
        }
        SysUserRole ur = new SysUserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(rid);
        batch.add(ur);
      }
      if (!batch.isEmpty()) {
        for (SysUserRole ur : batch) {
          sysUserRoleMapper.insert(ur);
        }
      }
    }
  }

  @Override
  public Set<String> frozenBatchUpdateStatuses(String idsComma, String status) {
    Set<String> targetIds = new HashSet<>();
    if (idsComma != null && !idsComma.isEmpty()) {
      for (String s : idsComma.split(",")) {
        String id = s == null ? null : s.trim();
        if (id != null && !id.isEmpty()) {
          targetIds.add(id);
        }
      }
    }
    if (!targetIds.isEmpty()) {
      List<String> staffIds =
          this.list(
                  new LambdaQueryWrapper<SysUser>()
                      .select(SysUser::getId)
                      .in(SysUser::getDlygx, targetIds))
              .stream()
              .map(SysUser::getId)
              .toList();
      if (staffIds != null && !staffIds.isEmpty()) {
        targetIds.addAll(staffIds);
      }
    }
    if (!targetIds.isEmpty()) {
      checkUserAdminRejectDel(String.join(",", targetIds));
      for (String id : targetIds) {
        updateStatus(id, status);
      }
    }
    return targetIds;
  }

  @Override
  public String getLoggedInUserMaskedEmail() {
    String userId = StpUtil.getLoginIdAsString();
    SysUser user = getById(userId);
    if (user == null || StrUtil.isBlank(user.getEmail())) {
      return null;
    }
    return maskEmail(user.getEmail());
  }

  @Override
  public void sendLoggedInUserTwoFactorEmailOtp() {
    String userId = StpUtil.getLoginIdAsString();
    SysUser user = getById(userId);
    if (user == null) {
      throw new JeecgBootException("User does not exist");
    }
    String email = StrUtil.trim(user.getEmail());
    if (StrUtil.isBlank(email)) {
      throw new JeecgBootException("User email is empty");
    }
    String redisKey = CommonConstant.EMAIL_SECOND_AUTH + email;
    String frequencyKey = CommonConstant.EMAIL_SEND_FREQUENCY_KEY + email;
    if (redisUtil.hasKey(frequencyKey)) {
      throw new JeecgBootException("Please wait before requesting another code");
    }
    Environment environment = SpringContextUtils.getBean(Environment.class);
    String captcha = org.jeecg.common.util.security.OtpCodeGenerator.generateNumeric6(environment);
    String hash = otpHasher.hash(captcha, "TWO_FACTOR_AUTHENTICATION", email);
    redisUtil.set(redisKey, hash, 300);
    redisUtil.set(frequencyKey, "1", 60);
    // ttlSeconds=0 → configured transport TTL (jeecg.mq.otp.transport.ttl.seconds /
    // OTP_MQ_TRANSPORT_TTL_SECONDS, default 120s, cap 300s). Was a hard-coded 10s that could
    // not survive MQ backlog/restart — OTP email became permanently undecodable (2026-09-30).
    OpaqueMqCodec.OpaqueResult opaque = OpaqueMqCodec.encodeForProducer("TWO_FACTOR_AUTHENTICATION", captcha, 0);
    cn.hutool.json.JSONObject msg = new cn.hutool.json.JSONObject();
    msg.set("email", email);
    msg.set("emailScene", "VERIFICATION_CODE");
    OpaqueMqCodec.applyToJson(msg, opaque);
    if (StrUtil.isNotBlank(user.getUsername())) {
      msg.set("username", user.getUsername());
    }
    RocketMQUtil rocketMQUtil = SpringContextUtils.getBean(RocketMQUtil.class);
    if (rocketMQUtil != null) {
      rocketMQUtil.sendRetry("sendEmailTopic_topic", msg, null);
    } else {
      log.warn("sendLoggedInUserTwoFactorEmailOtp: RocketMQUtil unavailable, OTP hash stored only, email NOT sent");
    }
  }

  private static String maskEmail(String email) {
    if (StrUtil.isBlank(email)) {
      return email;
    }
    int at = email.lastIndexOf('@');
    String domain = at >= 0 ? email.substring(at) : "";
    String local = at >= 0 ? email.substring(0, at) : email;
    if (local.length() <= 1) {
      local = "*";
    } else if (local.length() <= 4) {
      local = local.charAt(0) + "***";
    } else {
      local = local.substring(0, 2) + "***" + local.substring(local.length() - 2);
    }
    return local + domain;
  }

  private void publishShardSync(SysUser user, String op) {
    try {
      if (shardRedisTemplate == null || user == null || StrUtil.isBlank(user.getId())) {
        return;
      }
      Map<String, Object> payload = new HashMap<>(16);
      payload.put("op", op);
      payload.put("id", user.getId());
      if (!"DELETE".equals(op)) {
        payload.put("username", user.getUsername());
        payload.put("realname", user.getRealname());
        payload.put("avatar", user.getAvatar());
        payload.put("phone", user.getPhone());
        payload.put("email", user.getEmail());
        payload.put("delFlag", user.getDelFlag() == null ? Integer.valueOf(0) : user.getDelFlag());
        payload.put("updateTime", user.getUpdateTime() == null ? new Date() : user.getUpdateTime());
      }
      payload.put("shard_sync_source", "system-sysuser-" + op.toLowerCase(Locale.ROOT));
      shardRedisTemplate.convertAndSend(SHARD_SYNC_USER_CHANNEL, payload);
    } catch (Exception e) {
      log.warn("publish shard-sync sys-user-change failed id={} op={}", user == null ? null : user.getId(), op, e);
    }
  }
}
