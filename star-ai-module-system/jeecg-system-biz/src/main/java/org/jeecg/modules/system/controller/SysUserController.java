package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.session.SaSession;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import java.math.BigDecimal;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.util.RoleConfigUtil;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.desensitization.util.SensitiveInfoUtil;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.security.CommonRoleGuard;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.*;
import org.jeecg.common.valid.DictScope;
import org.jeecg.common.valid.annotation.ValidDictionary;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.system.entity.SysDepart;
import org.jeecg.modules.system.entity.SysPosition;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserMfa;
import org.jeecg.modules.system.entity.SysUserDepart;
import org.jeecg.modules.system.entity.SysUserRole;
import org.jeecg.modules.system.model.DepartIdModel;
import org.jeecg.modules.system.service.*;
import org.jeecg.modules.system.service.crypto.MfaSeedCryptoService;
import org.jeecg.modules.system.vo.OpsCreateUserRequest;
import org.jeecg.modules.system.vo.PhoneValidationRequest;
import org.jeecg.modules.system.vo.SysDepartUsersVO;
import org.jeecg.modules.system.vo.SysUserRoleVO;
import org.jeecg.modules.system.vo.lowapp.DepartAndUserInfo;

import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.jeecg.modules.system.shard.client.IDcsShardClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

/**
 * User table front-end controller @Author scott
 *
 * @since 2018-12-20*/
@Slf4j
@RestController
@RequestMapping("/sys/user")
@Validated
public class SysUserController {

  @Autowired
  private ISysUserService sysUserService;

  @Autowired
  private ISysUserMfaService sysUserMfaService;

  @Autowired
  private MfaSeedCryptoService mfaSeedCryptoService;

  @Autowired
  private ISysDepartService sysDepartService;

  @Autowired
  private ISysUserRoleService sysUserRoleService;

  @Autowired
  private ISysUserDepartService sysUserDepartService;



  @Autowired
  private RedisUtil redisUtil;

  @Autowired
  private RocketMQUtil rocketMQUtil;

  @Resource
  private ISysBaseAPI sysBaseAPI;

  @Value("${jeecg.path.upload}")
  private String upLoadPath;

  @Autowired
  private BaseCommonService baseCommonService;

  @Autowired
  private ISysPositionService sysPositionService;

  @Autowired
  private CommonAPI commonAPI;

  @Autowired
  private MessageSource messageSource;

  @Autowired
  private ISysRoleService sysRoleService;



  @Autowired
  private org.jeecg.modules.system.util.RsaPasswordUtil rsaPasswordUtil;

  @Autowired
  private CurrentBizUserResolver currentBizUserResolver;


  @Lazy
  @Autowired(required = false)
  private IDcsShardClient dcsApi;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private IAccountCreatedEmailService accountCreatedEmailService;

  @Autowired
  private org.jeecg.modules.system.service.impl.SessionKickService sessionKickService;

  @Value("${register.enabled:false}")
  private boolean registerEnabled;

  @Value("${register.whitelist-phones:}")
  private String registerWhitelistPhonesRaw;

  private volatile Set<String> registerWhitelistPhonesNormalized = Collections.emptySet();

  @PostConstruct
  public void logRegisterFeatureFlag() {
    Set<String> phones = parsePhoneWhitelist(registerWhitelistPhonesRaw);
    this.registerWhitelistPhonesNormalized = Collections.unmodifiableSet(phones);
    log.info(
        "REGISTER_ENABLED={} WHITELIST_COUNT={}",
        registerEnabled,
        registerWhitelistPhonesNormalized.size());
  }

  private static Set<String> parsePhoneWhitelist(String raw) {
    Set<String> out = new HashSet<>();
    if (raw == null || raw.isEmpty()) {
      return out;
    }
    String[] parts = raw.split(",");
    for (String p : parts) {
      if (p == null) {
        continue;
      }
      String s = p.trim();
      if (s.isEmpty()) {
        continue;
      }
      StringBuilder normalized = new StringBuilder(s.length());
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (Character.isDigit(c) || c == '+' || c == '-') {
          normalized.append(c);
        }
      }
      if (normalized.length() > 0) {
        out.add(normalized.toString());
      }
    }
    return out;
  }

  private static String normalizePhone(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isDigit(c) || c == '+' || c == '-') {
        out.append(c);
      }
    }
    return out.toString();
  }

  /**
   * Get tenant user data (tenant isolation supported)
   *
   * @param user
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @AutoLog("Get users under tenant")
  @RequestMapping(value = "/list", method = RequestMethod.POST)
  public Result<IPage<SysUser>> queryPageList(
      SysUser user,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      @RequestParam(required = false, name = "roleCode") String roleCode,
      @RequestParam(required = false, name = "roleId") String roleId,
      @RequestParam(value = "label", required = false) String label,
      @RequestParam(required = false) String searchText,
      HttpServletRequest req) {
    QueryWrapper<SysUser> queryWrapper = QueryGenerator.initQueryWrapper(user, req.getParameterMap());

    String userId = currentBizUserResolver.getBizOwnerUserId();
    if (!RoleConfigUtil.isAdmin(commonAPI)) {
      queryWrapper.eq("inviter_id", userId);
    }

    if (StrUtil.isNotBlank(roleCode)) {
      String[] roleCodes = roleCode.split(",");
      List<String> validRoleCodes = new ArrayList<>();
      for (String code : roleCodes) {
        if (StrUtil.isNotBlank(code)) {
          validRoleCodes.add(code.trim());
        }
      }
      if (!validRoleCodes.isEmpty()) {
        if (validRoleCodes.size() == 1) {
          queryWrapper.apply(
              true,
              "id in (select user_id from sys_user_role where role_id = (select id from sys_role where role_code = {0}))",
              validRoleCodes.get(0));
        } else {
          List<SysRole> matchedRoles =
              sysRoleService.list(
                  new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleCode, validRoleCodes));
          List<String> matchedRoleIds =
              matchedRoles == null
                  ? Collections.emptyList()
                  : matchedRoles.stream()
                      .map(SysRole::getId)
                      .filter(StrUtil::isNotBlank)
                      .collect(Collectors.toList());
          if (matchedRoleIds.isEmpty()) {
            queryWrapper.eq("id", "0");
          } else {
            List<String> userIds = sysUserService.getUserIdsByRoleIds(matchedRoleIds);
            if (userIds == null || userIds.isEmpty()) {
              queryWrapper.eq("id", "0");
            } else {
              queryWrapper.in("id", userIds);
            }
          }
        }
      }
    }

    if (StrUtil.isNotBlank(roleId)) {
      String[] roleIds = roleId.split(",");
      List<String> validRoleIds = new ArrayList<>();
      for (String id : roleIds) {
        if (StrUtil.isNotBlank(id)) {
          validRoleIds.add(id.trim());
        }
      }
      if (!validRoleIds.isEmpty()) {
        if (validRoleIds.size() == 1) {
          queryWrapper.apply(
              true, "id in (select user_id from sys_user_role where role_id = {0})",
              validRoleIds.get(0));
        } else {
          List<String> userIds = sysUserService.getUserIdsByRoleIds(validRoleIds);
          if (userIds == null || userIds.isEmpty()) {
            queryWrapper.eq("id", "0");
          } else {
            queryWrapper.in("id", userIds);
          }
        }
      }
    }

    if (StrUtil.isNotBlank(label)) {
      queryWrapper.apply(
          true, "id in (select user_id from sys_user_label where label_code = {0})", label);
    }

    if (StrUtil.isNotBlank(searchText)) {
      queryWrapper.and(
          wrapper -> wrapper.like("username", searchText).or().like("realname", searchText));
    }

    return sysUserService.queryPageList(req, queryWrapper, pageSize, pageNo);
  }

  /**
   * Get system user data (all users, no tenant isolation)
   *
   * @param user
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  /**
   * Get system user data (ops /dcs/user/list via Feign).
   *
   * <p>Must return {@link SysUser} entities (with {@code @FilePreview}) so DictAspect converts
   * avatar / kycAttachment* to preview URLs; do not pre-convert to JSONObject or annotations are ignored.
   */
  @AutoLog("GetSystemUserData")
  @RequestMapping(value = "/listAll", method = RequestMethod.POST)
  public Result<IPage<SysUser>> queryAllPageList(
      SysUser user,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      @RequestParam(required = false) String selectRoleId,
      @RequestParam(value = "label", required = false) String label,
      @RequestParam(required = false) String selectRoleCode,
      @RequestParam(value = "hasDepositBank", required = false) String hasDepositBank,
      @RequestParam(value = "hasBeneficiary", required = false) String hasBeneficiary,
      @RequestParam(required = false) String searchText,
      HttpServletRequest req,
      HttpServletResponse response) {
    QueryWrapper<SysUser> queryWrapper = QueryGenerator.initQueryWrapper(user, req.getParameterMap());

    String userId = currentBizUserResolver.getBizOwnerUserId();
    if (!RoleConfigUtil.isAdmin(commonAPI)) {
      queryWrapper.eq("inviter_id", userId);
    }

    if (StrUtil.isNotBlank(selectRoleId)) {
      queryWrapper.apply(
          true, "id in (select user_id from sys_user_role where role_id = {0})", selectRoleId);
    }

    if (StrUtil.isNotBlank(selectRoleCode)) {
      List<String> roleCodes =
          Arrays.stream(selectRoleCode.split(","))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .collect(Collectors.toList());
      if (!roleCodes.isEmpty()) {
        String placeholders =
            java.util.stream.IntStream.range(0, roleCodes.size())
                .mapToObj(i -> "{" + i + "}")
                .collect(Collectors.joining(", "));
        queryWrapper.apply(
            true,
            "id in (select user_id from sys_user_role where role_id in (select id from sys_role where role_code in ("
                + placeholders
                + ")))",
            roleCodes.toArray());
      }
    }

    if (StrUtil.isNotBlank(label)) {
      queryWrapper.apply(
          true, "id in (select user_id from sys_user_label where label_code = {0})", label);
    }

    if (StrUtil.isNotBlank(hasDepositBank)) {
      List<String> allIds = Collections.emptyList();
      if (dcsApi != null) {
        try {
          Result<List<String>> r = dcsApi.listDepositBankUserIds();
          if (r != null && r.isSuccess() && r.getResult() != null) {
            allIds = r.getResult();
          }
        } catch (Exception e) {
          log.warn("listDepositBankUserIds remote call failed", e);
        }
      }
      if ("1".equals(hasDepositBank)) {
        if (CollUtil.isEmpty(allIds)) {
          queryWrapper.eq("id", "0");
        } else {
          queryWrapper.in("id", allIds);
        }
      } else if ("0".equals(hasDepositBank)) {
        if (!CollUtil.isEmpty(allIds)) {
          queryWrapper.notIn("id", allIds);
        }
      }
    }

    if (StrUtil.isNotBlank(hasBeneficiary)) {
      List<String> allIds = Collections.emptyList();
      if (dcsApi != null) {
        try {
          Result<List<String>> r = dcsApi.listActiveBeneficiarySenderIds();
          if (r != null && r.isSuccess() && r.getResult() != null) {
            allIds = r.getResult();
          }
        } catch (Exception e) {
          log.warn("listActiveBeneficiarySenderIds remote call failed", e);
        }
      }
      if ("1".equals(hasBeneficiary)) {
        if (CollUtil.isEmpty(allIds)) {
          queryWrapper.eq("id", "0");
        } else {
          queryWrapper.in("id", allIds);
        }
      } else if ("0".equals(hasBeneficiary)) {
        if (!CollUtil.isEmpty(allIds)) {
          queryWrapper.notIn("id", allIds);
        }
      }
    }

    if (StrUtil.isNotBlank(searchText)) {
      queryWrapper.and(
          wrapper ->
              wrapper.like("username", searchText).or().like("realname", searchText).or().like("email", searchText));
    }

    Result<IPage<SysUser>> pageResult = sysUserService.queryPageList(req, queryWrapper, pageSize, pageNo);
    if (response != null && pageResult != null && pageResult.isSuccess()) {
      String etag = ETagResponseSupport.computeWeakEtag(pageResult.getResult());
      if (ETagResponseSupport.applyEtagOrNotModified(req, response, etag)) {
        pageResult.setResult(null);
      }
    }
    return pageResult;
  }

  @SaCheckPermission("system:user:add")
  @AutoLog("AddUser")
  @RequestMapping(value = "/add", method = RequestMethod.POST)
  public Result<?> add(@RequestBody JSONObject jsonObject) {
    Result<String> result = new Result<>();
    String selectedRoles = jsonObject.getString("selectedroles");
    String selectedDeparts = jsonObject.getString("selecteddeparts");
    try {
      SysUser user = JSON.parseObject(jsonObject.toJSONString(), SysUser.class);

      SysUser userObj = sysUserService.getUserByName(user.getUsername());

      if (userObj != null) {

        return Result.error(I18nUtil.getMessage("sysUser.operationFailed"));
      }

      if (StrUtil.isBlank(user.getEmail())) {
        return Result.error(I18nUtil.getMessage("sysUser.emailEmpty"));
      }

      if (sysUserService.getUserByEmail(user.getEmail()) != null) {
        return Result.error(I18nUtil.getMessage("sysUser.emailExists"));
      }

      user.setCreateTime(new Date()); // Set create time
      String salt = oConvertUtils.randomGen(8);
      user.setSalt(salt);

      String password = user.getPassword();

      if (oConvertUtils.isNotEmpty(password)) {
        String passwordEncode = SmCryptoUtil.hashPassword(password);
        user.setPassword(passwordEncode);
      } else {
        String passwordEncode = SmCryptoUtil.hashPassword(password);
        user.setPassword(passwordEncode);
      }

      // If new user is agent employee (dlygx has value), sync agent activation status
      if (StrUtil.isNotBlank(user.getDlygx())) {
        SysUser agentUser = sysUserService.getById(user.getDlygx());
        if (agentUser != null) {
          user.setStatus(agentUser.getStatus());
        } else {
          user.setStatus(1); // If agent does not exist, activate by default
        }
      } else {
        user.setStatus(1); // Non-agent staff, activated by default
      }
      user.setDelFlag(CommonConstant.DEL_FLAG_0);
      // Do not set org_code on user table here
      user.setOrgCode(null);
      // Save user via a single service to ensure transaction
      // GetTenantids
      String relTenantIds = jsonObject.getString("relTenantIds");

      if (StrUtil.isNotBlank(user.getAvatar())) {
        try {
          user.setAvatar(CommonUtils.convertObjectId(user.getAvatar()));
        } catch (Exception e) {
          return Result.error(I18nUtil.getMessage("sysUser.avatarInvalid"));
        }
      }

      sysUserService.saveUser(user, selectedRoles, selectedDeparts, relTenantIds);

      result.setResult(user.getId());

      baseCommonService.addLog(
          "AddUser，username： " + user.getUsername(), CommonConstant.LOG_TYPE_2, 2);
      result.success(I18nUtil.getMessage("sysUser.addSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  @PostMapping("/updateAccount")
  @SaCheckPermission("system:user:updateAccount")
  @AutoLog("UpdateUser")
  public Result updateAccount(@RequestBody SysUser user) {

    sysUserService.updateById(user);

    sysUserRoleService.remove(new QueryWrapper<SysUserRole>().eq("user_id", user.getId()));

    List<SysUserRole> userRoleList = user.getSelectRoleIds().stream()
        .map(
            i -> {
              SysUserRole userRole = new SysUserRole();
              userRole.setUserId(user.getId());
              userRole.setRoleId(i);

              return userRole;
            })
        .collect(Collectors.toList());

    sysUserRoleService.saveBatch(userRoleList);

    return Result.OK(I18nUtil.getMessage("common.success.edit"), null);
  }

  @SaCheckPermission("system:user:edit")
  @RequestMapping(value = "/edit", method = { RequestMethod.PUT, RequestMethod.POST })
  @AutoLog("EditUser")
  public Result<SysUser> edit(@RequestBody JSONObject jsonObject) {
    Result<SysUser> result = new Result<SysUser>();
    try {
      // P4: password is @TableField(select=false); edit must still preserve the stored
      // password hash (see user.setPassword(sysUser.getPassword()) below), so select it
      // explicitly. Caller holds system:user:edit (owner-admin role → C6 short-circuit).
      SysUser sysUser = sysUserService.getOne(
          new LambdaQueryWrapper<SysUser>()
              .select(SysUser::getId, SysUser::getUsername, SysUser::getPassword)
              .eq(SysUser::getId, jsonObject.getString("id")));
      if (sysUser == null) {
        return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
      }
      baseCommonService.addLog(
          "EditUser，username： " + sysUser.getUsername(), CommonConstant.LOG_TYPE_2, 2);

      SysUser user = JSON.parseObject(jsonObject.toJSONString(), SysUser.class);

      // Username editable: must be valid email; username/email must not conflict with other users
      String newUsername = StrUtil.trim(user.getUsername());
      if (StrUtil.isBlank(newUsername)) {
        return Result.error(I18nUtil.getMessage("sysUser.usernameEmpty"));
      }
      newUsername = newUsername.toLowerCase();
      if (!Validator.isEmail(newUsername)) {
        return Result.error(
            I18nUtil.getMessage("SysUserController.updateSubUserAuthEmails.invalid_email_format"));
      }
      if (usernameConflictsWithOtherUsers(newUsername, sysUser.getId())) {
        return Result.error(I18nUtil.getMessage("sysUser.operationFailed"));
      }
      if (emailConflictsWithOtherUsers(newUsername, sysUser.getId())) {
        return Result.error(I18nUtil.getMessage("sysUser.emailExists"));
      }

      String newEmail = StrUtil.trim(user.getEmail());
      if (StrUtil.isBlank(newEmail)) {
        // Same as user creation: username is login email; backfill username as email when email not provided
        newEmail = newUsername;
      } else {
        newEmail = newEmail.toLowerCase();
      }
      if (!Validator.isEmail(newEmail)) {
        return Result.error(
            I18nUtil.getMessage("SysUserController.updateSubUserAuthEmails.invalid_email_format"));
      }
      if (emailConflictsWithOtherUsers(newEmail, sysUser.getId())) {
        return Result.error(I18nUtil.getMessage("sysUser.emailExists"));
      }
      if (usernameConflictsWithOtherUsers(newEmail, sysUser.getId())) {
        return Result.error(I18nUtil.getMessage("sysUser.operationFailed"));
      }

      user.setUsername(newUsername);
      user.setEmail(newEmail);
      user.setUpdateTime(new Date());
      // String passwordEncode = org.jeecg.common.util.crypto.SmCryptoUtil.hashPassword(// user.getPassword());
      user.setPassword(sysUser.getPassword());
      String roles = jsonObject.getString("selectedroles");
      String departs = jsonObject.getString("selecteddeparts");
      if (oConvertUtils.isEmpty(departs)) {
        // vue3.0 front-end only passes departIds
        departs = "";
      }
      // Do not set org_code on user table here
      user.setOrgCode(null);
      // Update user via a single service to ensure transaction
      // GetTenantids
      String relTenantIds = jsonObject.getString("relTenantIds");

      if (StrUtil.isNotBlank(user.getAvatar())) {
        try {
          user.setAvatar(CommonUtils.convertObjectId(user.getAvatar()));
        } catch (Exception e) {
          return Result.error(I18nUtil.getMessage("sysUser.avatarInvalid"));
        }
      }

      sysUserService.editUser(user, roles, departs, relTenantIds);
      result.success(I18nUtil.getMessage("sysUser.modifySuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
 * Delete user*/
  @SaCheckPermission("system:user:delete")
  @AutoLog("DeleteUser")
  @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    baseCommonService.addLog("DeleteUser，id： " + id, CommonConstant.LOG_TYPE_2, 3);
    this.sysUserService.deleteUser(id);
    return Result.ok(I18nUtil.getMessage("sysUser.deleteSuccess"));
  }

  /**
 * Batch-delete users*/
  @SaCheckPermission("system:user:deleteBatch")
  @AutoLog("Batch deleteUser")
  @RequestMapping(value = "/deleteBatch", method = RequestMethod.DELETE)
  public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    baseCommonService.addLog("Batch deleteUser， ids： " + ids, CommonConstant.LOG_TYPE_2, 3);
    this.sysUserService.deleteBatchUsers(ids);
    return Result.ok(I18nUtil.getMessage("sysUser.deleteSuccess"));
  }

  /**
 * Freeze & unfreeze user
 *
 * @param jsonObject
 * @return*/
  @SaCheckPermission("system:user:frozenBatch")
  @AutoLog("Freeze&UnfreezeUser")
  @RequestMapping(value = "/frozenBatch", method = RequestMethod.PUT)
  public Result<SysUser> frozenBatch(@RequestBody JSONObject jsonObject) {
    Result<SysUser> result = new Result<SysUser>();

    String ids = jsonObject.getString("ids");
    String status = jsonObject.getString("status");

    Set<String> targetIds = sysUserService.frozenBatchUpdateStatuses(ids, status);

    for (String id : targetIds) {
      sessionKickService.kickAllDevices(id, "account_locked");
    }

    result.success(I18nUtil.getMessage("sysUser.operationSuccess"));
    return result;
  }

  @SaCheckPermission("system:user:queryById")
  @AutoLog("QueryUser")
  @RequestMapping(value = "/queryById", method = RequestMethod.POST)
  public Result<SysUser> queryById(@RequestParam(name = "id", required = true) String id) {
    Result<SysUser> result = new Result<SysUser>();
    SysUser sysUser = sysUserService.getById(id);
    if (sysUser == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      result.setResult(sysUser);
      result.setSuccess(true);
    }
    return result;
  }

  @SaCheckPermission("system:user:queryUserRole")
  @AutoLog("QueryUserRole")
  @RequestMapping(value = "/queryUserRole", method = RequestMethod.POST)
  public Result<List<String>> queryUserRole(
      @RequestParam(name = "userid", required = true) String userid) {
    Result<List<String>> result = new Result<>();
    List<String> list = new ArrayList<String>();
    List<SysUserRole> userRole = sysUserRoleService.list(
        new QueryWrapper<SysUserRole>().lambda().eq(SysUserRole::getUserId, userid));
    if (userRole == null || userRole.size() <= 0) {
      result.error500(I18nUtil.getMessage("sysUser.userRoleInfoNotFound"));
    } else {
      for (SysUserRole sysUserRole : userRole) {
        list.add(sysUserRole.getRoleId());
      }
      result.setSuccess(true);
      result.setResult(list);
    }
    return result;
  }

  /**
   * Check whether user account is unique<br>
   * Can check other fields; pass whatever needs validation...
   *
   * @param sysUser
   * @return
   */
  @PostMapping(value = "/checkOnlyUser")
  @AutoLog("Validate whether user account is unique")
  @SaCheckPermission("system:user:checkOnlyUser")
  public Result<Boolean> checkOnlyUser(SysUser sysUser) {
    Result<Boolean> result = new Result<>();
    // If this param is false, program throws exception
    result.setResult(true);
    try {
      // Query updated user info from input
      sysUser.setPassword(null);
      SysUser user = sysUserService.getOne(new QueryWrapper<SysUser>(sysUser));
      if (user != null) {
        result.setSuccess(false);
        result.setMessage(I18nUtil.getMessage("sysUser.usernameAlreadyExists"));
        return result;
      }

    } catch (Exception e) {
      result.setSuccess(false);
      result.setMessage(e.getMessage());
      return result;
    }
    result.setSuccess(true);
    return result;
  }

  /** Change password*/
  @SaCheckPermission("system:user:changepwd")
  @AutoLog("Change password")
  @PostMapping(value = "/changePassword")
  public Result<?> changePassword(
      @RequestParam String username,
      @RequestParam String password,
      @RequestParam(required = false) String rsaCacheKey) {
    SysUser u = this.sysUserService.getOne(
        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    if (u == null) {
      return Result.error(I18nUtil.getMessage("sysUser.userNotFound"));
    }

    Set<String> roles = sysUserService.getUserRolesSet(username);
    // Only protect admin / operations_admin; other roles (e.g. configuration_admin) can change password directly
    boolean protectedTarget =
        roles != null && (roles.contains("admin") || roles.contains(SecurityRoleConstants.ROLE_OPS_ADMIN));
    if (protectedTarget && !CommonRoleGuard.hasAnyOwnerAdminRole()) {
      return Result.error(403, I18nUtil.getMessage("sysUser.noPermissionModifyPassword"));
    }

    // If RSA cache key provided, password is encrypted and must be decrypted
    String decryptedPassword = password;
    if (StrUtil.isNotBlank(rsaCacheKey)) {
      decryptedPassword = rsaPasswordUtil.decryptPasswordAndClean(rsaCacheKey, password);
      if (decryptedPassword == null) {
        return Result.error(I18nUtil.getMessage("sysUser.passwordDecryptFailed"));
      }
    }

    // Validate password format
    if (!decryptedPassword.matches(CommonConstant.PASSWORD_REGEX)) {
      return Result.error(I18nUtil.getMessage("sysUser.passwordFormatIncorrect"));
    }

    // B5-3: block weak passwords when admin resets for someone else
    if (org.jeecg.common.util.CredentialGuard.isGenericWeakPassword(decryptedPassword)) {
      return Result.error(I18nUtil.getMessage("sysUser.passwordFormatIncorrect"));
    }

    SysUser sysUser = new SysUser();

    sysUser.setId(u.getId());
    sysUser.setPassword(decryptedPassword);
    // update-begin---author:wangshuai ---date:20220316
    // for: [VUEN-234] Password change adds sensitive audit log ------------
    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
    baseCommonService.addLog(
        "UpdateUser " + sysUser.getUsername() + " 's password, operator: " + loginUser.getUsername(),
        CommonConstant.LOG_TYPE_2,
        2);
    // update-end---author:wangshuai ---date:20220316
    // for: [VUEN-234] Password change adds sensitive audit log ------------
    Result result = sysUserService.changePassword(sysUser);

    if (result.isSuccess()) {
      try {
        sessionKickService.kickAllDevices(u.getId(), "admin_reset_pwd");
      } catch (Exception ignore) {
      }
    }

    return result;
  }

  /**
   * Query user-department association for the given user
   *
   * @param userId
   * @return
   */
  @PostMapping(value = "/userDepartList")
  @SaCheckPermission("system:user:userDepartList")
  @AutoLog("Query data associated with specified user and department")
  public Result<List<DepartIdModel>> getUserDepartsList(
      @RequestParam(name = "userId", required = true) String userId) {
    Result<List<DepartIdModel>> result = new Result<>();
    try {
      List<DepartIdModel> depIdModelList = this.sysUserDepartService.queryDepartIdsOfUser(userId);
      if (depIdModelList != null && depIdModelList.size() > 0) {
        result.setSuccess(true);
        result.setMessage(I18nUtil.getMessage("sysUser.findSuccess"));
        result.setResult(depIdModelList);
      } else {
        result.setSuccess(false);
        result.setMessage(I18nUtil.getMessage("sysUser.findFailed"));
      }
      return result;
    } catch (Exception e) {
      log.error("operation failed", e);
      result.setSuccess(false);
      result.setMessage(
          I18nUtil.getMessage("sysUser.findException", new Object[] { e.getMessage() }));
      return result;
    }
  }

  /**
   * Fix missing primary key when adding user; return id to frontend for department binding
   *
   * @return
   */
  @RequestMapping(value = "/generateUserId", method = RequestMethod.POST)
  @SaCheckPermission("system:user:generateUserId")
  @AutoLog("Generate user ID")
  public Result<String> generateUserId() {
    Result<String> result = new Result<>();
    log.debug("Executed, generating user ID==============================");
    String userId = UUID.randomUUID().toString().replace("-", "");
    result.setSuccess(true);
    result.setResult(userId);
    return result;
  }

  /**
   * ByDepartmentidQueryUserInfo
   *
   * @param id
   * @return
   */
  @PostMapping(value = "/queryUserByDepId")
  @SaCheckPermission("system:user:queryUserByDepId")
  @AutoLog("ByDepartmentidQueryUserInfo")
  public Result<List<SysUser>> queryUserByDepId(
      @RequestParam(name = "id", required = true) String id,
      @RequestParam(name = "realname", required = false) String realname) {
    Result<List<SysUser>> result = new Result<>();
    SysDepart sysDepart = sysDepartService.getById(id);
    List<SysUser> userList = sysUserDepartService.queryUserByDepCode(sysDepart.getOrgCode(), realname);

    // Batch query user departments
    List<String> userIds = userList.stream().map(SysUser::getId).collect(Collectors.toList());
    if (userIds != null && userIds.size() > 0) {
      Map<String, String> useDepNames = sysUserService.getDepNamesByUserIds(userIds);
      userList.forEach(
          item -> {
            item.setOrgCodeTxt(useDepNames.get(item.getId()));
          });
    }

    // P0: strip sensitive columns before returning to frontend.
    userList.forEach(SysUser::clearSensitiveForResponse);

    try {
      result.setSuccess(true);
      result.setResult(userList);
      return result;
    } catch (Exception e) {
      log.error("operation failed", e);
      result.setSuccess(false);
      return result;
    }
  }

  /**
   * User picker component: paginated query by username or department
   *
   * @param departId
   * @param username
   * @return
   */
  @PostMapping(value = "/queryUserComponentData")
  @AutoLog("User selector component - Dedicated paginated query by user account or department")
  @SaCheckPermission("system:user:queryUserComponentData")
  public Result<IPage<SysUser>> queryUserComponentData(
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      @RequestParam(name = "departId", required = false) String departId,
      @RequestParam(name = "realname", required = false) String realname,
      @RequestParam(name = "username", required = false) String username,
      @RequestParam(name = "isMultiTranslate", required = false) String isMultiTranslate,
      @RequestParam(name = "id", required = false) String id) {
    // SQL injection guards on user-supplied dims
    String[] arr = new String[] { departId, realname, username, id };
    SqlInjectionUtil.filterContent(arr, SymbolConstant.SINGLE_QUOTATION_MARK);
    IPage<SysUser> pageList = sysUserDepartService.queryDepartUserPageList(
        departId, username, realname, pageSize, pageNo, id, isMultiTranslate);
    return Result.OK(pageList);
  }

  /**
   * Export excel
   *
   * @param request
   * @param sysUser
   */
  @SaCheckPermission("system:user:export")
  @AutoLog("Export user data")
  @RequestMapping(value = "/exportXls", method = RequestMethod.POST)
  public ModelAndView exportXls(SysUser sysUser, HttpServletRequest request) {
    // Step.1 Build query conditions
    QueryWrapper<SysUser> queryWrapper = QueryGenerator.initQueryWrapper(sysUser, request.getParameterMap());
    // Step.2 Export Excel via AutoPoi
    ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
    // update-begin--Author:kangxiaolin Date:20180825
    // for: [03] User export: if data is selected, only export selected data --------------------
    String selections = request.getParameter("selections");
    if (!oConvertUtils.isEmpty(selections)) {
      queryWrapper.in("id", selections.split(","));
    }
    // update-end--Author:kangxiaolin Date:20180825
    // for: [03] User export: if data is selected, only export selected data ----------------------
    List<SysUser> pageList = sysUserService.list(queryWrapper);

    // Export file name
    mv.addObject(NormalExcelConstants.FILE_NAME, "UserList");
    mv.addObject(NormalExcelConstants.CLASS, SysUser.class);
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    ExportParams exportParams = new ExportParams("UserListData", "Exported by:" + user.getRealname(), "Export information");
    exportParams.setImageBasePath(upLoadPath);
    mv.addObject(NormalExcelConstants.PARAMS, exportParams);
    mv.addObject(NormalExcelConstants.DATA_LIST, pageList);
    return mv;
  }

  /**
   * Import data through excel
   *
   * @param request
   * @param response
   * @return
   */
  @SaCheckPermission("system:user:import")
  @AutoLog("Import user data")
  @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
  public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
    Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
    // Error messages
    List<String> errorMessage = new ArrayList<>();
    int successLines = 0, errorLines = 0;
    for (Map.Entry<String, MultipartFile> entity : fileMap.entrySet()) {
      MultipartFile file = entity.getValue(); // Get uploaded file object
      ImportParams params = new ImportParams();
      params.setTitleRows(2);
      params.setHeadRows(1);
      params.setNeedSave(true);
      try {
        List<SysUser> listSysUsers = ExcelImportUtil.importExcel(file.getInputStream(), SysUser.class, params);
        for (int i = 0; i < listSysUsers.size(); i++) {
          SysUser sysUserExcel = listSysUsers.get(i);
          if (StringUtils.isBlank(sysUserExcel.getPassword())) {
            errorLines++;
            errorMessage.add("Row " + (i + 1) + ": password must be set in the import file, default 123456 bootstrap password blocked (B5-3).");
            continue;
          }
          if (org.jeecg.common.util.CredentialGuard.isGenericWeakPassword(sysUserExcel.getPassword())) {
            errorLines++;
            errorMessage.add("Row " + (i + 1) + ": password matches weak blocklist, import rejected (B5-3).");
            continue;
          }
          // Hash password with salt
          String salt = oConvertUtils.randomGen(8);
          sysUserExcel.setSalt(salt);
          String passwordEncode = org.jeecg.common.util.crypto.SmCryptoUtil.hashPassword(sysUserExcel.getPassword());
          sysUserExcel.setPassword(passwordEncode);
          try {
            sysUserService.save(sysUserExcel);
            successLines++;
          } catch (Exception e) {
            errorLines++;
            String message = e.getMessage().toLowerCase();
            int lineNumber = i + 1;
            // Determine error message by index name
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
          // Batch link departments and users
          String departIds = "";
          if (StringUtils.isNotBlank(departIds)) {
            String userId = sysUserExcel.getId();
            String[] departIdArray = departIds.split(",");
            List<SysUserDepart> userDepartList = new ArrayList<>(departIdArray.length);
            for (String departId : departIdArray) {
              userDepartList.add(new SysUserDepart(userId, departId));
            }
            sysUserDepartService.saveBatch(userDepartList);
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
    return ImportExcelUtil.imporReturnRes(errorLines, successLines, errorMessage);
  }

  /**
   * @param userIds
   * @return @Function: batch query by id
   */
  @RequestMapping(value = "/queryByIds", method = RequestMethod.POST)
  @SaCheckPermission("system:user:queryByIds")
  @AutoLog("Batch query by id")
  public Result<Collection<SysUser>> queryByIds(@RequestParam(name = "userIds") String userIds) {
    Result<Collection<SysUser>> result = new Result<>();
    String[] userId = userIds.split(",");
    Collection<String> idList = Arrays.asList(userId);
    Collection<SysUser> userRole = sysUserService.listByIds(idList);
    result.setSuccess(true);
    result.setResult(userRole);
    return result;
  }

  /**
   * @param userNames
   * @return @Function: batch query by id
   */
  @RequestMapping(value = "/queryByNames", method = RequestMethod.POST)
  @SaCheckPermission("system:user:queryByNames")
  @AutoLog("Batch query by id")
  public Result<Collection<SysUser>> queryByNames(
      @RequestParam(name = "userNames") String userNames) {
    Result<Collection<SysUser>> result = new Result<>();
    String[] names = userNames.split(",");
    QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
    queryWrapper.lambda().in(true, SysUser::getUsername, names);
    Collection<SysUser> userRole = sysUserService.list(queryWrapper);
    result.setSuccess(true);
    result.setResult(userRole);
    return result;
  }

  /** Home page user password reset */
  @SneakyThrows
  @RequestMapping(value = "/updatePassword", method = RequestMethod.POST)
  @AutoLog("Change password")
  public Result<?> updatePassword(
      @RequestParam String username,
      @RequestParam String oldpassword,
      @RequestParam String password,
      @RequestParam String confirmpassword,
      @RequestParam(required = false) String rsaCacheKey,
      HttpServletRequest request) {
    Locale locale = LocaleResolver.getCurrLocale();

    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    if (!sysUser.getUsername().equals(username)) {

      log.info("Logged-in user {} does not match the account for password change: {}", sysUser.getUsername(), username);
      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }
    SysUser user = this.sysUserService.getOne(
        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    if (user == null) {
      log.info("User{}does not exist", username);
      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }
    // update-begin---author:wangshuai ---date:20220316
    // for: [VUEN-234] Password change adds sensitive audit log ------------
    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
    baseCommonService.addLog(
        "Change password, username: " + loginUser.getUsername(), CommonConstant.LOG_TYPE_2, 2);
    // update-end---author:wangshuai ---date:20220316
    // for: [VUEN-234] Password change adds sensitive audit log ------------

    // If RSA cache key provided, password is encrypted and must be decrypted
    String decryptedOldPassword = oldpassword;
    String decryptedPassword = password;
    String decryptedConfirmPassword = confirmpassword;

    if (StrUtil.isNotBlank(rsaCacheKey)) {
      decryptedOldPassword = rsaPasswordUtil.decryptPassword(rsaCacheKey, oldpassword);
      if (decryptedOldPassword == null) {
        log.warn("Change password failed, RSA old password decryption failed: {}", username);
        return Result.error(
            messageSource.getMessage("common.authentication_failed_msg", null, locale));
      }

      // New and confirm password use same private key to decrypt; frontend usually encrypts separately
      // Assume same cacheKey; front-end must pass multiple cacheKeys if different
      decryptedPassword = rsaPasswordUtil.decryptPassword(rsaCacheKey, password);
      // Delete private key on final decryption
      decryptedConfirmPassword = rsaPasswordUtil.decryptPasswordAndClean(rsaCacheKey, confirmpassword);

      if (decryptedPassword == null || decryptedConfirmPassword == null) {
        log.warn("Change password failed, RSA new password decryption failed: {}", username);
        return Result.error(
            messageSource.getMessage("common.authentication_failed_msg", null, locale));
      }
    }

    // Validate password format
    if (StrUtil.isNotBlank(decryptedPassword)
        && !decryptedPassword.matches(CommonConstant.PASSWORD_REGEX)) {
      log.warn("Change password failed, invalid password format: {}", username);
      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    if (org.jeecg.common.util.CredentialGuard.isGenericWeakPassword(decryptedPassword)) {
      log.warn("Change password failed, password matches weak blocklist: {}", username);
      return Result.error(
          messageSource.getMessage("common.weak_password", null, "Password too weak.", locale));
    }

    // B5-2: if user has TOTP MFA enabled, they must re-verify a freshly-generated TOTP before changing
    // their password (session may have been hijacked).
    if (user != null) {
      java.util.Optional<org.jeecg.modules.system.entity.SysUserMfa> totpMfa =
          sysUserMfaService.findEnabledTotp(user.getId(), 0);
      if (totpMfa.isPresent()) {
        String mfaCode = request.getParameter("mfa");
        if (StrUtil.isBlank(mfaCode)) {
          log.warn("Change password failed: TOTP required user={}", username);
          return Result.error(
              messageSource.getMessage("mfa.required", null, "MFA code required.", locale));
        }
        String secret = mfaSeedCryptoService.tryDecryptWithAesThenMigrate(totpMfa.get());
        if (StrUtil.isBlank(secret)) {
          String seed = sysUserMfaService.decryptSeed(totpMfa.get());
          if (StrUtil.isNotBlank(seed)) {
            secret = seed;
          }
        }
        if (StrUtil.isBlank(secret)) {
          log.warn("Change password blocked: TOTP secret missing user={}", username);
          return Result.error(
              messageSource.getMessage("common.authentication_failed_msg", null, locale));
        }
        Integer otp;
        try {
          otp = Integer.parseInt(StrUtil.trim(mfaCode));
        } catch (Exception ex) {
          log.warn("Change password failed: TOTP format user={}", username);
          return Result.error(
              messageSource.getMessage("common.authentication_failed_msg", null, locale));
        }
        com.warrenstrange.googleauth.GoogleAuthenticator ga = new com.warrenstrange.googleauth.GoogleAuthenticator();
        boolean ok = ga.authorize(secret, otp);
        if (!ok) {
          log.warn("Change password failed: TOTP mismatch user={}", username);
          return Result.error(
              messageSource.getMessage("common.authentication_failed_msg", null, locale));
        }
      }
    }

    Result result = sysUserService.resetPassword(
        username, decryptedOldPassword, decryptedPassword, decryptedConfirmPassword);

    return result;
  }

  @RequestMapping(value = "/userRoleList", method = RequestMethod.POST)
  @SaCheckPermission("system:user:userRoleList")
  @AutoLog("QueryUserRole")
  public Result<IPage<SysUser>> userRoleList(
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<IPage<SysUser>> result = new Result<IPage<SysUser>>();
    Page<SysUser> page = new Page<SysUser>(pageNo, pageSize);
    String roleId = req.getParameter("roleId");
    String username = req.getParameter("username");
    IPage<SysUser> pageList = sysUserService.getUserByRoleId(page, roleId, username);
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  /**
   * Add users to the specified role
   *
   * @param
   * @return
   */
  @SaCheckPermission("system:user:addUserRole")
  @AutoLog("Add users to specified role")
  @RequestMapping(value = "/addSysUserRole", method = RequestMethod.POST)
  public Result<String> addSysUserRole(@RequestBody SysUserRoleVO sysUserRoleVO) {
    Result<String> result = new Result<String>();
    // TODO Check whether the current role belongs to the logged-in tenant
    try {
      String sysRoleId = sysUserRoleVO.getRoleId();

      // Permission check: operations_admin may only configure roles other than operations_admin and admin
      List<String> currentUserRoles = StpUtil.getRoleList();
      if (currentUserRoles != null
          && currentUserRoles.contains(SecurityRoleConstants.ROLE_OPS_ADMIN)
          && !currentUserRoles.contains("admin")) {
        // Get role info to operate on
        SysRole targetRole = sysRoleService.getById(sysRoleId);
        if (targetRole != null) {
          String targetRoleCode = targetRole.getRoleCode();
          if (RoleConfigUtil.isConfiguredAdminRole(commonAPI, targetRoleCode)) {
            result.setSuccess(false);
            result.setMessage(I18nUtil.getMessage("sysUser.operationsAdminRoleRestricted"));
            return result;
          }
        }
      }

      for (String sysUserId : sysUserRoleVO.getUserIdList()) {
        SysUserRole sysUserRole = new SysUserRole(sysUserId, sysRoleId);
        QueryWrapper<SysUserRole> queryWrapper = new QueryWrapper<SysUserRole>();
        queryWrapper.eq("role_id", sysRoleId).eq("user_id", sysUserId);
        SysUserRole one = sysUserRoleService.getOne(queryWrapper);
        if (one == null) {
          sysUserRoleService.save(sysUserRole);
        }
      }
      result.setMessage(I18nUtil.getMessage("sysUser.addSuccess"));
      result.setSuccess(true);
      return result;
    } catch (Exception e) {
      log.error("operation failed", e);
      result.setSuccess(false);
      result.setMessage(
          I18nUtil.getMessage("sysUser.operationError", new Object[] { e.getMessage() }));
      return result;
    }
  }

  /**
   * Delete user relations for specified role
   *
   * @param
   * @return
   */
  @SaCheckPermission("system:user:deleteRole")
  @AutoLog("Delete user relationship for the specified role")
  @RequestMapping(value = "/deleteUserRole", method = RequestMethod.DELETE)
  public Result<SysUserRole> deleteUserRole(
      @RequestParam(name = "roleId") String roleId,
      @RequestParam(name = "userId", required = true) String userId) {
    Result<SysUserRole> result = new Result<SysUserRole>();
    try {
      QueryWrapper<SysUserRole> queryWrapper = new QueryWrapper<SysUserRole>();
      queryWrapper.eq("role_id", roleId).eq("user_id", userId);
      sysUserRoleService.remove(queryWrapper);
      result.success(I18nUtil.getMessage("sysUser.deleteSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.deleteFailed"));
    }
    return result;
  }

  /**
   * Batch delete user relations for specified role
   *
   * @param
   * @return
   */
  @SaCheckPermission("system:user:deleteRoleBatch")
  @AutoLog("Batch delete user relations for specified roles")
  @RequestMapping(value = "/deleteUserRoleBatch", method = RequestMethod.DELETE)
  public Result<SysUserRole> deleteUserRoleBatch(
      @RequestParam(name = "roleId") String roleId,
      @RequestParam(name = "userIds", required = true) String userIds) {
    Result<SysUserRole> result = new Result<SysUserRole>();
    try {
      QueryWrapper<SysUserRole> queryWrapper = new QueryWrapper<SysUserRole>();
      queryWrapper.eq("role_id", roleId).in("user_id", Arrays.asList(userIds.split(",")));
      sysUserRoleService.remove(queryWrapper);
      result.success(I18nUtil.getMessage("sysUser.deleteSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.deleteFailed"));
    }
    return result;
  }

  /** Add users to the specified department */
  @SaCheckPermission("system:user:editDepartWithUser")
  @AutoLog("Add corresponding users to specified department")
  @RequestMapping(value = "/editSysDepartWithUser", method = RequestMethod.POST)
  public Result<String> editSysDepartWithUser(@RequestBody SysDepartUsersVO sysDepartUsersVO) {
    Result<String> result = new Result<String>();
    try {
      String sysDepId = sysDepartUsersVO.getDepId();
      for (String sysUserId : sysDepartUsersVO.getUserIdList()) {
        SysUserDepart sysUserDepart = new SysUserDepart(null, sysUserId, sysDepId);
        QueryWrapper<SysUserDepart> queryWrapper = new QueryWrapper<SysUserDepart>();
        queryWrapper.eq("dep_id", sysDepId).eq("user_id", sysUserId);
        SysUserDepart one = sysUserDepartService.getOne(queryWrapper);
        if (one == null) {
          sysUserDepartService.save(sysUserDepart);
        }
      }
      result.setMessage(I18nUtil.getMessage("sysUser.addSuccess"));
      result.setSuccess(true);
      return result;
    } catch (Exception e) {
      log.error("operation failed", e);
      result.setSuccess(false);
      result.setMessage(
          I18nUtil.getMessage("sysUser.operationError", new Object[] { e.getMessage() }));
      return result;
    }
  }

  /** Delete user relations for the specified organization */
  @SaCheckPermission("system:user:deleteUserInDepart")
  @AutoLog("Delete user relationship for the specified organization")
  @RequestMapping(value = "/deleteUserInDepart", method = RequestMethod.DELETE)
  public Result<SysUserDepart> deleteUserInDepart(
      @RequestParam(name = "depId") String depId,
      @RequestParam(name = "userId", required = true) String userId) {
    Result<SysUserDepart> result = new Result<SysUserDepart>();
    try {
      QueryWrapper<SysUserDepart> queryWrapper = new QueryWrapper<SysUserDepart>();
      queryWrapper.eq("dep_id", depId).eq("user_id", userId);
      boolean b = sysUserDepartService.remove(queryWrapper);
      if (b) {
        result.success(I18nUtil.getMessage("sysUser.deleteSuccess"));
      } else {
        result.error500(I18nUtil.getMessage("sysUser.departUserNoRelation"));
      }
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.deleteFailed"));
    }
    return result;
  }

  /** Batch delete user relations for the specified organization */
  @SaCheckPermission("system:user:deleteUserInDepartBatch")
  @AutoLog("Batch delete user relations for specified organizations")
  @RequestMapping(value = "/deleteUserInDepartBatch", method = RequestMethod.DELETE)
  public Result<SysUserDepart> deleteUserInDepartBatch(
      @RequestParam(name = "depId") String depId,
      @RequestParam(name = "userIds", required = true) String userIds) {
    Result<SysUserDepart> result = new Result<SysUserDepart>();
    try {
      QueryWrapper<SysUserDepart> queryWrapper = new QueryWrapper<SysUserDepart>();
      queryWrapper.eq("dep_id", depId).in("user_id", Arrays.asList(userIds.split(",")));
      sysUserDepartService.remove(queryWrapper);
      result.success(I18nUtil.getMessage("sysUser.deleteSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.deleteFailed"));
    }
    return result;
  }

  /**
 * User registration API
 *
 * @param jsonObject
 * @param user
 * @return*/
  @PostMapping("/register")
  @AutoLog("User registration")
  public Result<JSONObject> userRegister(@RequestBody JSONObject jsonObject, SysUser user) {
    if (!registerEnabled) {
      Result<JSONObject> disabled = new Result<>();
      disabled.setSuccess(false);
      disabled.setMessage(I18nUtil.getMessage("loginController.registerByEmail.accessByRequestOnly"));
      return disabled;
    }
    String phone = jsonObject == null ? null : jsonObject.getString("phone");
    String normalizedPhone = normalizePhone(phone);
    if (normalizedPhone.isEmpty()
        || !registerWhitelistPhonesNormalized.contains(normalizedPhone)) {
      if (log.isInfoEnabled()) {
        log.info("Registration request rejected: whitelist miss. phone_digest={}", truncateAndHash(normalizedPhone));
      }
      Result<JSONObject> disabled = new Result<>();
      disabled.setSuccess(false);
      disabled.setMessage(I18nUtil.getMessage("loginController.registerByEmail.accessByRequestOnly"));
      return disabled;
    }
    return userRegisterLegacy(jsonObject, user);
  }

  private static String truncateAndHash(String s) {
    if (s == null || s.isEmpty()) {
      return "";
    }
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(16);
      for (int i = 0; i < 6 && i < d.length; i++) {
        sb.append(String.format("%02x", d[i]));
      }
      return sb.toString();
    } catch (Exception ignore) {
      return String.valueOf(s.length());
    }
  }

  /** Legacy registration implementation retained for possible feature-flag re-enable; currently unused. */
  @SuppressWarnings("unused")
  private Result<JSONObject> userRegisterLegacy(JSONObject jsonObject, SysUser user) {
    Result<JSONObject> result = new Result<JSONObject>();
    String phone = jsonObject.getString("phone");
    String smscode = jsonObject.getString("smscode");

    // update-begin-author:taoyan date:2022-9-13 for: VUEN-2245 [Vulnerability] New vulnerability pending fix 20220906
    String redisKey = CommonConstant.PHONE_REDIS_KEY_PRE + phone;
    Object code = redisUtil.get(redisKey);
    // update-end-author:taoyan date:2022-9-13 for: VUEN-2245 [Vulnerability] New vulnerability pending fix 20220906

    String username = jsonObject.getString("username");
    // Use phone as username if not set
    if (oConvertUtils.isEmpty(username)) {
      username = phone;
    }
    // Random password if not set
    String password = jsonObject.getString("password");
    String rsaCacheKey = jsonObject.getString("rsaCacheKey");

    // If RSA cache key provided, password is encrypted and must be decrypted
    if (StrUtil.isNotBlank(rsaCacheKey) && StrUtil.isNotBlank(password)) {
      String decryptedPassword = rsaPasswordUtil.decryptPassword(rsaCacheKey, password);
      if (decryptedPassword == null) {
        result.setMessage(I18nUtil.getMessage("sysUser.passwordDecryptFailedMsg"));
        result.setSuccess(false);
        return result;
      }
      password = decryptedPassword;
      // Delete private key after successful decryption
      rsaPasswordUtil.decryptPasswordAndClean(rsaCacheKey, password);
    }

    if (oConvertUtils.isEmpty(password)) {
      password = RandomUtil.randomString(8);
    }
    String email = jsonObject.getString("email");
    SysUser sysUser1 = sysUserService.getUserByName(username);
    if (sysUser1 != null) {
      result.setMessage(I18nUtil.getMessage("sysUser.usernameRegistered"));
      result.setSuccess(false);
      return result;
    }
    SysUser sysUser2 = sysUserService.getUserByPhone(phone);
    if (sysUser2 != null) {
      result.setMessage(I18nUtil.getMessage("sysUser.phoneRegistered"));
      result.setSuccess(false);
      return result;
    }

    if (oConvertUtils.isNotEmpty(email)) {
      SysUser sysUser3 = sysUserService.getUserByEmail(email);
      if (sysUser3 != null) {
        result.setMessage(I18nUtil.getMessage("sysUser.emailRegistered"));
        result.setSuccess(false);
        return result;
      }
    }
    if (null == code) {
      result.setMessage(I18nUtil.getMessage("sysUser.phoneCodeExpired"));
      result.setSuccess(false);
      return result;
    }
    if (!smscode.equals(code.toString())) {
      result.setMessage(I18nUtil.getMessage("sysUser.phoneCodeError"));
      result.setSuccess(false);
      return result;
    }

    String realname = jsonObject.getString("realname");
    if (oConvertUtils.isEmpty(realname)) {
      realname = username;
    }

    try {
      user.setCreateTime(new Date()); // Set create time
      String salt = oConvertUtils.randomGen(8);
      String passwordEncode = org.jeecg.common.util.crypto.SmCryptoUtil.hashPassword(password);
      user.setSalt(salt);
      user.setUsername(username);
      user.setRealname(realname);
      user.setPassword(passwordEncode);
      user.setEmail(email);
      user.setPhone(phone);
      user.setStatus(CommonConstant.USER_UNFREEZE);
      user.setDelFlag(CommonConstant.DEL_FLAG_0);
      user.setActivitiSync(CommonConstant.ACT_SYNC_1);
      sysUserService.addUserWithRole(user, ""); // Default temporary role test
      result.success(I18nUtil.getMessage("sysUser.registerSuccess"));
    } catch (Exception e) {
      result.error500(I18nUtil.getMessage("sysUser.registerFailed"));
    }
    return result;
  }

  // /**
  // * Query user info by username or mobile number
  // * @param
  // * @return
  // */
  // @RequestMapping("/querySysUser")
  // public Result<Map<String, Object>> querySysUser(SysUser sysUser) {
  // String phone = sysUser.getPhone();
  // String username = sysUser.getUsername();
  // Result<Map<String, Object>> result = new Result<Map<String, Object>>();
  // Map<String, Object> map = new HashMap<String, Object>();
  // if (oConvertUtils.isNotEmpty(phone)) {
  // SysUser user = sysUserService.getUserByPhone(phone);
  // if(user!=null) {
  // map.put("username",user.getUsername());
  // map.put("phone",user.getPhone());
  // result.setSuccess(true);
  // result.setResult(map);
  // return result;
  // }
  // }
  // if (oConvertUtils.isNotEmpty(username)) {
  // SysUser user = sysUserService.getUserByName(username);
  // if(user!=null) {
  // map.put("username",user.getUsername());
  // map.put("phone",user.getPhone());
  // result.setSuccess(true);
  // result.setResult(map);
  // return result;
  // }
  // }
  // result.setSuccess(false);
  // result.setMessage("VerifyFailed");
  // return result;
  // }

  /** User mobile number verification */
  @PostMapping("/phoneVerification")
  @AutoLog("User mobile number verification")
  public Result<Map<String, String>> phoneVerification(@RequestBody JSONObject jsonObject) {
    Result<Map<String, String>> result = new Result<Map<String, String>>();
    String phone = jsonObject.getString("phone");
    String smscode = jsonObject.getString("smscode");
    // update-begin-author:taoyan date:2022-9-13 for: VUEN-2245 [Vulnerability] New vulnerability pending fix 20220906
    String redisKey = CommonConstant.PHONE_REDIS_KEY_PRE + phone;
    Object code = redisUtil.get(redisKey);
    if (!smscode.equals(code)) {
      result.setMessage(I18nUtil.getMessage("sysUser.phoneCodeError"));
      result.setSuccess(false);
      return result;
    }
    // Set validity period
    redisUtil.set(redisKey, smscode, 600);
    // update-end-author:taoyan date:2022-9-13 for: VUEN-2245 [Vulnerability] New vulnerability pending fix 20220906

    // Added query by username
    LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
    query.eq(SysUser::getPhone, phone);
    SysUser user = sysUserService.getOne(query);
    Map<String, String> map = new HashMap(5);
    map.put("smscode", smscode);
    if (null == user) {
      // Front-end uses message text to judge user existence; do not change
      result.setMessage(I18nUtil.getMessage("sysUser.userInfoNotFound"));
      result.setSuccess(false);
      return result;
    }
    map.put("username", user.getUsername());
    result.setResult(map);
    result.setSuccess(true);
    return result;
  }

  /**
   * Get partial user info by TOKEN (data suitable for form designer)
   *
   * @return
   */
  @RequestMapping(value = "/getUserSectionInfoByToken", method = RequestMethod.POST)
  @AutoLog("Get partial user info by TOKEN")
  public Result<?> getUserSectionInfoByToken(
      HttpServletRequest request, @RequestParam(name = "token", required = false) String token) {
    try {
      // Check login status; NotLoginException is thrown when token is kicked off
      StpUtil.checkLogin();

      LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
      ;

      String username = loginUser.getUsername();

      log.debug(" ------ Fetch partial user info via token, current user: " + username);

      // Query based on usernameUserInfo
      SysUser sysUser = sysUserService.getUserByName(username);
      Map<String, Object> map = new HashMap<String, Object>();
      map.put("sysUserId", sysUser.getId());
      map.put("sysUserCode", sysUser.getUsername()); // Current loginuser login account
      map.put("sysUserName", sysUser.getRealname()); // Current loginuser real name
      map.put("sysOrgCode", sysUser.getOrgCode()); // Current loginuser department code

      log.debug(" ------ Fetch partial user info via token, retrieved user info: " + map);

      return Result.ok(map);
    } catch (Exception e) {
      log.error("operation failed", e);
      return Result.error(500, I18nUtil.getMessage("sysUser.queryFailed", new Object[]{e.getMessage()}));
    }
  }

  /**
   * [APP API] Get user list; fuzzy match by username and real name
   *
   * @param keyword
   * @param pageNo
   * @param pageSize
   * @return
   */
  @RequestMapping(value = "/appUserList", method = RequestMethod.POST)
  @AutoLog("GetUserList")
  @SaCheckPermission("system:user:appUserList")
  public Result<?> appUserList(
      @RequestParam(name = "keyword", required = false) String keyword,
      @RequestParam(name = "username", required = false) String username,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      @RequestParam(name = "syncFlow", required = false) String syncFlow) {
    try {
      // TODO For query efficiency avoid mp Page wrapper; prefer custom pagination SQL
      LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>();
      if (oConvertUtils.isNotEmpty(syncFlow)) {
        query.eq(SysUser::getActivitiSync, CommonConstant.ACT_SYNC_1);
      }
      query.eq(SysUser::getDelFlag, CommonConstant.DEL_FLAG_0);
      if (oConvertUtils.isNotEmpty(username)) {
        if (username.contains(",")) {
          query.in(SysUser::getUsername, username.split(","));
        } else {
          query.eq(SysUser::getUsername, username);
        }
      } else {
        query.and(
            i -> i.like(SysUser::getUsername, keyword).or().like(SysUser::getRealname, keyword));
      }
      Page<SysUser> page = new Page<>(pageNo, pageSize);
      IPage<SysUser> res = this.sysUserService.page(page, query);
      return Result.ok(res);
    } catch (Exception e) {
      log.error("operation failed", e);
      return Result.error(500, I18nUtil.getMessage("sysUser.queryFailed", new Object[]{e.getMessage()}));
    }
  }

  /**
   * Get logically deleted users (no pagination)
   *
   * @return logicDeletedUserList
   */
  @RequestMapping(value = "/recycleBin", method = RequestMethod.POST)
  @AutoLog("Get logically deleted user list, no pagination")
  @SaCheckPermission("system:user:recycleBin")
  public Result getRecycleBin() {
    List<SysUser> logicDeletedUserList = sysUserService.queryLogicDeleted();
    if (logicDeletedUserList.size() > 0) {
      // Batch query user departments
      // step.1 get all userIds first
      List<String> userIds = logicDeletedUserList.stream().map(SysUser::getId).collect(Collectors.toList());
      // step.2 query users' department names in one batch by userIds
      Map<String, String> useDepNames = sysUserService.getDepNamesByUserIds(userIds);
      logicDeletedUserList.forEach(item -> item.setOrgCode(useDepNames.get(item.getId())));
    }
    return Result.ok(logicDeletedUserList);
  }

  /**
   * Restore soft-deleted users
   *
   * @param jsonObject
   * @return
   */
  @RequestMapping(value = "/putRecycleBin", method = RequestMethod.PUT)
  @SaCheckPermission("system:user:putRecycleBin")
  @AutoLog("Restore logically deleted user")
  public Result putRecycleBin(@RequestBody JSONObject jsonObject, HttpServletRequest request) {
    String userIds = jsonObject.getString("userIds");
    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
    if (StringUtils.isNotBlank(userIds)) {
      SysUser updateUser = new SysUser();
      updateUser.setUpdateBy(loginUser.getUsername());
      updateUser.setUpdateTime(new Date());
      sysUserService.revertLogicDeleted(Arrays.asList(userIds.split(",")), updateUser);
    }
    return Result.ok(I18nUtil.getMessage("sysUser.restoreSuccess"));
  }

  /**
   * Permanently delete users
   *
   * @param userIds user IDs to delete, comma-separated
   * @return
   */
  @SaCheckPermission("system:user:deleteRecycleBin")
  @AutoLog("Permanently delete user")
  @RequestMapping(value = "/deleteRecycleBin", method = RequestMethod.DELETE)
  public Result deleteRecycleBin(@RequestParam("userIds") String userIds) {
    if (StringUtils.isNotBlank(userIds)) {
      sysUserService.removeLogicDeleted(Arrays.asList(userIds.split(",")));
    }
    return Result.ok(I18nUtil.getMessage("sysUser.deleteSuccess"));
  }

  @SaCheckRole(value = {"admin"}, mode = SaMode.OR)
  @AutoLog("Reset user TOTP")
  @PostMapping("/mfa/totp/reset")
  public Result<?> resetUserTotp(@RequestParam("userId") String userId) {
    Locale locale = LocaleResolver.getCurrLocale();

    SysUser sysUser = sysUserService.getById(userId);
    if (sysUser == null) {
      return Result.error(messageSource.getMessage("common.user_not_exist", null, locale));
    }

    SysUserMfa mfa = sysUserMfaService.findAnyTotp(userId, 0).orElse(null);
    if (mfa != null) {
      Date now = new Date();
      LoginUser operator = (LoginUser) StpUtil.getSession().get("loginUser");
      String operatorName = operator != null ? operator.getUsername() : "system";
      mfa.setEnabled(0);
      mfa.setSecretEnc(null);
      mfa.setVerifiedAt(null);
      mfa.setLastUsedAt(null);
      mfa.setFailCount(0);
      mfa.setLockedUntil(null);
      mfa.setDelFlag(0);
      mfa.setUpdateBy(operatorName);
      mfa.setUpdateTime(now);
      sysUserMfaService.updateById(mfa);
    }

    baseCommonService.addLog("Reset user TOTP, userId: " + userId, CommonConstant.LOG_TYPE_2, 2);
    return Result.OK(I18nUtil.getMessage("common.success.operation"));
  }

  /**
   * Update user info on mobile
   *
   * @param jsonObject
   * @return
   */
  @SaCheckRole(value = { "admin" }, mode = SaMode.OR)
  @AutoLog("Edit user on mobile")
  @RequestMapping(value = "/appEdit", method = { RequestMethod.PUT, RequestMethod.POST })
  public Result<SysUser> appEdit(HttpServletRequest request, @RequestBody JSONObject jsonObject) {
    Result<SysUser> result = new Result<SysUser>();
    try {
      LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
      String username = loginUser.getUsername();
      SysUser sysUser = sysUserService.getUserByName(username);
      baseCommonService.addLog(
          "Edit user on mobile, id: " + jsonObject.getString("id"), CommonConstant.LOG_TYPE_2, 2);
      String realname = jsonObject.getString("realname");
      String avatar = jsonObject.getString("avatar");
      String sex = jsonObject.getString("sex");
      String phone = jsonObject.getString("phone");
      String email = jsonObject.getString("email");
      Date birthday = jsonObject.getDate("birthday");
      SysUser userPhone = sysUserService.getUserByPhone(phone);
      if (sysUser == null) {
        result.error500(I18nUtil.getMessage("sysUser.userDataNotFound"));
      } else {
        if (userPhone != null) {
          String userPhonename = userPhone.getUsername();
          if (!userPhonename.equals(username)) {
            result.error500(I18nUtil.getMessage("sysUser.phoneExists"));
            return result;
          }
        }
        if (StringUtils.isNotBlank(realname)) {
          sysUser.setRealname(realname);
        }
        if (StringUtils.isNotBlank(avatar)) {
          sysUser.setAvatar(avatar);
        }
        if (StringUtils.isNotBlank(sex)) {
          sysUser.setSex(Integer.parseInt(sex));
        }
        if (StringUtils.isNotBlank(phone)) {
          sysUser.setPhone(phone);
        }
        if (StringUtils.isNotBlank(email)) {
          // update-begin---author:wangshuai ---date:20220708
          // for: [VUEN-1528] JiMu official site duplicate email should show accurate message ------------
          LambdaQueryWrapper<SysUser> emailQuery = new LambdaQueryWrapper<>();
          emailQuery.eq(SysUser::getEmail, email);
          long count = sysUserService.count(emailQuery);
          if (!email.equals(sysUser.getEmail()) && count != 0) {
            result.error500(I18nUtil.getMessage("sysUser.saveFailedEmailExists"));
            return result;
          }
          // update-end---author:wangshuai ---date:20220708
          // for: [VUEN-1528] JiMu official site duplicate email should show accurate message --------------
          sysUser.setEmail(email);
        }
        if (null != birthday) {
          sysUser.setBirthday(birthday);
        }
        sysUser.setUpdateTime(new Date());
        sysUserService.updateById(sysUser);
      }
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.saveFailed"));
    }
    return result;
  }

  /**
   * Query user info on mobile (fuzzy search by username)
   *
   * @param keyword
   * @return
   */
  @RequestMapping(value = "/appQueryUser", method = RequestMethod.POST)
  @AutoLog("QueryUserInfo")
  @SaCheckPermission("system:user:appQueryUser")
  public Result<List<SysUser>> appQueryUser(
      @RequestParam(name = "keyword", required = false) String keyword,
      @RequestParam(name = "username", required = false) String username,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest request) {
    Result<List<SysUser>> result = new Result<List<SysUser>>();
    LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<SysUser>();
    // External mock-login temp accounts hidden from list
    queryWrapper.ne(SysUser::getUsername, "_reserve_user_external");
    // Add username parameter
    if (oConvertUtils.isNotEmpty(username)) {
      if (username.contains(",")) {
        queryWrapper.in(SysUser::getUsername, username.split(","));
      } else {
        queryWrapper.eq(SysUser::getUsername, username);
      }
    } else if (StringUtils.isNotBlank(keyword)) {
      queryWrapper.and(
          i -> i.like(SysUser::getUsername, keyword).or().like(SysUser::getRealname, keyword));
    }

    Page<SysUser> page = new Page<>(pageNo, pageSize);
    IPage<SysUser> pageList = this.sysUserService.page(page, queryWrapper);
    // Batch query user departments
    List<String> userIds = pageList.getRecords().stream().map(SysUser::getId).collect(Collectors.toList());
    if (userIds != null && userIds.size() > 0) {
      Map<String, String> useDepNames = sysUserService.getDepNamesByUserIds(userIds);
      pageList
          .getRecords()
          .forEach(
              item -> {
                item.setOrgCodeTxt(useDepNames.get(item.getId()));
              });
    }
    result.setResult(pageList.getRecords());
    return result;
  }

  /**
   * Update phone by username [unused method]
   *
   * @param json
   * @return
   */
  @RequestMapping(value = "/updateMobile", method = RequestMethod.PUT)
  @SaCheckPermission("system:user:updateMobile")
  @AutoLog("Change mobile number")
  public Result<?> changMobile(@RequestBody JSONObject json, HttpServletRequest request) {
    String smscode = json.getString("smscode");
    String phone = json.getString("phone");
    Result<SysUser> result = new Result<SysUser>();

    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
    // Get login user name
    String username = loginUser.getUsername();
    if (oConvertUtils.isEmpty(username)
        || oConvertUtils.isEmpty(smscode)
        || oConvertUtils.isEmpty(phone)) {
      result.setMessage(I18nUtil.getMessage("sysUser.modifyPhoneFailed"));
      result.setSuccess(false);
      return result;
    }
    // update-begin-author:taoyan date:2022-9-13 for: VUEN-2245 [Vulnerability] New vulnerability pending fix 20220906
    String redisKey = CommonConstant.PHONE_REDIS_KEY_PRE + phone;
    Object object = redisUtil.get(redisKey);
    // update-end-author:taoyan date:2022-9-13 for: VUEN-2245 [Vulnerability] New vulnerability pending fix 20220906
    if (null == object) {
      result.setMessage(I18nUtil.getMessage("sysUser.smsCodeExpired"));
      result.setSuccess(false);
      return result;
    }
    if (!smscode.equals(object.toString())) {
      result.setMessage(I18nUtil.getMessage("sysUser.smsCodeMismatch"));
      result.setSuccess(false);
      return result;
    }
    SysUser user = sysUserService.getUserByName(username);
    if (user == null) {
      return Result.error(I18nUtil.getMessage("sysUser.userNotFound"));
    }
    user.setPhone(phone);
    sysUserService.updateById(user);
    return Result.ok(I18nUtil.getMessage("sysUser.phoneSetSuccess"));
  }

  /**
   * IN query on object property values; property may vary; used by user component
   *
   * @param sysUser
   * @return
   */
  @RequestMapping(value = "/getMultiUser", method = RequestMethod.POST)
  @AutoLog("IN query by property values in object")
  @SaCheckPermission("system:user:getMultiUser")
  public List<SysUser> getMultiUser(SysUser sysUser) {
    QueryWrapper<SysUser> queryWrapper = QueryGenerator.initQueryWrapper(sysUser, null);
    // update-begin---author:wangshuai ---date:20220104
    // for：[JTC-297]frozen user could still be set as agent------------
    queryWrapper.eq("status", Integer.parseInt(CommonConstant.STATUS_1));
    // update-end---author:wangshuai ---date:20220104
    // for：[JTC-297]frozen user could still be set as agent------------
    List<SysUser> ls = this.sysUserService.list(queryWrapper);
    // P2: strip all sensitive columns (not just password/salt) before returning.
    ls.forEach(SysUser::clearSensitiveForResponse);
    return ls;
  }

  /**
   * Chat: dedicated endpoint for chat component; paginated query by username, real name, and department id
   *
   * @param departId department id
   * @param keyword  search keyword
   * @return
   */
  @RequestMapping(value = "/getUserInformation", method = RequestMethod.POST)
  @SaCheckPermission("system:user:getUserInformation")
  @AutoLog("GetUserInfo")
  public Result<IPage<SysUser>> getUserInformation(
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      @RequestParam(name = "departId", required = false) String departId,
      @RequestParam(name = "keyword", required = false) String keyword) {
    Integer tenantId = null;
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
    }
    IPage<SysUser> pageList = sysUserDepartService.getUserInformation(tenantId, departId, keyword, pageSize, pageNo);
    return Result.OK(pageList);
  }

  /**
   * Simplified workflow user selector component
   *
   * @param departId department id
   * @param roleId   role id
   * @param keyword  search keyword
   * @return
   */
  @RequestMapping(value = "/selectUs1erList", method = RequestMethod.POST)
  @AutoLog("GetUserInfo")
  @SaCheckPermission("system:user:selectUs1erList")
  public Result<IPage<SysUser>> selectUserList(
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      @RequestParam(name = "departId", required = false) String departId,
      @RequestParam(name = "roleId", required = false) String roleId,
      @RequestParam(name = "keyword", required = false) String keyword,
      @RequestParam(name = "excludeUserIdList", required = false) String excludeUserIdList,
      HttpServletRequest req) {
    Integer tenantId = null;
    IPage<SysUser> pageList = sysUserDepartService.getUserInformation(
        tenantId, departId, roleId, keyword, pageSize, pageNo, excludeUserIdList);
    return Result.OK(pageList);
  }

  /**
   * Get user info (Vue3 user settings) [low-code app API]
   *
   * @return
   */
  @RequestMapping(value = "/login/setting/getUserData", method = RequestMethod.POST)
  @AutoLog("GetUserInfo")
  public Result<SysUser> getUserData(HttpServletRequest request) {

    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");

    String username = loginUser.getUsername();
    SysUser user = sysUserService.getUserByName(username);
    if (user == null) {
      return Result.error(I18nUtil.getMessage("sysUser.userDataNotFound"));
    }

    // update-begin---author:wangshuai ---date:20230220 for：[QQYUN-3980]org position feature
    // Add tenant_id to position table
    // Add position-user relation table ------------
    // Get user id via position data
    List<SysPosition> sysPositionList = sysPositionService.getPositionList(user.getId());
    if (null != sysPositionList && sysPositionList.size() > 0) {
      // update-end---author:wangshuai ---date:20230220 for：[QQYUN-3980]org position feature
      // Add tenant_id to position table
      // Add position-user relation table ------------
      StringBuilder nameBuilder = new StringBuilder();
      StringBuilder idBuilder = new StringBuilder();
      String verticalBar = " | ";
      for (SysPosition sysPosition : sysPositionList) {
        nameBuilder.append(sysPosition.getName()).append(verticalBar);
        idBuilder.append(sysPosition.getId()).append(SymbolConstant.COMMA);
      }
      String names = nameBuilder.toString();
      if (oConvertUtils.isNotEmpty(names)) {
        names = names.substring(0, names.lastIndexOf(verticalBar));
        user.setPostText(names);
      }
      // Concatenate position id
      String ids = idBuilder.toString();
      if (oConvertUtils.isNotEmpty(ids)) {
        ids = ids.substring(0, ids.lastIndexOf(SymbolConstant.COMMA));
        user.setPost(ids);
      }
    }
    return Result.ok(user);
  }

  /**
   * User edit (vue3 user settings) [low-code app dedicated API]
   *
   * @param sysUser
   * @return
   */
  @PostMapping("/login/setting/userEdit")
  @AutoLog("UserEdit")
  @SaCheckPermission("system:user:setting:edit")
  public Result<String> userEdit(@RequestBody SysUser sysUser, HttpServletRequest request) {

    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");

    String username = loginUser.getUsername();
    SysUser user = sysUserService.getById(sysUser.getId());
    if (user == null) {
      return Result.error(I18nUtil.getMessage("sysUser.userDataNotFound"));
    }
    if (!username.equals(user.getUsername())) {
      return Result.error(I18nUtil.getMessage("sysUser.canOnlyModifyOwnData"));
    }
    sysUserService.updateById(sysUser);
    return Result.ok(I18nUtil.getMessage("sysUser.updatePersonalInfoSuccess"));
  }

  /**
   * Batch update [low-app]
   *
   * @param jsonObject
   * @return
   */
  @PutMapping("/batchEditUsers")
  @SaCheckPermission("system:user:batchEditUsers")
  public Result<SysUser> batchEditUsers(@RequestBody JSONObject jsonObject) {
    Result<SysUser> result = new Result<SysUser>();
    try {
      sysUserService.batchEditUsers(jsonObject);
      result.setSuccess(true);
      result.setMessage(I18nUtil.getMessage("sysUser.operationSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * Search departments and users by keyword [low-app]
   *
   * @param keyword
   * @return
   */
  @RequestMapping(value = "/searchByKeyword", method = RequestMethod.POST)
  @AutoLog("Search departments and users by keyword")
  @SaCheckPermission("system:user:searchByKeyword")
  public Result<DepartAndUserInfo> searchByKeyword(
      @RequestParam(name = "keyword", required = false) String keyword) {
    DepartAndUserInfo info = sysUserService.searchByKeyword(keyword);
    return Result.ok(info);
  }

  @RequestMapping(value = "/sumRegisterCounts", method = RequestMethod.POST)
  @AutoLog("Count registered users")
  @SaCheckPermission("system:user:sumRegisterCounts")
  public Result sumRegisterCounts() {

    return Result.OK(sysUserService.sumRegisterCounts());
  }

  @RequestMapping(value = "/getRegisterNumTrend", method = RequestMethod.POST)
  @AutoLog("Get registered user count trend")
  @SaCheckPermission("system:user:getRegisterNumTrend")
  public Result getRegisterNumTrend(@RequestParam Integer lastNum, @RequestParam String dateType) {

    return Result.OK(sysUserService.getRegisterNumTrend(lastNum, dateType));
  }

  @PostMapping("/listSysUserRole")
  @AutoLog("QueryUserRole")
  @SaCheckPermission("system:user:listSysUserRole")
  public Result<List<SysUserRole>> listSysUserRole(@RequestBody List<String> userIds) {

    List<SysUserRole> sysUserRoleList = sysUserRoleService.list(new QueryWrapper<SysUserRole>().in("user_id", userIds));

    return Result.OK(sysUserRoleList);
  }

  /**
   * Verify phone number uniqueness (async form validation for frontend)
   *
   * @param request validation request object
   * @return validation result; HTTP status always 200; message in response body
   */
  @PostMapping("/validatePhone")
  @AutoLog("Verify whether phone number is duplicate")
  @ApiOperation(value = "Verify whether phone number is duplicate", notes = "Used for frontend form async validation whether phone number is already used by another user")
  public Result validatePhone(@RequestBody PhoneValidationRequest request) {
    Locale locale = LocaleResolver.getCurrLocale();

    // params check
    if (StrUtil.isBlank(request.getPhoneCode()) || StrUtil.isBlank(request.getPhone())) {
      return Result.error(
          CommonConstant.SC_OK_200,
          messageSource.getMessage("common.phone  _code_and_phone_required", null, locale));
    }

    // Get current userID
    String currUserId = currentBizUserResolver.getBizOwnerUserId();
    LoginUser loginUser = currentBizUserResolver.getLoginUser();
    if (loginUser == null) {
      loginUser = sysBaseAPI.getUserById(currentBizUserResolver.getLoginUserId());
    }

    // User ID to verify (if userId provided and not current user, check permission)
    String userId = request.getUserId();
    if (StrUtil.isBlank(userId)) {
      userId = currUserId;
    } else if (!StrUtil.equals(userId, currUserId)) {
      // Verify permission to authenticate on behalf of specified user
      SysUser user = sysUserService.getById(userId);
      if (user == null) {
        log.warn("User {} attempted phone verification for non-existent user {}", currUserId, userId);
        return Result.error(
            CommonConstant.SC_OK_200,
            messageSource.getMessage("common.user_not_found", null, locale));
      }

      if (!StrUtil.equals(user.getInviterId(), currUserId)) {
        log.warn("User {} attempted phone verification for user {} not invited by themselves", currUserId, user.getUsername());
        return Result.error(
            CommonConstant.SC_OK_200,
            messageSource.getMessage("common.not_agent_cannot_kyc", null, locale));
      }
    }

    // Verify whether the phone number is already used by another user
    if (sysUserService.exists(
        new QueryWrapper<SysUser>()
            .ne("id", userId)
            .eq("phone_code", request.getPhoneCode())
            .eq("phone", request.getPhone()))) {
      log.warn("User {} attempted to use existing phone number {}", loginUser.getUsername(), request.getPhone());
      return Result.error(
          CommonConstant.SC_OK_200,
          messageSource.getMessage("common.phone_already_exists", null, locale));
    }

    return Result.ok();
  }

  /**
   * Ops manual create regular user (enterprise) or tier-1 agent.
   * See docs/api-ops-create-user.md
   */
  @PostMapping("/opsCreateUser")
  @SaCheckPermission("system:user:opsCreateUser")
  @AutoLog("Ops manually create user or agent")

  public Result opsCreateUser(@RequestBody OpsCreateUserRequest req) {
    Locale locale = LocaleResolver.getCurrLocale();
    if (req == null || StrUtil.isBlank(req.getCreateType())) {
      return Result.error(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    String createType = req.getCreateType().trim();
    if (!"user".equals(createType) && !"agent1".equals(createType)) {
      return Result.error(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    if (StrUtil.isBlank(req.getEmail()) && StrUtil.isBlank(req.getUsername())) {
      return Result.error(I18nUtil.getMessage("sysUser.emailEmpty"));
    }
    // Ops create: username and email merged (single front-end field)
    String email = StrUtil.blankToDefault(req.getEmail(), req.getUsername()).trim();
    req.setEmail(email);
    req.setUsername(email);
    if (StrUtil.isBlank(req.getCustomerType())) {
      return Result.error(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    if (sysUserService.getUserByName(req.getUsername()) != null) {
      return Result.error(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    if (sysUserService.getUserByEmail(req.getEmail()) != null) {
      return Result.error(I18nUtil.getMessage("sysUser.emailExists"));
    }

    String accountGroup = "agent1".equals(createType) ? "1" : "2";
    List<String> heldTypes = listAccountTypesByGroupFromDb(accountGroup);
    if (heldTypes.isEmpty()) {
      return Result.error(
          messageSource.getMessage(
              "common.error.open_failed",
              new Object[] {"no account types for account_group=" + accountGroup},
              "no account types for account_group=" + accountGroup,
              locale));
    }

    boolean openAll = req.getOpenAllAccounts() == null || Boolean.TRUE.equals(req.getOpenAllAccounts());
    List<String> openTypes = parseOpsAccountTypes(req.getAccountTypes());
    if (openTypes.isEmpty() && openAll) {
      openTypes = new ArrayList<>(heldTypes);
    }
    if (openTypes.isEmpty()) {
      return Result.error(
          messageSource.getMessage(
              "common.error.open_failed",
              new Object[] {"accountTypes required"},
              "accountTypes required",
              locale));
    }
    for (String t : openTypes) {
      if (!heldTypes.contains(t)) {
        return Result.error(
            messageSource.getMessage(
                "common.error.open_failed",
                new Object[] {"accountType not in account_group=" + accountGroup + ": " + t},
                "accountType not in account_group=" + accountGroup + ": " + t,
                locale));
      }
    }

    String inviterId = null;
    if ("user".equals(createType)) {
      Result<String> bindResult = resolveOpsInviterId(req.getInviteCode(), req.getInviterId(), locale);
      if (!bindResult.isSuccess()) {
        return bindResult;
      }
      inviterId = bindResult.getResult();
    }

    // Ops onboarding: no password input; placeholder hash; first password via account email resetUrl
    String placeholderSecret = IdWorker.getIdStr() + "!Unset!" + System.nanoTime();
    String hashedPassword = SmCryptoUtil.hashPassword(placeholderSecret);

    SysUser newUser = new SysUser();
    newUser.setUsername(req.getUsername().trim());
    newUser.setEmail(req.getEmail().trim());
    newUser.setRealname(StrUtil.blankToDefault(req.getRealname(), req.getUsername().trim()));
    newUser.setCustomerType(req.getCustomerType());
    newUser.setPassword(hashedPassword);
    newUser.setSalt(oConvertUtils.randomGen(8));
    newUser.setCreateTime(new Date());
    newUser.setStatus(1);
    newUser.setDelFlag(CommonConstant.DEL_FLAG_0);
    // Ember R2/Q9: ops-created accounts treated as off-site approved; KYC marked passed so the full chain is not blocked by transaction gates.
    newUser.setKycStatus("2");
    newUser.setAllowLogin(1);
    newUser.setEmailNotice(1);
    newUser.setInviterId(inviterId);
    newUser.setOrgCode(null);
    if (StrUtil.isNotBlank(req.getAvatar())) {
      try {
        newUser.setAvatar(CommonUtils.convertObjectId(req.getAvatar()));
      } catch (Exception e) {
        return Result.error(I18nUtil.getMessage("sysUser.avatarInvalid"));
      }
    }
    if ("agent1".equals(createType)) {
      newUser.setInviterId(null);
      newUser.setDlygx(null);
    }

    String roleId = commonAPI.translateDictFromTable("sys_role", "id", "role_code", createType);
    if (StrUtil.isBlank(roleId)) {
      return Result.error(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    sysUserService.saveUser(newUser, roleId, null, null);

    List<cn.hutool.json.JSONObject> inviteCodes = null;
    if ("agent1".equals(createType)) {
      inviteCodes = ensureAgentInviteCodes(newUser.getId());
    }

    cn.hutool.json.JSONObject result = new cn.hutool.json.JSONObject();

    // Ops create: open account synchronously in same DB (avoid create success without account via MQ only)
    ensureOpsAccountsOpen(newUser.getId(), heldTypes, openTypes);

    // Publish MQ after transaction submit for DCS-side idempotent correction (compatible with registration flow consumer)
    final String mqUserId = newUser.getId();
    final String mqRoleCode = createType;
    final String mqAccountTypes = String.join(",", openTypes);
    Runnable sendOpenAccountMq =
        () -> {
          cn.hutool.json.JSONObject mqMsg = new cn.hutool.json.JSONObject();
          mqMsg.set("userId", mqUserId);
          mqMsg.set("roleCode", mqRoleCode);
          mqMsg.set("accountTypes", mqAccountTypes);
          try {
            // Send synchronously for easier failure observation; log only on failure (account already persisted)
            rocketMQUtil.send("autoOpenAccount_topic", mqMsg, null);
          } catch (Exception e) {
            log.warn(
                "opsCreateUser sync account opening completed, but failed to publish autoOpenAccount_topic userId={}",
                mqUserId,
                e);
          }
        };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              sendOpenAccountMq.run();
            }
          });
    } else {
      sendOpenAccountMq.run();
    }

    accountCreatedEmailService.scheduleAfterCommit(
        newUser,
        "OPS_RESET",
        StpUtil.getLoginIdAsString(),
        "ACCOUNT_CREATED:ops:" + newUser.getId());

    baseCommonService.addLog(
        "Ops created user, username: " + newUser.getUsername() + "，createType=" + createType,
        CommonConstant.LOG_TYPE_2,
        2);

    result.set("userId", newUser.getId());
    result.set("roleCode", createType);
    result.set("openedAccountTypes", openTypes);
    result.set("inviteCodes", inviteCodes);
    return Result.OK(result);
  }

  /**
   * Ops bind/unbind agent for regular user
   */
  @PostMapping("/opsBindAgent")
  @SaCheckPermission("system:user:opsBindAgent")
  @AutoLog("Ops bind or unbind agent")
  public Result opsBindAgent(
      @RequestParam String userId,
      @RequestParam(required = false) String inviteCode,
      @RequestParam(required = false) String inviterId) {
    // NOTE: UserInviteCode/UserLabel 已清理, 方法体 stub
    return Result.error("功能已停用");
  }

  private Result<String> resolveOpsInviterId(String inviteCode, String inviterId, Locale locale) {
    if (StrUtil.isBlank(inviterId)) {
      return Result.OK(null);
    }
    SysUser agent = sysUserService.getById(inviterId);
    if (agent == null) {
      return Result.error(I18nUtil.getMessage("sysUser.userNotFound"));
    }
    return Result.OK(inviterId);
  }

  private List<cn.hutool.json.JSONObject> ensureAgentInviteCodes(String userId) {
    return new ArrayList<>();
  }

  private List<String> listAccountTypesByGroupFromDb(String accountGroup) {
    if (dcsApi == null) {
      log.warn("listAccountTypesByGroupFromDb skip, dcsApi not injected for shard");
      return new ArrayList<>();
    }
    try {
      Result<List<String>> r = dcsApi.listAccountTypesByGroup(accountGroup);
      if (r != null && r.isSuccess() && r.getResult() != null) {
        return r.getResult();
      }
    } catch (Exception e) {
      log.warn("listAccountTypesByGroup remote call failed accountGroup={}", accountGroup, e);
    }
    return new ArrayList<>();
  }

  private void ensureOpsAccountsOpen(
      String userId, List<String> heldTypes, List<String> openTypes) {
    if (StrUtil.isBlank(userId)) {
      return;
    }
    if (dcsApi == null) {
      log.warn("ensureOpsAccountsOpen skip, dcsApi not injected for shard userId={}", userId);
      return;
    }
    try {
      Result<Boolean> r = dcsApi.ensureOpsAccountsOpen(userId, heldTypes, openTypes);
      if (r == null || !r.isSuccess()) {
        log.warn("ensureOpsAccountsOpen remote call failed userId={} code={} msg={}",
            userId, r == null ? null : r.getCode(), r == null ? null : r.getMessage());
      }
    } catch (Exception e) {
      log.warn("ensureOpsAccountsOpen remote call exception userId={}", userId, e);
    }
  }

  private List<String> parseOpsAccountTypes(List<String> accountTypes) {
    if (accountTypes == null || accountTypes.isEmpty()) {
      return new ArrayList<>();
    }
    return accountTypes.stream()
        .filter(StrUtil::isNotBlank)
        .map(String::trim)
        .distinct()
        .collect(Collectors.toList());
  }

  // Enable or disable master-agent role for specified user
  @PostMapping("/updateAgentRole")
  @AutoLog("Update master agent role")
  @SaCheckPermission("system:user:updateAgentRole")
  public Result updateAgentRole(
      @ValidDictionary(table = "sys_user", code = "id", text = "id", scope = DictScope.SYSTEM) @RequestParam String userId,
      @RequestParam boolean openAgentRole) {
    // NOTE: UserInviteCode/UserLabel 已清理, 方法体 stub
    return Result.error("功能已停用");
  }

  // Get master agent ID bound to current user
  @RequestMapping(value = "/getAgentId", method = RequestMethod.POST)
  @AutoLog("Get master agent ID bound to current user")
  public Result<String> getAgentId() {

    if (StpUtil.hasRole("agent1")) {
      return Result.ok(StpUtil.getLoginIdAsString());
    }

    if (StpUtil.hasRole("agent2")) {

      return Result.ok(sysUserService.getById(StpUtil.getLoginIdAsString()).getInviterId());
    }

    if (StpUtil.hasRole("user")) {

      String inviterId = sysUserService.getById(StpUtil.getLoginIdAsString()).getInviterId();

      if (StrUtil.isBlank(inviterId)) {
        return Result.OK();
      }

      return Result.ok(sysUserService.getById(inviterId).getInviterId());
    }

    return Result.ok();
  }

  @AutoLog("Get agent ID belonging to user")
  @PostMapping("/getUserAgent")
  public Result<cn.hutool.json.JSONObject> getUserAgent(@RequestParam String userId) {

    cn.hutool.json.JSONObject result = new cn.hutool.json.JSONObject();

    SysUser sysUser = sysUserService.getById(userId);

    if (StrUtil.isNotBlank(sysUser.getInviterId())) {

      List<SysRole> roleList = sysUserService.getRolesByUserId(sysUser.getInviterId());

      if (roleList.stream().anyMatch(i -> i.getRoleCode().equals("agent1"))) {

        result.set("agent1", sysUser.getInviterId());
      } else {
        result.set("agent2", sysUser.getInviterId());

        String inviterId = sysUserService.getById(sysUser.getInviterId()).getInviterId();

        if (StrUtil.isNotBlank(inviterId)) {

          result.set("agent1", inviterId);
        }
      }
    }

    return Result.ok(result);
  }

  // Update agent bound to current user; supports unbind/bind etc.
  @AutoLog("Update agent bound to current user")
  @PostMapping("/updateUserAgent")
  @SaCheckLogin
  public Result<String> updateUserAgent(@RequestParam(required = false) String inviteCode) {
    return null;
  }

  // Get agent invite code; supports agent staff and agents
  @RequestMapping(value = "/getAgentInviteCode", method = RequestMethod.POST)
  @AutoLog("Get agent invitation code")
  public Result<String> getAgentInviteCode() {
    return null;
  }

  // Bind email
  @PostMapping("/bindEmail")
  @AutoLog("Bind email")
  public Result<String> bindEmail(@RequestParam String email) {

    String userId = StpUtil.getLoginIdAsString();
    SysUser sysUser = sysUserService.getById(userId);
    if (sysUser == null) {
      return Result.error(I18nUtil.getMessage("sysUser.userNotFound"));
    }
    if (StrUtil.isNotBlank(sysUser.getEmail())) {

      return Result.error(I18nUtil.getMessage("sysUser.emailAlreadyBound"));
    }

    if (sysUserService.exists(new QueryWrapper<SysUser>().eq("email", email))) {
      return Result.error(I18nUtil.getMessage("sysUser.emailAlreadyBoundByOther"));
    }
    sysUser.setEmail(email);
    sysUserService.updateById(sysUser);
    // Clear cache
    redisUtil.del(StrUtil.format("sys:cache:encrypt:user::{}", sysUser.getUsername()));
    StpUtil.getSession().set("loginUser", sysBaseAPI.getUserById(userId));
    return Result.ok(I18nUtil.getMessage("sysUser.emailBindSuccess"));
  }

  @AutoLog("Set user tags")
  @SaCheckPermission("system:user:setUserLabel")
  @PostMapping("/setUserLabel")
  public Result setUserLabel(@RequestParam String userId, @RequestBody List<String> labels) {
    // NOTE: UserInviteCode/UserLabel 已清理, 方法体 stub
    return Result.error("功能已停用");
  }

  @PostMapping("/team/member/list")
  @AutoLog("Query team members")
  public Result<IPage<SysUser>> listTeamMembers(@RequestBody TeamMemberListRequest request) {
    Locale locale = LocaleResolver.getCurrLocale();
    String ownerUserId = resolveTeamOwnerId(request != null ? request.getOwnerUserId() : null);

    Result<?> permissionCheck = validateTeamOwnerAccess(ownerUserId, locale);
    if (permissionCheck != null) {
      return (Result<IPage<SysUser>>) permissionCheck;
    }

    QueryWrapper<SysUser> queryWrapper =
        new QueryWrapper<SysUser>().eq("del_flag", 0).eq("dlygx", ownerUserId);

    if (request != null && request.getStatus() != null) {
      queryWrapper.eq("status", request.getStatus());
    }
    if (request != null && StrUtil.isNotBlank(request.getSearchText())) {
      queryWrapper.and(
          wrapper ->
              wrapper
                  .like("username", request.getSearchText())
                  .or()
                  .like("realname", request.getSearchText())
                  .or()
                  .like("email", request.getSearchText())
                  .or()
                  .like("phone", request.getSearchText()));
    }
    queryWrapper.orderByDesc("create_time");

    int pageNo = request != null && request.getPageNo() != null ? request.getPageNo() : 1;
    int pageSize = request != null && request.getPageSize() != null ? request.getPageSize() : 10;

    Page<SysUser> page = new Page<>(pageNo, pageSize);
    IPage<SysUser> pageList = sysUserService.page(page, queryWrapper);
    pageList.getRecords().forEach(
        item -> item.setRoleList(enrichTeamMemberRoles(sysUserService.getRolesByUserId(item.getId()))));
    return Result.OK(pageList);
  }

  @PostMapping("/team/member/create")

  @AutoLog("Create team member")
  public Result<String> createTeamMember(@Valid @RequestBody TeamMemberCreateRequest request) {
    Locale locale = LocaleResolver.getCurrLocale();
    String ownerUserId = resolveTeamOwnerId(request.getOwnerUserId());

    Result<?> permissionCheck = validateTeamOwnerAccess(ownerUserId, locale);
    if (permissionCheck != null) {
      return (Result<String>) permissionCheck;
    }

    if (sysUserService.getUserByName(request.getUsername()) != null) {
      return Result.error(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    if (StrUtil.isBlank(request.getEmail())) {
      return Result.error(I18nUtil.getMessage("sysUser.emailEmpty"));
    }
    if (sysUserService.getUserByEmail(request.getEmail()) != null) {
      return Result.error(I18nUtil.getMessage("sysUser.emailExists"));
    }

    List<SysRole> teamRoles = resolveTeamRoles(request.getRoleCodes(), locale);
    SysUser ownerUser = sysUserService.getById(ownerUserId);
    if (ownerUser == null || ownerUser.getDelFlag() != null && ownerUser.getDelFlag() != 0) {
      return Result.error(messageSource.getMessage("common.user_not_found", null, locale));
    }

    SysUser user = new SysUser();
    user.setUsername(StrUtil.trim(request.getUsername()));
    user.setRealname(StrUtil.trim(request.getRealname()));
    user.setEmail(StrUtil.trim(request.getEmail()));
    user.setCustomerType(ownerUser.getCustomerType());
    user.setStatus(ownerUser.getStatus());
    user.setAllowLogin(ownerUser.getAllowLogin() == null ? 1 : ownerUser.getAllowLogin());
    user.setDelFlag(CommonConstant.DEL_FLAG_0);
    user.setDlygx(ownerUserId);
    user.setInviterId(null);
    user.setOrgCode(null);
    user.setCreateTime(new Date());
    user.setSalt(oConvertUtils.randomGen(8));
    user.setEmailNotice(1);
    String placeholderSecret = IdWorker.getIdStr() + "!Unset!" + System.nanoTime();
    user.setPassword(SmCryptoUtil.hashPassword(placeholderSecret));

    String selectedRoles = teamRoles.stream().map(SysRole::getId).collect(Collectors.joining(","));
    sysUserService.saveUser(user, selectedRoles, "", null);
    sysUserService.refreshRolesByUserId(user.getId());

    // Same as ops user creation: after transaction submit, send ACCOUNT_CREATED onboarding email (includes set-password resetUrl)
    accountCreatedEmailService.scheduleAfterCommit(
        user,
        "TEAM_INVITE",
        ownerUserId,
        "ACCOUNT_CREATED:team:" + user.getId());
    log.info(
        "createTeamMember scheduled ACCOUNT_CREATED email userId={} email={}",
        user.getId(),
        user.getEmail());

    return Result.OK(user.getId());
  }

  @PutMapping("/team/member/status")
  @AutoLog("Update team member status")
  public Result<?> updateTeamMemberStatus(@RequestBody TeamMemberStatusRequest request) {
    Locale locale = LocaleResolver.getCurrLocale();
    String ownerUserId = resolveTeamOwnerId(request.getOwnerUserId());

    Result<?> permissionCheck = validateTeamOwnerAccess(ownerUserId, locale);
    if (permissionCheck != null) {
      return permissionCheck;
    }

    SysUser teamMember = getTeamMember(ownerUserId, request.getMemberUserId());
    if (teamMember == null) {
      return Result.error(messageSource.getMessage("common.user_not_found", null, locale));
    }

    sysUserService.updateStatus(teamMember.getId(), String.valueOf(request.getStatus()));
    sysUserService.update(
        new UpdateWrapper<SysUser>()
            .eq("id", teamMember.getId())
            .set("allow_login", request.getStatus() != null && request.getStatus() == 1 ? 1 : 2));
    if (request.getStatus() != null && request.getStatus() != 1) {
      sessionKickService.kickAllDevices(teamMember.getId(), "team_member_locked");
    }

    return Result.OK(I18nUtil.getMessage("common.success.edit"));
  }

  @PutMapping("/team/member/update")
  @AutoLog("Edit team member")
  public Result<?> updateTeamMember(@RequestBody TeamMemberUpdateRequest request) {
    Locale locale = LocaleResolver.getCurrLocale();
    String ownerUserId = resolveTeamOwnerId(request.getOwnerUserId());

    Result<?> permissionCheck = validateTeamOwnerAccess(ownerUserId, locale);
    if (permissionCheck != null) {
      return permissionCheck;
    }

    SysUser teamMember = getTeamMember(ownerUserId, request.getMemberUserId());
    if (teamMember == null) {
      return Result.error(messageSource.getMessage("common.user_not_found", null, locale));
    }

    String email = StrUtil.trim(request.getEmail());
    if (StrUtil.isBlank(email)) {
      return Result.error(I18nUtil.getMessage("sysUser.emailEmpty"));
    }
    if (emailConflictsWithOtherUsers(email, teamMember.getId())) {
      return Result.error(I18nUtil.getMessage("sysUser.emailExists"));
    }

    String phoneCode = StrUtil.trim(request.getPhoneCode());
    String phone = StrUtil.trim(request.getPhone());
    if (phoneConflictsWithOtherUsers(phoneCode, phone, teamMember.getId())) {
      return Result.error(I18nUtil.getMessage("common.phone_already_exists"));
    }

    SysUser updateUser = new SysUser();
    updateUser.setId(teamMember.getId());
    updateUser.setRealname(StrUtil.trim(request.getRealname()));
    updateUser.setEmail(email);
    updateUser.setPhoneCode(phoneCode);
    updateUser.setPhone(phone);
    updateUser.setBirthday(request.getBirthday());
    updateUser.setSex(request.getSex());
    updateUser.setAvatar(StrUtil.trim(request.getAvatar()));
    updateUser.setUpdateTime(new Date());
    sysUserService.updateById(updateUser);

    return Result.OK(I18nUtil.getMessage("common.success.edit"));
  }

  @PutMapping("/team/member/roles")

  @AutoLog("Update team member role")
  public Result<?> updateTeamMemberRoles(@RequestBody TeamMemberRolesRequest request) {
    Locale locale = LocaleResolver.getCurrLocale();
    String ownerUserId = resolveTeamOwnerId(request.getOwnerUserId());

    Result<?> permissionCheck = validateTeamOwnerAccess(ownerUserId, locale);
    if (permissionCheck != null) {
      return permissionCheck;
    }

    SysUser teamMember = getTeamMember(ownerUserId, request.getMemberUserId());
    if (teamMember == null) {
      return Result.error(messageSource.getMessage("common.user_not_found", null, locale));
    }

    List<SysRole> teamRoles = resolveTeamRoles(request.getRoleCodes(), locale);
    String selectedRoles = teamRoles.stream().map(SysRole::getId).collect(Collectors.joining(","));

    SysUser updateUser = new SysUser();
    updateUser.setId(teamMember.getId());
    updateUser.setUpdateTime(new Date());
    sysUserService.editUserWithRole(updateUser, selectedRoles);
    sysUserService.refreshRolesByUserId(teamMember.getId());
    SaSession session = StpUtil.getSessionByLoginId(teamMember.getId(), false);
    if (session != null) {
      session.set("loginUser", sysBaseAPI.getUserById(teamMember.getId()));
    }

    return Result.OK(I18nUtil.getMessage("common.success.edit"));
  }

  @PutMapping("/team/member/resetPassword")
  @AutoLog("Reset team member password")
  public Result<?> resetTeamMemberPassword(@RequestBody TeamMemberResetPasswordRequest request) {
    Locale locale = LocaleResolver.getCurrLocale();
    String ownerUserId = resolveTeamOwnerId(request.getOwnerUserId());

    Result<?> permissionCheck = validateTeamOwnerAccess(ownerUserId, locale);
    if (permissionCheck != null) {
      return permissionCheck;
    }
    if (StrUtil.isBlank(request.getPassword())
        || !request.getPassword().matches(CommonConstant.PASSWORD_REGEX)) {
      return Result.error(
          messageSource.getMessage("sysUser.passwordFormatIncorrect", null, locale));
    }

    SysUser teamMember = getTeamMember(ownerUserId, request.getMemberUserId());
    if (teamMember == null) {
      return Result.error(messageSource.getMessage("common.user_not_found", null, locale));
    }

    SysUser updateUser = new SysUser();
    updateUser.setId(teamMember.getId());
    updateUser.setPassword(SmCryptoUtil.hashPassword(request.getPassword()));
    updateUser.setLastPasswordChangeTime(new Date());
    sysUserService.updateById(updateUser);
    sessionKickService.kickAllDevices(teamMember.getId(), "team_member_reset_pwd");

    return Result.OK(I18nUtil.getMessage("common.success.operation"));
  }

  @PutMapping("/team/member/unbind")
  @AutoLog("Unbind team member")
  public Result<?> unbindTeamMember(@RequestBody TeamMemberUnbindRequest request) {
    Locale locale = LocaleResolver.getCurrLocale();
    String ownerUserId = resolveTeamOwnerId(request.getOwnerUserId());

    Result<?> permissionCheck = validateTeamOwnerAccess(ownerUserId, locale);
    if (permissionCheck != null) {
      return permissionCheck;
    }

    SysUser teamMember = getTeamMember(ownerUserId, request.getMemberUserId());
    if (teamMember == null) {
      return Result.error(messageSource.getMessage("common.user_not_found", null, locale));
    }

    List<SysRole> teamRoles =
        sysUserService.getRolesByUserId(teamMember.getId()).stream()
            .toList();
    if (!teamRoles.isEmpty()) {
      sysUserRoleService.remove(
          new QueryWrapper<SysUserRole>()
              .eq("user_id", teamMember.getId())
              .in("role_id", teamRoles.stream().map(SysRole::getId).toList()));
    }

    sysUserService.update(
        new UpdateWrapper<SysUser>()
            .eq("id", teamMember.getId())
            .set("dlygx", null)
            .set("allow_login", 2)
            .set("status", 2)
            .set("update_time", new Date()));
    sysUserService.refreshRolesByUserId(teamMember.getId());
    sessionKickService.kickAllDevices(teamMember.getId(), "team_member_unbound");

    return Result.OK(I18nUtil.getMessage("common.success.operation"));
  }

  @AutoLog("Batch set email notification toggle")
  @PutMapping("/batchSetEmailNotice")
  public Result<String> batchSetEmailNotice(@RequestBody JSONObject jsonObject) {
    Locale locale = LocaleResolver.getCurrLocale();

    String ids = jsonObject.getString("ids");
    Integer emailNotice = jsonObject.getInteger("emailNotice");

    if (StrUtil.isBlank(ids)) {
      return Result.error(messageSource.getMessage("common.userid_is_null", null, locale));
    }
    if (emailNotice == null || (emailNotice != 1 && emailNotice != 2)) {
      return Result.error(I18nUtil.getMessage("sysUser.emailNoticeInvalid"));
    }

    List<String> userIds = Arrays.stream(ids.split(","))
        .map(String::trim)
        .filter(StrUtil::isNotBlank)
        .distinct()
        .toList();
    if (userIds.isEmpty()) {
      return Result.error(messageSource.getMessage("common.userid_is_null", null, locale));
    }

    // Current operating agent ID: team members/proxy roles use primary account by business ownership
    String currentAgentId = currentBizUserResolver.getBizOwnerUserId();
    if (StrUtil.isBlank(currentAgentId)) {
      return Result.error(messageSource.getMessage("common.not_agent_cannot_kyc", null, locale));
    }

    // Ownership check: all specified users must belong to current agent (inviter_id = currentAgentId)
    Long belongCount = sysUserService.count(new QueryWrapper<SysUser>()
        .in("id", userIds)
        .eq("inviter_id", currentAgentId)
        .eq("del_flag", 0));
    if (belongCount == null || belongCount != userIds.size()) {
      return Result.error(messageSource.getMessage("common.not_agent_cannot_kyc", null, locale));
    }

    sysUserService.update(new UpdateWrapper<SysUser>()
        .in("id", userIds)
        .set("email_notice", emailNotice));

    return Result.ok(I18nUtil.getMessage("sysUser.operationSuccess"));
  }




  @PutMapping("/agent/updateAuthEmails")
  @ApiOperation("Agent updates subordinate user verification email")
  @AutoLog("Agent updates subordinate user verification email")
  public Result<?> updateSubUserAuthEmails(@RequestBody AgentUpdateAuthEmailsRequest req) {
    Locale locale = LocaleResolver.getCurrLocale();
    String operatorUserId = currentBizUserResolver.getLoginUserId();
    if (StrUtil.isBlank(operatorUserId)) {
      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    boolean privileged = RoleConfigUtil.isAdmin(commonAPI);
    boolean isAgent =
        StpUtil.hasRole("agent1")
            || StpUtil.hasRole("agent2")
            || StpUtil.hasRoleOr("MACKER", "CHECKER");
    if (!privileged && !isAgent) {
      return Result.error(messageSource.getMessage("common.no_permission", null, locale));
    }

    if (StrUtil.isBlank(req.getUserId())) {
      return Result.error(messageSource.getMessage("common.user_not_exist", null, locale));
    }

    if (StrUtil.equals(operatorUserId, req.getUserId())) {
      return Result.error(messageSource.getMessage("common.no_permission", null, locale));
    }

    SysUser targetUser = sysUserService.getById(req.getUserId());
    if (targetUser == null || (targetUser.getDelFlag() != null && targetUser.getDelFlag() != 0)) {
      return Result.error(messageSource.getMessage("common.user_not_exist", null, locale));
    }

    String agentId = operatorUserId;
    if (StpUtil.hasRoleOr("MACKER", "CHECKER")) {
      LoginUser operatorAsUser = sysBaseAPI.getUserById(operatorUserId);
      if (operatorAsUser != null && StrUtil.isNotBlank(operatorAsUser.getDlygx())) {
        agentId = operatorAsUser.getDlygx();
      }
    }

    if (!privileged) {
      if (StpUtil.hasRole("agent1")) {
        if (!StrUtil.equals(agentId, targetUser.getInviterId())) {
          String inviterId = targetUser.getInviterId();
          if (StrUtil.isBlank(inviterId)) {
            return Result.error(messageSource.getMessage("common.modify_user_no_permission", null, locale));
          }
          SysUser inviterUser = sysUserService.getById(inviterId);
          if (inviterUser == null || !StrUtil.equals(agentId, inviterUser.getInviterId())) {
            return Result.error(messageSource.getMessage("common.modify_user_no_permission", null, locale));
          }
        }
      } else {
        if (!StrUtil.equals(agentId, targetUser.getInviterId())) {
          return Result.error(messageSource.getMessage("common.modify_user_no_permission", null, locale));
        }
      }
    }

    if (targetUser.getLastLoginTime() != null) {
      return Result.error(
          messageSource.getMessage(
              "SysUserController.updateSubUserAuthEmails.user_already_logged_in", null, locale));
    }

    String newEmail = StrUtil.trim(req.getEmail());
    if (StrUtil.isBlank(newEmail)) {
      return Result.error(messageSource.getMessage("sysUser.emailEmpty", null, locale));
    }
    newEmail = newEmail.toLowerCase();
    if (!Validator.isEmail(newEmail)) {
      return Result.error(
          messageSource.getMessage(
              "SysUserController.updateSubUserAuthEmails.invalid_email_format", null, locale));
    }
    if (!StrUtil.equalsIgnoreCase(newEmail, targetUser.getEmail())) {
      if (emailConflictsWithOtherUsers(newEmail, targetUser.getId())) {
        return Result.error(messageSource.getMessage("sysUser.operationFailed", null, locale));
      }
    }

    String oldEmail = targetUser.getEmail();

    LoginUser operator = (LoginUser) StpUtil.getSession().get("loginUser");
    if (operator == null) {
      operator = sysBaseAPI.getUserById(operatorUserId);
    }

    SysUser updateUser = new SysUser();
    updateUser.setId(targetUser.getId());
    updateUser.setUpdateTime(new Date());
    if (operator != null) {
      updateUser.setUpdateBy(operator.getUsername());
    }

    boolean willUpdate = false;
    if (!StrUtil.equalsIgnoreCase(newEmail, targetUser.getEmail())) {
      updateUser.setEmail(newEmail);
      willUpdate = true;
    }

    if (!willUpdate) {
      return Result.OK(I18nUtil.getMessage("common.success.operation"));
    }

    sysUserService.updateById(updateUser);

    redisUtil.del(StrUtil.format("sys:cache:encrypt:user::{}", targetUser.getUsername()));

    String logContent =
        StrUtil.format(
            "Agent updates subordinate user verification email: targetUserId={}, email:{}->{}",
            targetUser.getId(),
            SensitiveInfoUtil.email(oldEmail),
            SensitiveInfoUtil.email(newEmail));
    baseCommonService.addLog(logContent, CommonConstant.LOG_TYPE_2, 2, operator);

    return Result.OK(I18nUtil.getMessage("common.success.edit"));
  }

  @Data
  public static class AgentUpdateAuthEmailsRequest {
    private String userId;
    private String email;
  }

  private boolean emailConflictsWithOtherUsers(String email, String excludeUserId) {
    if (StrUtil.isBlank(email)) {
      return false;
    }
    Long count =
        sysUserService.count(
            new QueryWrapper<SysUser>()
                .ne("id", excludeUserId)
                .eq("del_flag", 0)
                .eq("email", email));
    return count != null && count > 0;
  }

  private boolean usernameConflictsWithOtherUsers(String username, String excludeUserId) {
    if (StrUtil.isBlank(username)) {
      return false;
    }
    Long count =
        sysUserService.count(
            new QueryWrapper<SysUser>()
                .ne("id", excludeUserId)
                .eq("del_flag", 0)
                .eq("username", username));
    return count != null && count > 0;
  }

  private boolean phoneConflictsWithOtherUsers(
      String phoneCode, String phone, String excludeUserId) {
    if (StrUtil.isBlank(phone)) {
      return false;
    }
    Long count =
        sysUserService.count(
            new QueryWrapper<SysUser>()
                .ne("id", excludeUserId)
                .eq("del_flag", 0)
                .eq("phone_code", phoneCode)
                .eq("phone", phone));
    return count != null && count > 0;
  }

  private String resolveTeamOwnerId(String requestOwnerUserId) {
    if (RoleConfigUtil.isAdmin(commonAPI) && StrUtil.isNotBlank(requestOwnerUserId)) {
      return requestOwnerUserId;
    }
    return currentBizUserResolver.getBizOwnerUserId();
  }

  private Result<?> validateTeamOwnerAccess(String ownerUserId, Locale locale) {
    if (StrUtil.isBlank(ownerUserId)) {
      return Result.error(messageSource.getMessage("common.user_not_found", null, locale));
    }
    if (RoleConfigUtil.isAdmin(commonAPI)) {
      return validateTeamOwnerRole(ownerUserId, locale);
    }

    String loginUserId = currentBizUserResolver.getLoginUserId();
    String bizOwnerUserId = currentBizUserResolver.getBizOwnerUserId();
    if (!StrUtil.equals(loginUserId, bizOwnerUserId)) {
      return Result.error(messageSource.getMessage("common.no_permission", null, locale));
    }
    if (!StrUtil.equals(ownerUserId, bizOwnerUserId)) {
      return Result.error(messageSource.getMessage("common.no_permission", null, locale));
    }
    return validateTeamOwnerRole(ownerUserId, locale);
  }

  private Result<?> validateTeamOwnerRole(String ownerUserId, Locale locale) {
    List<SysRole> ownerRoles = sysUserService.getRolesByUserId(ownerUserId);
    boolean supportedOwner =
        ownerRoles.stream()
            .map(SysRole::getRoleCode)
            .anyMatch(roleCode -> StrUtil.equalsAny(roleCode, "agent1", "user"));
    if (!supportedOwner) {
      return Result.error(messageSource.getMessage("common.no_permission", null, locale));
    }
    return null;
  }

  private SysUser getTeamMember(String ownerUserId, String memberUserId) {
    if (StrUtil.isBlank(memberUserId)) {
      return null;
    }
    return sysUserService.getOne(
        new QueryWrapper<SysUser>()
            .eq("id", memberUserId)
            .eq("dlygx", ownerUserId)
            .eq("del_flag", 0));
  }

  private List<SysRole> enrichTeamMemberRoles(List<SysRole> roles) {
    return roles;
  }

  private List<SysRole> resolveTeamRoles(List<String> roleCodes, Locale locale) {
    List<String> finalRoleCodes =
        roleCodes == null || roleCodes.isEmpty()
            ? Arrays.asList(CurrentBizUserResolver.ROLE_TEAM_OPERATOR)
            : roleCodes.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();

    if (finalRoleCodes.isEmpty()) {
      throw new JeecgBootException(messageSource.getMessage("common.no_permission", null, locale));
    }

    List<SysRole> teamRoles =
        sysRoleService.list(new QueryWrapper<SysRole>().in("role_code", finalRoleCodes));
    if (teamRoles.size() != finalRoleCodes.size()) {
      throw new JeecgBootException("Team roles not initialized, please run V1.0.5 SQL first");
    }
    return teamRoles;
  }

  @Data
  public static class TeamMemberListRequest {
    private Integer pageNo;
    private Integer pageSize;
    private Integer status;
    private String searchText;
    private String ownerUserId;
  }

  @Data
  public static class TeamMemberCreateRequest {
    private String ownerUserId;
    private String username;
    private String realname;
    private String password;
    private String email;
    private String phoneCode;
    private String phone;
    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd")
    private Date birthday;
    private Integer sex;
    private List<String> roleCodes;
  }

  @Data
  public static class TeamMemberStatusRequest {
    private String ownerUserId;
    private String memberUserId;
    private Integer status;
  }

  @Data
  public static class TeamMemberUpdateRequest {
    private String ownerUserId;
    private String memberUserId;
    private String realname;
    private String email;
    private String phoneCode;
    private String phone;
    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd")
    private Date birthday;
    private Integer sex;
    private String avatar;
  }

  @Data
  public static class TeamMemberRolesRequest {
    private String ownerUserId;
    private String memberUserId;
    private List<String> roleCodes;
  }

  @Data
  public static class TeamMemberResetPasswordRequest {
    private String ownerUserId;
    private String memberUserId;
    private String password;
  }

  @Data
  public static class TeamMemberUnbindRequest {
    private String ownerUserId;
    private String memberUserId;
  }
}
