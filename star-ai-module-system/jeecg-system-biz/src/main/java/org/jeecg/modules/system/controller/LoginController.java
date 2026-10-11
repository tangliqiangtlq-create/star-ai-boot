package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.FailCountLimit;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.aspect.annotation.H8LoginAudit;
import org.jeecg.common.aspect.enumeration.SecondAuthType;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.enem.EmailScene;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.*;
import org.jeecg.common.util.encryption.AesEncryptUtil;
import org.jeecg.common.util.encryption.EncryptedString;
import org.jeecg.common.util.security.OpaqueMqCodec;
import org.jeecg.common.system.security.C7PreLoginInternalBlocker;
import org.jeecg.common.valid.annotation.ValidDictionary;
import org.jeecg.config.BusinessRuleConfig;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.base.service.IBusinessRuleService;
import org.jeecg.modules.system.enem.SceneType;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserMfa;
import org.jeecg.modules.system.model.SysLoginModel;
import org.jeecg.modules.system.service.IAccountCreatedEmailService;
import org.jeecg.modules.system.service.ISysDictService;
import org.jeecg.modules.system.service.ISysLogService;
import org.jeecg.modules.system.service.ISysUserMfaService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.system.service.crypto.MfaSeedCryptoService;
import org.jeecg.modules.system.service.impl.SysBaseApiImpl;
import org.jeecg.modules.system.util.RandImageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author scott
 *
 * @since 2018-12-17
 */
@RestController
@RequestMapping("/sys")
@Api(tags = "User login")
@Slf4j
@Validated
public class LoginController {

  private static final String MFA_LOGIN_CHALLENGE_PREFIX = "sys:mfa:login:challenge:";
  private static final String MFA_LOGIN_CHALLENGE_USED_PREFIX = "sys:mfa:login:challenge_used:";
  private static final long MFA_LOGIN_CHALLENGE_TTL_SECONDS = 180L;
  private static final int MFA_TOTP_FAIL_LOCK_THRESHOLD = 5;
  private static final int MFA_TOTP_LOCK_MINUTES = 5;
  private static final String MFA_ERR_TOTP_INVALID = "MFA_TOTP_INVALID";
  private static final String MFA_ERR_CHALLENGE_EXPIRED = "MFA_CHALLENGE_EXPIRED";
  private static final String MFA_ERR_CHALLENGE_USED = "MFA_CHALLENGE_USED";
  private static final String MFA_ERR_TOTP_LOCKED = "MFA_TOTP_LOCKED";
  /**
   * B7-01 / C-P0-2: challenge after password/email when TOTP is already enabled — finish via
   * {@code /sys/verifyTotp}. Login must NOT force TOTP enrollment (not an A4/B5 Done gate).
   */
  private static final String MFA_PURPOSE_LOGIN = "LOGIN";

  @Autowired private ISysUserService sysUserService;
  @Autowired private org.jeecg.modules.system.mapper.SysUserMapper sysUserMapper;
  @Autowired private ISysUserMfaService sysUserMfaService;
  @Autowired private org.jeecg.modules.system.service.impl.SessionKickService sessionKickService;
  @Autowired private org.jeecg.common.util.security.OtpRateService otpRateService;
  @Autowired private MfaSeedCryptoService mfaSeedCryptoService;
  @Autowired private SysBaseApiImpl sysBaseApi;
  @Autowired private ISysLogService logService;
  @Autowired private RedisUtil redisUtil;

  @Autowired private ISysDictService sysDictService;
  @Resource private BaseCommonService baseCommonService;
  @Autowired private JeecgBaseConfig jeecgBaseConfig;
  @Autowired private MessageSource messageSource;

  @Autowired private RocketMQUtil rocketMQUtil;
  @Autowired private OTPHasher otpHasher;

  @Autowired private CommonAPI commonAPI;

  @Resource private ISysBaseAPI sysBaseAPI;

  @Resource private IBusinessRuleService businessRuleService;

  @Autowired private IdGeneratorSnowflake idGeneratorSnowflake;

  @Autowired private org.jeecg.modules.system.util.PaymentPasswordUtil paymentPasswordUtil;

  @Autowired private org.jeecg.modules.system.util.RsaPasswordUtil rsaPasswordUtil;

  @Autowired private IAccountCreatedEmailService accountCreatedEmailService;

  @Autowired(required = false)
  private org.jeecg.common.aspect.service.impl.AmountBasedSecondAuthResolver amountBasedSecondAuthResolver;

  @Value("${spring.profiles.active}")
  private String active;

  @Value("${register.enabled:false}")
  private boolean registerEnabled;

  @Value("${register.whitelist-phones:}")
  private String registerWhitelistPhonesRaw;

  @Autowired private org.springframework.core.env.Environment environment;

  private final String BASE_CHECK_CODES =
      "qwertyuiplkjhgfdsazxcvbnmQWERTYUPLKJHGFDSAZXCVBNM1234567890";

  @PostConstruct
  public void logRegisterFeatureFlag() {
    int whitelistCount = 0;
    if (registerWhitelistPhonesRaw != null && !registerWhitelistPhonesRaw.isEmpty()) {
      for (String p : registerWhitelistPhonesRaw.split(",")) {
        if (p != null && p.trim().length() > 0) {
          whitelistCount++;
        }
      }
    }
    log.info("REGISTER_ENABLED={} WHITELIST_COUNT={}", registerEnabled, whitelistCount);
  }

  @ApiOperation("Login API")
  @RequestMapping(value = "/login", method = RequestMethod.POST)
  @H8LoginAudit("Login API: /sys/login")
  public Result<JSONObject> login(
      @Valid @RequestBody SysLoginModel sysLoginModel,
      HttpServletRequest request,
      HttpServletResponse response) {

    Locale locale = LocaleResolver.getCurrLocale();

    Result<JSONObject> result = new Result<JSONObject>();
    String username = sysLoginModel.getUsername();
    String password = sysLoginModel.getPassword();

    C7PreLoginInternalBlocker.assertAllowedForInteractiveLogin(username);

    if (!org.jeecg.common.util.CredentialGuard.hasText(username)
        || !org.jeecg.common.util.CredentialGuard.isPresentLoginPassword(password)) {
      log.warn("LOGIN_FAIL reason=EMPTY_CREDENTIALS username=[{}]", username);
      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    // G05: count every credentialed login attempt (incl. captcha-gated) toward LOCK_TOO_MANY_LOGIN
    businessRuleService.registerFailedAttempt(BusinessRuleConfig.PASSWORD, username, request);
    if (businessRuleService.isAccountLocked(username, request)) {
      log.warn("LOGIN_FAIL reason=LOCK_TOO_MANY_LOGIN username=[{}]", username);
      response.setStatus(429);
      return Result.error(429, "LOCK_TOO_MANY_LOGIN");
    }

    // If RSA/SM2 cache key provided, password is encrypted and must be decrypted.
    // Do NOT evict the private key until auth succeeds — otherwise a wrong-password attempt
    // (or captcha retry) kills the key and the next submit with the same rsaCacheKey fails
    // decrypt → opaque "Authentication failed".
    String transportKey = sysLoginModel.getRsaCacheKey();
    if (StrUtil.isNotBlank(transportKey)) {
      String decryptedPassword = rsaPasswordUtil.decryptPassword(transportKey, password);
      if (decryptedPassword == null
          || !org.jeecg.common.util.CredentialGuard.isPresentLoginPassword(decryptedPassword)) {
        log.warn(
            "LOGIN_FAIL reason=TRANSPORT_DECRYPT username=[{}] rsaCacheKey=[{}] cipherLen={}",
            username,
            transportKey,
            password == null ? 0 : password.length());
        return Result.error(
            messageSource.getMessage("common.authentication_failed_msg", null, locale));
      }
      password = decryptedPassword;
    }

    if (businessRuleService.isAccountLocked(username, request)) {
      // G05: locked account must be HTTP 429 + LOCK_TOO_MANY_LOGIN (not soft 200+error body)
      log.warn("LOGIN_FAIL reason=ACCOUNT_LOCKED username=[{}]", username);
      response.setStatus(429);
      return Result.error(429, "LOCK_TOO_MANY_LOGIN");
    }

    // step.1 captcha check — B5: force captcha only after N consecutive password failures
    String captcha = sysLoginModel.getCaptcha();
    String realKey = null;
    boolean captchaRequired =
        businessRuleService.isCaptchaRequired(BusinessRuleConfig.PASSWORD, username, request);
    if (captchaRequired && StrUtil.isBlank(captcha)) {
      log.warn("LOGIN_FAIL reason=CAPTCHA_REQUIRED username=[{}]", username);
      return Result.error(messageSource.getMessage("common.verifyCodeError", null, locale));
    }
    if (StrUtil.isNotBlank(captcha)) {
      String lowerCaseCaptcha = captcha.toLowerCase();
      // Add secret key as obfuscation to avoid simple concatenation abuse; user can customize this key
      String origin =
          lowerCaseCaptcha + sysLoginModel.getCheckKey() + jeecgBaseConfig.getSignatureSecret();
      realKey = Md5Util.md5Encode(origin, "utf-8");
      Object checkCode = redisUtil.get(realKey);
      boolean captchaOk = false;
      if (checkCode != null) {
        String sv = checkCode.toString();
        if (StrUtil.equalsAnyIgnoreCase(sv, lowerCaseCaptcha)) {
          captchaOk = true; // Transitional plain-equals compatibility: legacy captcha codes expire naturally within 11 minutes
        } else {
          captchaOk = otpHasher.verify(lowerCaseCaptcha, sv, "RANDOM_IMAGE", realKey);
        }
      }
      if (!captchaOk) {
        log.warn(
            "LOGIN_FAIL reason=CAPTCHA_MISMATCH username=[{}] checkKey=[{}] redisHasCode={}",
            username,
            sysLoginModel.getCheckKey(),
            checkCode != null);
        businessRuleService.registerFailedAttempt(BusinessRuleConfig.CAPTCHA, username, request);
        return Result.error(messageSource.getMessage("common.verifyCodeError", null, locale));
      }
    } else {
      log.debug("User [{}] login skipped captcha (no prior failures)", username);
    }

    // step.2 check user exists and is valid
    // P4: credential verification needs password/salt, which are @TableField(select=false)
    // and therefore absent from MP auto SELECT. XML getUserByName (SELECT *) is safe here:
    // /sys/login is anonymous, so C6 column gate short-circuits.
    SysUser sysUser = sysUserService.getUserByName(username);
    result = sysUserService.checkUserIsEffective(sysUser);
    if (!result.isSuccess()) {
      log.warn(
          "LOGIN_FAIL reason=USER_INEFFECTIVE username=[{}] message=[{}]",
          username,
          result.getMessage());
      return result;
    }

    String syspassword = sysUser.getPassword();
    // P3: single source of credential truth shared with /sys/api/verifyPassword (S2S).
    boolean passwordMatches =
        org.jeecg.common.util.CredentialGuard.hasStoredPasswordHash(syspassword)
            && SmCryptoUtil.verifyPasswordWithLegacyFallback(
                username, password, sysUser.getSalt(), syspassword);
    if (!passwordMatches) {
      log.warn(
          "LOGIN_FAIL reason=BAD_PASSWORD username=[{}] storedFmt={} storedLen={} saltLen={} plainLen={}",
          username,
          storedPasswordFormat(syspassword),
          syspassword == null ? 0 : syspassword.length(),
          sysUser.getSalt() == null ? 0 : sysUser.getSalt().length(),
          password == null ? 0 : password.length());
      // G05 counter already incremented at request entry (covers captcha-gated retries).
      if (businessRuleService.isAccountLocked(username, request)) {
        response.setStatus(429);
        return Result.error(429, "LOCK_TOO_MANY_LOGIN");
      }
      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    // Transport private key is one-shot after successful password verify
    if (StrUtil.isNotBlank(transportKey)) {
      rsaPasswordUtil.evictPrivateKey(transportKey);
    }

    // C4: role queries (sys_user_role/sys_role) are tenant-scoped; establish context from the
    // authenticated user before validateTeamMemberLogin / getRole, otherwise login 500s with
    // C4_TENANT_CONTEXT_NULL (header may be absent on /sys/login).
    Integer loginTenantId = sysUser.getLoginTenantId();
    if (loginTenantId != null) {
      TenantContext.setTenant(String.valueOf(loginTenantId));
    } else {
      TenantContext.setTenant(String.valueOf(CommonConstant.TENANT_ID_DEFAULT_VALUE));
    }

    String uaStr = request.getHeader("User-Agent");

    UserAgent ua = UserAgentUtil.parse(uaStr);

    String platform = ua.getPlatform().toString();

    Result<?> teamLoginCheck = validateTeamMemberLogin(sysUser, locale);
    if (teamLoginCheck != null) {
      return (Result<JSONObject>) teamLoginCheck;
    }

    // B7-01: only when TOTP is already enabled must login challenge for second factor.
    // Disabled / pre-assigned seeds do not block login (bind later via /sys/mfa/totp/setup/*).
    SysUserMfa totpMfa = sysUserMfaService.findEnabledTotp(sysUser.getId(), 0).orElse(null);
    if (totpMfa != null) {
      Date now = new Date();
      if (sysUserMfaService.isLocked(totpMfa, now)) {
        return Result.error(
            messageSource.getMessage("common.authentication_failed_msg", null, locale));
      }
      String challengeId = createMfaLoginChallenge(sysUser.getId(), "PASSWORD");
      JSONObject obj = new JSONObject();
      obj.put("status", "mfa_required");
      obj.put("mfa_required", true);
      obj.put("challenge_id", challengeId);
      obj.put("next_factor", "TOTP");
      result.setResult(obj);
      result.success("");
      if (StrUtil.isNotBlank(realKey)) {
        redisUtil.del(realKey);
      }
      redisUtil.del(CommonConstant.LOGIN_FAIL + username);
      businessRuleService.resetFailedAttempts(BusinessRuleConfig.PASSWORD, username, request);
      businessRuleService.resetFailedAttempts(BusinessRuleConfig.CAPTCHA, username, request);
      baseCommonService.addLog("Username: " + username + ",TOTP verification required", CommonConstant.LOG_TYPE_1, null);
      return result;
    }

    // step.4 fetch user info after successful login
    LoginUser loginUser = userInfo(sysUser, result, platform);

    // step.5 delete captcha after successful login
    if (StrUtil.isNotBlank(realKey)) {
      redisUtil.del(realKey);
    }
    redisUtil.del(CommonConstant.LOGIN_FAIL + username);
    businessRuleService.resetFailedAttempts(BusinessRuleConfig.PASSWORD, username, request);
    businessRuleService.resetFailedAttempts(BusinessRuleConfig.CAPTCHA, username, request);

    baseCommonService.addLog(
        "Username: " + username + ",login successful!", CommonConstant.LOG_TYPE_1, null, loginUser);
    return result;
  }

  /** 【vue3 only】GetUserInfo */
  @PostMapping("/user/getUserInfo")
  public Result<JSONObject> getUserInfo(HttpServletRequest request) {
    long start = System.currentTimeMillis();
    Result<JSONObject> result = new Result<JSONObject>();

    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
    if (oConvertUtils.isNotEmpty(loginUser.getUsername())) {
      // Query based on usernameUserInfo — C6-safe variant: only firewall-allowed columns
      // (no password/salt/reason/is_sender/wallet_address/financial_state/kyc_attachment_*).
      // /sys/user/getUserInfo is post-login, so SELECT * would be blocked by C6-2 for non-admin
      // users AND would leak credential/PII fields into the JSON response.
      SysUser sysUser = sysUserService.getUserByNameForInfo(loginUser.getUsername());
      JSONObject obj = new JSONObject();
      log.info("1 Fetch user info elapsed (basic user info)" + (System.currentTimeMillis() - start) + "ms");

      // update-begin---author:scott ---date:2022-06-20 for: vue3 front-end, support custom home page -----------
      String vue3Version = request.getHeader(CommonConstant.VERSION);
      // update-begin---author:liusq ---date:2022-06-29
      // for: API return value changed; sync update judgment logic here -----------
      List<String> roleCodes = sysUserService.getRole(loginUser.getUsername());
      populateLoginUserScope(loginUser, roleCodes);
      // NOTE: Jeecg 门户角色首页(SysRoleIndex)已清理, 前端自行决定首页路径
      // update-begin---author:liusq ---date:2022-06-29
      // for: API return value changed; sync update judgment logic here -----------
      // update-end---author:scott ---date::2022-06-20
      // for: vue3 front-end, support custom home page --------------
      log.info("2 Fetch user info elapsed (home page config)" + (System.currentTimeMillis() - start) + "ms");

      SysUserMfa totpMfa = sysUserMfaService.findEnabledTotp(sysUser.getId(), 0).orElse(null);

      obj.put("userInfo", sysUser);
      obj.put("teamMember", Boolean.TRUE.equals(loginUser.getTeamMember()));
      obj.put("bizOwnerUserId", loginUser.getBizOwnerUserId());
      obj.put("totpEnabled", totpMfa != null);
      obj.put("sysAllDictItems", sysDictService.queryAllDictItems());
      log.info("3 GetUserInfoElapsed (DictionaryData)" + (System.currentTimeMillis() - start) + "ms");

      result.setResult(obj);
      result.success("");
    }
    log.info("end GetUserInfoElapsed " + (System.currentTimeMillis() - start) + "ms");
    return result;
  }

  /**
   * Logout
   *
   * @return
   */
  @PostMapping(value = "/logout")
  @AutoLog(value = "logout")
  public Result<Object> logout(HttpServletRequest req, HttpServletResponse resp) {
    String userId = null;
    try {
      if (StpUtil.isLogin()) {
        userId = StpUtil.getLoginIdAsString();
      }
    } catch (Exception ignore) {
    }
    try {
      StpUtil.logout();
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    if (StrUtil.isNotBlank(userId)) {
      try {
        sessionKickService.kickAllDevices(userId, "user_logout");
      } catch (Exception ignore) {
      }
    }
    // B9-3: drop any satoken cookie regardless of how Sa-Token was configured
    // (defense-in-depth: Sa-Token cookie mode + legacy localStorage migrations).
    clearAllSatokenCookies(req, resp);

    return Result.OK();
  }

  private static final Set<String> LOGOUT_COOKIE_NAMES =
      Set.of(
          "satoken",
          "Satoken",
          "SA-TOKEN",
          "satoken_client",
          "satoken_backoffice",
          "token",
          "access_token");

  static void clearAllSatokenCookies(HttpServletRequest req, HttpServletResponse resp) {
    if (resp == null) return;
    Set<String> toClear = new HashSet<>(LOGOUT_COOKIE_NAMES);
    try {
      Object cfgObj = SaManager.getConfig();
      if (cfgObj instanceof cn.dev33.satoken.config.SaTokenConfig) {
        cn.dev33.satoken.config.SaTokenConfig cfg =
            (cn.dev33.satoken.config.SaTokenConfig) cfgObj;
        String name = cfg.getTokenName();
        if (StrUtil.isNotBlank(name)) toClear.add(name);
      }
    } catch (Exception ignore) {
    }
    String contextPath = req == null ? null : req.getContextPath();
    String path = StrUtil.isBlank(contextPath) ? "/" : contextPath;
    for (String name : toClear) {
      jakarta.servlet.http.Cookie c = new jakarta.servlet.http.Cookie(name, "");
      c.setMaxAge(0);
      c.setPath(path);
      c.setHttpOnly(true);
      c.setSecure(!"false".equalsIgnoreCase(System.getProperty("jeecg.cookie.secure.override", "")));
      try {
        c.setAttribute("SameSite", "Strict");
      } catch (Exception ignore) {
      }
      resp.addCookie(c);
      // Also emit Set-Cookie header string (browsers accept multiple headers; the empty/expire
      // entry wins)
      StringBuilder sb = new StringBuilder();
      sb.append(name).append("=; Path=").append(path);
      sb.append("; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
      sb.append("; HttpOnly; SameSite=Strict");
      if (c.getSecure()) sb.append("; Secure");
      resp.addHeader("Set-Cookie", sb.toString());
    }
  }

  /**
   * Get visit count
   *
   * @return
   */
  @PostMapping("loginfo")
  public Result<JSONObject> loginfo() {
    Result<JSONObject> result = new Result<JSONObject>();
    JSONObject obj = new JSONObject();
    // update-begin--Author:zhangweijian Date:20190428 for：pass begin/end time params
    // Get start and end of day
    Calendar calendar = new GregorianCalendar();
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    Date dayStart = calendar.getTime();
    calendar.add(Calendar.DATE, 1);
    Date dayEnd = calendar.getTime();
    // Get system access records
    Long totalVisitCount = logService.findTotalVisitCount();
    obj.put("totalVisitCount", totalVisitCount);
    Long todayVisitCount = logService.findTodayVisitCount(dayStart, dayEnd);
    obj.put("todayVisitCount", todayVisitCount);
    Long todayIp = logService.findTodayIp(dayStart, dayEnd);
    // update-end--Author:zhangweijian Date:20190428 for：pass begin/end time params
    obj.put("todayIp", todayIp);
    result.setResult(obj);
    return result;
  }

  /**
   * Get visit count
   *
   * @return
   */
  @PostMapping("/visitInfo")
  public Result<List<Map<String, Object>>> visitInfo() {
    Result<List<Map<String, Object>>> result = new Result<List<Map<String, Object>>>();
    Calendar calendar = new GregorianCalendar();
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    calendar.add(Calendar.DAY_OF_MONTH, 1);
    Date dayEnd = calendar.getTime();
    calendar.add(Calendar.DAY_OF_MONTH, -7);
    Date dayStart = calendar.getTime();
    List<Map<String, Object>> list = logService.findVisitCount(dayStart, dayEnd);
    result.setResult(oConvertUtils.toLowerCasePageList(list));
    return result;
  }

  /**
   * UserInfo
   *
   * @param sysUser
   * @param result
   * @return
   */
  /**
   * UserInfo
   *
   * @param sysUser
   * @param result
   */
  private LoginUser userInfo(
      SysUser sysUser, Result<JSONObject> result, Boolean writeToken, String device) {

    // Keep C4 tenant context for MFA / dict / role queries during login session bootstrap.
    Integer loginTenantId = sysUser.getLoginTenantId();
    if (loginTenantId != null) {
      TenantContext.setTenant(String.valueOf(loginTenantId));
    } else if (TenantContext.getTenant() == null || TenantContext.getTenant().isBlank()) {
      TenantContext.setTenant(String.valueOf(CommonConstant.TENANT_ID_DEFAULT_VALUE));
    }

    // GetUserDepartmentInfo
    JSONObject obj = new JSONObject(new LinkedHashMap<>());

    if (writeToken) {

      StpUtil.login(sysUser.getId(), device);
    }

    // step.6 record user login log
    LoginUser loginUser = new LoginUser();
    BeanUtil.copyProperties(sysUser, loginUser);
    // SEC: BeanUtil copies the credential material straight off SysUser. Never let it ride
    // along with the login payload: `obj` below is serialized into the /sys/login response and
    // the same instance is stored in the session cache. Cleared before either use.
    loginUser.setPassword(null);
    loginUser.setSalt(null);
    SysUserMfa totpMfa = sysUserMfaService.findEnabledTotp(sysUser.getId(), 0).orElse(null);
    loginUser.setTotpEnabled(totpMfa != null);

    loginUser.setCustomerType_dictText(
        commonAPI.translateDict("customerType", sysUser.getCustomerType()));

    if (writeToken) {

      StpUtil.getSession().set("loginUser", loginUser);
    }

    obj.put("status", "success");
    obj.put("userInfo", loginUser);

    List<String> roleCodes = sysUserService.getRole(loginUser.getUsername());
    populateLoginUserScope(loginUser, roleCodes);

    if (writeToken) {
      StpUtil.getSession().set("loginUser", loginUser);
    }

    result.setResult(obj);
    result.success(I18nUtil.getMessage("login.loginSuccess"));

    SysUser update = new SysUser();

    update.setId(sysUser.getId());
    update.setLastLoginTime(new Date());

    sysUserService.updateById(update);

    return loginUser;
  }

  private LoginUser userInfo(SysUser sysUser, Result<JSONObject> result, String device) {

    return userInfo(sysUser, result, true, device);
  }

  // A4-1: /sys/getEncryptedString removed — static AES key/iv must never be served.
  // MFA seeds use MfaSeedCryptoService; response encryption uses setEncryptionKey (SM2/SM4).

  /**
   * Backend generate captcha image: valid
   *
   * @param response
   * @param key
   */
  @ApiOperation("Get verification code")
  @PostMapping(value = "/randomImage/{key}")
  public Result<String> randomImage(HttpServletResponse response, @PathVariable("key") String key) {
    Result<String> res = new Result<String>();
    try {
      // Generate verification code
      String code = RandomUtil.randomString(BASE_CHECK_CODES, 4);
      // Store in Redis
      String lowerCaseCode = code.toLowerCase();

      // update-begin-author:taoyan date:2022-9-13 for: VUEN-2245 [Vulnerability] New vulnerability pending fix 20220906
      // Add secret key as obfuscation to avoid simple concatenation abuse; user can customize this key
      String origin = lowerCaseCode + key + jeecgBaseConfig.getSignatureSecret();
      String realKey = Md5Util.md5Encode(origin, "utf-8");
      // update-end-author:taoyan date:2022-9-13 for: VUEN-2245 [Vulnerability] New vulnerability pending fix 20220906

      String imageHash = otpHasher.hash(lowerCaseCode, "RANDOM_IMAGE", realKey);
      redisUtil.set(realKey, imageHash, 60);
      String keySha256Prefix16 =
          (realKey != null)
              ? org.apache.commons.codec.digest.DigestUtils.sha256Hex(realKey).substring(0, 16)
              : "null";
      log.info(
          "Get verification code, keySha256Prefix16={}, codeLen={}",
          keySha256Prefix16,
          (code != null ? code.length() : 0));
      // Return to front-end
      String base64 = RandImageUtil.generate(code);
      res.setSuccess(true);
      res.setResult(base64);
    } catch (Exception e) {
      log.error("operation failed", e);
      res.error500(I18nUtil.getMessage("login.getCaptchaFailed"));
      return res;
    }
    return res;
  }

  /**
   * Forgot password API
   *
   * @return Result password recovery result
   */
  @SneakyThrows
  @ApiOperation("Forgot password API")
  @PostMapping("/recoverPassword")
  @SaIgnore
  @AutoLog("Forgot password")
  public Result<String> recoverPassword(
      @Email @RequestParam String email,
      @RequestParam String verificationCode,
      @RequestParam String newPassword,
      @RequestParam(required = false) String rsaCacheKey,
      HttpServletRequest request) {
    // Get current locale
    Locale locale = LocaleResolver.getCurrLocale();

    Result<String> result = new Result<>();

    if (businessRuleService.isAccountLocked(email, request)) {

      log.info("Forgot password, account is locked: {}", email);

      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    // Verification code check
    String redisKey = CommonConstant.EMAIL_RESET_PWD_REDIS_KEY_PRE + email;
    Object codeInRedis = redisUtil.get(redisKey);
    boolean otpOk = false;
    if (codeInRedis != null) {
      String sv = String.valueOf(codeInRedis);
      if (sv.equals(verificationCode)) {
        otpOk = true; // Transitional plain-equals compatibility: legacy captcha codes expire naturally within 11 minutes
      } else {
        otpOk = otpHasher.verify(verificationCode, sv, "RESET_PASSWORD", email);
      }
    }
    if (!otpOk) {

      log.info("Forgot password, verification code incorrect: {}", email);

      businessRuleService.registerFailedAttempt(BusinessRuleConfig.EMAIL_OTP, email, request);

      return Result.error(messageSource.getMessage("common.verifyCodeError", null, locale));
    }

    // Check whether email exists
    LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(SysUser::getEmail, email);
    SysUser user = sysUserService.getOne(queryWrapper);
    if (user == null) {

      log.info("Forgot password, email does not exist: {}", email);

      businessRuleService.registerFailedAttempt(
          BusinessRuleConfig.EMAIL_VERIFICATION, email, request);

      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    // If RSA cache key provided, password is encrypted and must be decrypted
    if (StrUtil.isNotBlank(rsaCacheKey)) {
      String decryptedPassword = rsaPasswordUtil.decryptPassword(rsaCacheKey, newPassword);
      if (decryptedPassword == null) {
        log.warn("Forgot password failed, RSA password decryption failed: {}", email);
        return Result.error(
            messageSource.getMessage("common.authentication_failed_msg", null, locale));
      }
      newPassword = decryptedPassword;
      // Delete private key after successful decryption
      rsaPasswordUtil.decryptPasswordAndClean(rsaCacheKey, newPassword);
    }

    // Validate password format
    if (!newPassword.matches(CommonConstant.PASSWORD_REGEX)) {
      log.warn("Forgot password failed, invalid password format: {}", email);
      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    String password = SmCryptoUtil.hashPassword(newPassword);

    // Update user password
    user.setPassword(password);
    user.setLastPasswordChangeTime(new Date());
    sysUserService.updateById(user);

    // Delete captcha from Redis
    redisUtil.del(redisKey);

    // AddLog
    baseCommonService.addLog(
        messageSource.getMessage(
            "loginController.recoverPassword.successLog", new Object[] {email}, locale),
        CommonConstant.LOG_TYPE_1,
        null);

    String successMessage =
        messageSource.getMessage("loginController.recoverPassword.success", null, locale);

    businessRuleService.resetFailedAttempts(BusinessRuleConfig.EMAIL_OTP, email, request);

    redisUtil.del(StrUtil.format("sys:cache:encrypt:user::{}", user.getUsername()));

    // Password recovery is anonymous; do not call StpUtil.getSession() (throws NotLoginException when not logged in)
    return result.success(successMessage);
  }

  /**
   * Email registration API
   *
   * @return Result registration result
   */
  /**
   * Email registration API.
   *
   * <p>Ember R1: public self-service registration is disabled; access is via Admin user creation ({@code opsCreateUser}) / Request access. Method signature and route are retained for legacy client compatibility, but always returns "access
   * is by request only" and no longer persists account creation. Public branding config: {@link #rampPublicConfig()}.
   *
   * @return Result always fails: access is by request only
   */
  @SneakyThrows
  @ApiOperation("Email registration API (closed; invited customers only)")
  @PostMapping("/registerByEmail")
  @AutoLog("Email registration")
  public Result registerByEmail(
      @Email @RequestParam String email,
      @RequestParam String password,
      @ValidDictionary(code = "customerType") @RequestParam String customerType,
      @RequestParam String verificationCode,
      @RequestParam(required = false) String inviteCode,
      @RequestParam(required = false) String rsaCacheKey,
      HttpServletRequest request) {

    Locale locale = LocaleResolver.getCurrLocale();

    log.info("Public self-registration is disabled, rejecting registration request: {}", email);

    return Result.error(
        messageSource.getMessage("loginController.registerByEmail.accessByRequestOnly", null, locale));
  }

  /**
   * Ramp public brand config (accessible without login; no sensitive data).
   *
   * <p>For login page / Request access hints and other unauthenticated reads of brand name, Request access URL, and public domain (Q2).
   *
   * @return brandName / requestAccessUrl / publicDomain / supportEmail
   */
  @ApiOperation("Ramp public brand config")
  @PostMapping("/rampPublicConfig")
  public Result<Map<String, String>> rampPublicConfig() {

    Map<String, String> config = new HashMap<>();
    config.put("brandName", jeecgBaseConfig.getBrandName());
    config.put("requestAccessUrl", jeecgBaseConfig.getRequestAccessUrl());
    config.put("publicDomain", jeecgBaseConfig.getPublicDomain());
    // Login "Request access" fallback dialog (Q2): show when requestAccessUrl is blank
    config.put("supportEmail", jeecgBaseConfig.getContactSupportEmail());

    return Result.OK(config);
  }

  @SneakyThrows
  @ApiOperation("Email registration API")
  @PostMapping("/agentRegisterByEmail")
  @AutoLog("Agent registers user")
  @SaCheckPermission("system:user:agentRegisterByEmail")
  public Result agentRegisterByEmail(
      @Email @RequestParam String email,
      @ValidDictionary(code = "customerType") @RequestParam String customerType,
      @RequestParam(defaultValue = "false") Boolean sendEmail,
      @RequestParam(defaultValue = "N") String isSender) {

    Result<String> result = new Result<>();

    // Get current locale
    Locale locale = LocaleResolver.getCurrLocale();

    // Check whether username already exists
    LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(SysUser::getUsername, email);
    if (sysUserService.getOne(queryWrapper) != null) {

      log.info("User registration failed, username already exists: {}", email);

      return Result.error(messageSource.getMessage("sysUser.registerFailed", null, locale));
    }

    // Check whether email is already registered
    queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(SysUser::getEmail, email);
    if (sysUserService.getOne(queryWrapper) != null) {

      log.info("User registration failed, email already registered: {}", email);

      return Result.error(messageSource.getMessage("sysUser.registerFailed", null, locale));
    }

    String placeholderSecret = IdUtil.fastSimpleUUID() + "!Unset!" + System.nanoTime();
    String hashedPassword = SmCryptoUtil.hashPassword(placeholderSecret);

    LoginUser agentUser = null;

    LoginUser user = sysBaseAPI.getUserById(StpUtil.getLoginIdAsString());

    if (StpUtil.hasRoleOr("MACKER", "CHECKER")) {

      agentUser = sysBaseAPI.getUserById(user.getDlygx());

    } else {
      agentUser = user;
    }

    String loginUserId = StpUtil.getLoginIdAsString();

    String roleCode = "user";

    // Create new user
    SysUser newUser = new SysUser();
    newUser.setUsername(email);
    newUser.setEmail(email);
    newUser.setCustomerType(customerType);
    newUser.setDelFlag(0);
    newUser.setStatus(1);
    newUser.setRealname(email);
    newUser.setPassword(hashedPassword);
    // Agent-assisted account opening: skip in-site KYC restriction; mark as approved directly
    newUser.setKycStatus("2");
    newUser.setInviterId(agentUser.getId());
    newUser.setIsSender(isSender);
    // Allow login if email sent; otherwise deny login
    newUser.setAllowLogin(sendEmail ? 1 : 2);
    newUser.setEmailNotice(2);

    // Save user to database

    String roleId = commonAPI.translateDictFromTable("sys_role", "id", "role_code", roleCode);

    sysUserService.saveUser(newUser, roleId, null, null);

    baseCommonService.addLog("User registration successful: " + email, CommonConstant.LOG_TYPE_1, null);

    if (sendEmail) {
      accountCreatedEmailService.scheduleAfterCommit(
          newUser,
          "OPS_RESET",
          loginUserId,
          "ACCOUNT_CREATED:agent:" + newUser.getId());
    }

    result.setSuccess(true);
    result.setCode(200);
    result.setResult(newUser.getId());

    cn.hutool.json.JSONObject message = new cn.hutool.json.JSONObject();
    message.set("userId", newUser.getId());
    message.set("roleCode", roleCode);

    rocketMQUtil.sendRetry("autoOpenAccount_topic", message, null);

    return result;
  }

  @ApiOperation("Agent updates user login permission")
  @PostMapping("/updateAgentUserAllowLogin")
  @AutoLog("Agent updates user login permission")
  public Result updateAgentUserAllowLogin(
      @RequestParam String userId, @RequestParam Integer allowLogin) {

    LoginUser agentUser = null;

    LoginUser user = sysBaseAPI.getUserById(StpUtil.getLoginIdAsString());

    if (StpUtil.hasRoleOr("MACKER", "CHECKER")) {

      agentUser = sysBaseAPI.getUserById(user.getDlygx());

    } else {
      agentUser = user;
    }

    SysUser sysUser = sysUserService.getById(userId);

    if (sysUser == null) {
      return Result.error(
          messageSource.getMessage("common.user_not_exist", null, LocaleResolver.getCurrLocale()));
    }

    if (!sysUser.getInviterId().equals(agentUser.getId())) {
      return Result.error(
          messageSource.getMessage(
              "common.modify_user_no_permission", null, LocaleResolver.getCurrLocale()));
    }

    sysUser.setAllowLogin(allowLogin);

    sysUserService.updateById(sysUser);

    return Result.OK(I18nUtil.getMessage("common.success.edit"));
  }

  @SneakyThrows
  @ApiOperation("Email verification code login")
  @PostMapping("/emailLogin")
  @AutoLog("Email verification code login")
  @H8LoginAudit("Email login: /sys/emailLogin")
  public Result emailLogin(
      @RequestParam String email,
      @RequestParam String verificationCode,
      HttpServletRequest request) {

    Result<JSONObject> result = new Result<>();

    // Get current locale
    Locale locale = LocaleResolver.getCurrLocale();

    email = StrUtil.trim(email);
    C7PreLoginInternalBlocker.assertAllowedForInteractiveLogin(email);
    if (StrUtil.isBlank(email)) {
      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    if (businessRuleService.isAccountLocked(email, request)) {

      log.info("Email login failed, account is locked: {}", email);

      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    // Verify email verification code (same identifier as sendVerificationCode)
    String redisKey = CommonConstant.EMAIL_LOGIN + email;
    Object codeInRedis = redisUtil.get(redisKey);
    boolean otpOk = false;
    if (codeInRedis != null) {
      String sv = String.valueOf(codeInRedis);
      if (sv.equals(verificationCode)) {
        otpOk = true; // Transitional plain-equals compatibility: legacy captcha codes expire naturally within 11 minutes
      } else {
        otpOk = otpHasher.verify(verificationCode, sv, "LOGIN", email);
      }
    }
    if (!otpOk) {

      businessRuleService.resetFailedAttempts(BusinessRuleConfig.EMAIL_OTP, email, request);

      return Result.error(
          messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    // Support lookup by login name or email
    SysUser sysUser = resolveEmailLoginUser(email);
    result = sysUserService.checkUserIsEffective(sysUser);
    if (!result.isSuccess()) {
      return result;
    }

    String uaStr = request.getHeader("User-Agent");

    UserAgent ua = UserAgentUtil.parse(uaStr);

    String platform = ua.getPlatform().toString();

    // B7-01: enabled TOTP only
    SysUserMfa totpMfa = sysUserMfaService.findEnabledTotp(sysUser.getId(), 0).orElse(null);
    if (totpMfa != null) {
      Date now = new Date();
      if (sysUserMfaService.isLocked(totpMfa, now)) {
        return Result.error(
            messageSource.getMessage("common.authentication_failed_msg", null, locale));
      }
      String challengeId = createMfaLoginChallenge(sysUser.getId(), "EMAIL_OTP");
      JSONObject obj = new JSONObject();
      obj.put("status", "mfa_required");
      obj.put("mfa_required", true);
      obj.put("challenge_id", challengeId);
      obj.put("next_factor", "TOTP");
      result.setResult(obj);
      result.success("");
      redisUtil.del(redisKey);
      baseCommonService.addLog("Email: " + email + ",TOTP verification required", CommonConstant.LOG_TYPE_1, null);
      return result;
    }

    userInfo(sysUser, result, platform);

    redisUtil.del(redisKey);

    return result;
  }

  /** Resolve user by login name or email. */
  private SysUser resolveEmailLoginUser(String account) {
    SysUser user =
        sysUserService.getOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, account));
    if (user != null) {
      return user;
    }
    return sysUserService.getUserByEmail(account);
  }

  @ApiOperation("Login TOTP verification")
  @PostMapping("/verifyTotp")
  @H8LoginAudit(value = "Login MFA verify: /sys/verifyTotp", endpoint = "/sys/mfa/verify")
  @org.jeecg.common.aspect.FailCountLimit(maxFailures = 5, lockDuration = 900, windowSeconds = 900, keyExpression = "#challengeId")
  public Result<JSONObject> verifyTotp(
      @RequestParam(name = "challenge_id") String challengeId,
      @RequestParam(name = "totp") String totp,
      HttpServletRequest request) {

    Locale locale = LocaleResolver.getCurrLocale();

    if (StrUtil.isBlank(challengeId) || StrUtil.isBlank(totp)) {
      return mfaError(
          400,
          messageSource.getMessage("common.authentication_failed_msg", null, locale),
          MFA_ERR_TOTP_INVALID,
          null,
          null);
    }

    String redisKey = MFA_LOGIN_CHALLENGE_PREFIX + StrUtil.trim(challengeId);
    String usedKey = MFA_LOGIN_CHALLENGE_USED_PREFIX + StrUtil.trim(challengeId);
    Object rawChallenge = redisUtil.get(redisKey);
    if (rawChallenge == null) {
      if (redisUtil.get(usedKey) != null) {
        return mfaError(409, "Challenge already used", MFA_ERR_CHALLENGE_USED, null, null);
      }
      return mfaError(410, "Challenge expired", MFA_ERR_CHALLENGE_EXPIRED, null, null);
    }

    JSONObject challenge;
    try {
      challenge = JSONObject.parseObject(rawChallenge.toString());
    } catch (Exception e) {
      // CSP-EXEMPT: T1 双杀清零-异常分支不删challenge
      // redisUtil.del(redisKey);
      return mfaError(410, "Challenge expired", MFA_ERR_CHALLENGE_EXPIRED, null, null);
    }

    String purpose = StrUtil.blankToDefault(challenge.getString("purpose"), MFA_PURPOSE_LOGIN);
    if (!MFA_PURPOSE_LOGIN.equals(purpose)) {
      // CSP-EXEMPT: T1 双杀清零-异常分支不删challenge
      // redisUtil.del(redisKey);
      return mfaError(410, "Challenge expired", MFA_ERR_CHALLENGE_EXPIRED, null, null);
    }

    String userId = challenge.getString("userId");
    if (StrUtil.isBlank(userId)) {
      // CSP-EXEMPT: T1 双杀清零-异常分支不删challenge
      // redisUtil.del(redisKey);
      return mfaError(410, "Challenge expired", MFA_ERR_CHALLENGE_EXPIRED, null, null);
    }

    SysUser sysUser = sysUserService.getById(userId);
    Result<JSONObject> effective = sysUserService.checkUserIsEffective(sysUser);
    if (!effective.isSuccess()) {
      return effective;
    }

    C7PreLoginInternalBlocker.assertAllowedForInteractiveLogin(sysUser.getUsername());

    SysUserMfa mfa = sysUserMfaService.findEnabledTotp(userId, 0).orElse(null);
    if (mfa == null || StrUtil.isBlank(mfa.getSecretEnc())) {
      return mfaError(400, "TOTP disabled", "MFA_TOTP_NOT_ENABLED", null, null);
    }

    Date now = new Date();
    if (sysUserMfaService.isLocked(mfa, now)) {
      return mfaLockedError(mfa);
    }

    String secret = mfaSeedCryptoService.tryDecryptWithAesThenMigrate(mfa);
    if (StrUtil.isBlank(secret)) {
      return Result.error(messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    Integer otp;
    try {
      otp = Integer.parseInt(StrUtil.trim(totp));
    } catch (Exception e) {
      return mfaError(400, "Verification code format error", MFA_ERR_TOTP_INVALID, null, null);
    }

    Date lastUsed = mfa.getLastUsedAt();
    if (lastUsed != null) {
      long delta30 = (now.getTime() / 30000L) - (lastUsed.getTime() / 30000L);
      if (delta30 <= 0) {
        return mfaError(400, "Verification code reused in the same 30s slot", "MFA_TOTP_REPLAY", null, null);
      }
    }

    GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();
    boolean ok = googleAuthenticator.authorize(secret, otp);
    if (!ok) {
      int fail = mfa.getFailCount() == null ? 0 : mfa.getFailCount();
      fail++;
      mfa.setFailCount(fail);
      if (fail >= MFA_TOTP_FAIL_LOCK_THRESHOLD) {
        mfa.setLockedUntil(DateUtil.offsetMinute(now, MFA_TOTP_LOCK_MINUTES));
      }
      mfa.setUpdateBy(sysUser.getUsername());
      mfa.setUpdateTime(now);
      sysUserMfaService.updateById(mfa);
      if (sysUserMfaService.isLocked(mfa, now)) {
        return mfaLockedError(mfa);
      }
      int remainingAttempts = Math.max(0, MFA_TOTP_FAIL_LOCK_THRESHOLD - fail);
      return mfaError(400, "Incorrect verification code", MFA_ERR_TOTP_INVALID, null, remainingAttempts);
    }

    mfa.setFailCount(0);
    mfa.setLockedUntil(null);
    mfa.setLastUsedAt(now);
    mfa.setUpdateBy(sysUser.getUsername());
    mfa.setUpdateTime(now);
    sysUserMfaService.updateById(mfa);

    redisUtil.set(usedKey, "1", MFA_LOGIN_CHALLENGE_TTL_SECONDS);
    redisUtil.del(redisKey);

    String uaStr = request.getHeader("User-Agent");
    UserAgent ua = UserAgentUtil.parse(uaStr);
    String platform = ua.getPlatform().toString();

    Result<?> teamLoginCheck = validateTeamMemberLogin(sysUser, locale);
    if (teamLoginCheck != null) {
      return (Result<JSONObject>) teamLoginCheck;
    }

    Result<JSONObject> result = new Result<>();
    LoginUser loginUser = userInfo(sysUser, result, platform);
    baseCommonService.addLog(
        "Username: " + sysUser.getUsername() + ",TOTP login successful!", CommonConstant.LOG_TYPE_1, null, loginUser);
    return result;
  }

  /** B7-01: short-lived Redis challenge after password/email when enabled TOTP must be verified. */
  private String createMfaLoginChallenge(String userId, String primaryFactor) {
    String challengeId = java.util.UUID.randomUUID().toString().replace("-", "");
    JSONObject payload = new JSONObject();
    payload.put("userId", userId);
    payload.put("primaryFactor", primaryFactor);
    payload.put("purpose", MFA_PURPOSE_LOGIN);
    payload.put("createdAt", System.currentTimeMillis());
    redisUtil.set(
        MFA_LOGIN_CHALLENGE_PREFIX + challengeId, payload.toJSONString(), MFA_LOGIN_CHALLENGE_TTL_SECONDS);
    return challengeId;
  }

  private Result<JSONObject> mfaError(
      int code, String message, String errorCode, Long remainingSeconds, Integer remainingAttempts) {
    Result<JSONObject> result = new Result<>();
    result.setSuccess(false);
    result.setCode(code);
    result.setMessage(message);
    JSONObject payload = new JSONObject();
    payload.put("errorCode", errorCode);
    if (remainingSeconds != null) {
      payload.put("remainingSeconds", remainingSeconds);
    }
    if (remainingAttempts != null) {
      payload.put("remainingAttempts", remainingAttempts);
    }
    result.setResult(payload);
    return result;
  }

  private Result<JSONObject> mfaLockedError(SysUserMfa mfa) {
    Date now = new Date();
    long remainingSeconds = 0;
    if (mfa.getLockedUntil() != null) {
      remainingSeconds = Math.max(0, (mfa.getLockedUntil().getTime() - now.getTime()) / 1000);
    }
    return mfaError(423, "Too many consecutive errors; please try again later", MFA_ERR_TOTP_LOCKED, remainingSeconds, null);
  }

  /**
   * Send verification code API; supports registration, password change, etc.
   *
   * @return
   */
  @SneakyThrows
  // @LimitSubmit(key = "sendVerificationCode:#email", limit = 30)
  @PostMapping(value = "/sendVerificationCode")
  @SaIgnore
  @AutoLog("Send verification code")
  public Result<String> sendVerificationCode(
      @Email @RequestParam(name = "email") String email,
      @RequestParam SceneType scene,
      jakarta.servlet.http.HttpServletRequest request) {

    Locale locale = LocaleResolver.getCurrLocale();

    log.info("Current locale: {}", locale);

    Result<String> result = new Result<>();

    String remoteIp = request == null ? "unknown" : IpUtils.getIpAddr(request);
    org.jeecg.common.util.security.OtpRateService.RateResult rr =
        otpRateService.checkAndReserve(email, remoteIp);
    if (!rr.allowed) {
      String errMsg;
      switch (rr.code) {
        case 1:
          errMsg = "OTP rate limited: please try again later (per-email limit)";
          break;
        case 2:
          errMsg = "OTP rate limited: please try again later (per-ip limit)";
          break;
        case 3:
          errMsg = "OTP rate limited: system busy, please try again later";
          break;
        default:
          errMsg = "OTP rate limited: please try again later";
      }
      return Result.error(
          messageSource.getMessage(
              "loginController.sendVerificationCode.tooFrequent",
              null,
              errMsg,
              locale));
    }

    if (oConvertUtils.isEmpty(email)) {
      String message =
          messageSource.getMessage("loginController.sendVerificationCode.emailEmpty", null, locale);
      result.setMessage(message);
      result.setSuccess(false);
      return result;
    }

    if (oConvertUtils.isEmpty(scene)) {
      String message =
          messageSource.getMessage("loginController.sendVerificationCode.sceneEmpty", null, locale);
      result.setMessage(message);
      result.setSuccess(false);
      return result;
    }

    // Define Redis key prefix by scenario
    String redisKeyPrefix;
    switch (scene) {
      case REGISTER:
        redisKeyPrefix = CommonConstant.EMAIL_REGISTER_REDIS_KEY_PRE;
        break;
      case CHANGE_PASSWORD:
        redisKeyPrefix = CommonConstant.EMAIL_CHANGE_PWD_REDIS_KEY_PRE;
        break;

      case RESET_PASSWORD:
        redisKeyPrefix = CommonConstant.EMAIL_RESET_PWD_REDIS_KEY_PRE;
        break;
      case LOGIN:
        redisKeyPrefix = CommonConstant.EMAIL_LOGIN;
        break;
      case TWO_FACTOR_AUTHENTICATION:
        redisKeyPrefix = CommonConstant.EMAIL_SECOND_AUTH;
        break;

      default:
        String message =
            messageSource.getMessage(
                "loginController.sendVerificationCode.unknownScene", null, locale);
        result.setMessage(message);
        result.setSuccess(false);
        return result;
    }

    // Second-auth scenario: force current loginuser email; keep consistent with openSafeWithAuth check logic
    if (scene == SceneType.TWO_FACTOR_AUTHENTICATION) {
      if (!StpUtil.isLogin()) {
        String message =
            messageSource.getMessage("common.authentication_failed_msg", null, locale);
        result.setMessage(message);
        result.setSuccess(false);
        return result;
      }
      SysUser currentUser = sysUserService.getById(StpUtil.getLoginIdAsString());
      if (currentUser == null) {
        return Result.error(I18nUtil.getMessage("login.userNotFound"));
      }
      if (StrUtil.isBlank(currentUser.getEmail())) {
        return Result.error(I18nUtil.getMessage("login.userEmailEmpty"));
      }
      email = StrUtil.trim(currentUser.getEmail());
    }

    String redisKey = redisKeyPrefix + email;
    String frequencyKey = CommonConstant.EMAIL_SEND_FREQUENCY_KEY + email;
    // CodePlan 36 / C2-3: send-frequency limiter (TTL 60s)
    if (redisUtil.hasKey(frequencyKey)) {
      return Result.error(
          messageSource.getMessage(
              "loginController.sendVerificationCode.tooFrequent",
              null,
              "Please wait before requesting another code",
              locale));
    }
    // B5-3: fixed 123456 only when OTP_ALLOW_FIXED_CODE=true (never by profile alone)
    String captcha = org.jeecg.common.util.security.OtpCodeGenerator.generateNumeric6(environment);
    log.info(
        "Verification code sent for email scene={}, fixedAllowed={}",
        scene,
        org.jeecg.common.util.security.OtpCodeGenerator.isFixedCodeAllowed(environment));

    try {
      // Simulate sending email without calling the actual mail API

      cn.hutool.json.JSONObject msg = new cn.hutool.json.JSONObject();

      msg.set("email", email);
      msg.set("emailScene", EmailScene.VERIFICATION_CODE.name());
      String hash = otpHasher.hash(captcha, scene.name(), email);
      redisUtil.set(redisKey, hash, 300);
      redisUtil.set(frequencyKey, "1", 60);
      // ttlSeconds=0 → configured transport TTL (default 120s, cap 300s); was hard-coded 10s
      // which could not survive MQ backlog/restart (2026-09-30). encodeForProducer now fails
      // fast if the Redis write fails, so the MQ message is only sent when decodable.
      OpaqueMqCodec.OpaqueResult opaque = OpaqueMqCodec.encodeForProducer(scene.name(), captcha, 0);
      OpaqueMqCodec.applyToJson(msg, opaque);
      if (scene == SceneType.TWO_FACTOR_AUTHENTICATION) {
        SysUser currentUser = sysUserService.getById(StpUtil.getLoginIdAsString());
        if (currentUser != null && StrUtil.isNotBlank(currentUser.getUsername())) {
          msg.set("username", currentUser.getUsername());
        }
      } else {
        SysUser byEmail = sysUserService.getUserByEmail(email);
        if (byEmail != null && StrUtil.isNotBlank(byEmail.getUsername())) {
          msg.set("username", byEmail.getUsername());
        }
      }

      rocketMQUtil.sendRetry("sendEmailTopic_topic", msg, null);

      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
      String message =
          messageSource.getMessage(
              "loginController.sendVerificationCode.emailSendError", null, locale);
      result.error500(message);
      return result;
    }
    return result;
  }

  @PostMapping("/getCurrUser")
  @SaCheckLogin
  @AutoLog("GetCurrentUserInfo")
  public Result getCurrUser() {

    LoginUser loginUser = sysBaseApi.getUserById(StpUtil.getLoginIdAsString());

    loginUser.setPassword(null);

    loginUser.setCustomerType_dictText(
        commonAPI.translateDict("customerType", loginUser.getCustomerType()));

    if (StrUtil.isNotBlank(loginUser.getInviterId())) {

      loginUser.setInviterId_dictText(
          commonAPI.translateDictFromTable("sys_user", "realname", "id", loginUser.getInviterId()));
    }

    List<SysRole> roleList = sysUserService.getRolesByUserId(loginUser.getId());
    List<String> roleCodes =
        roleList.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
    populateLoginUserScope(loginUser, roleCodes);
    loginUser.setPermissionTags(Collections.emptyList());

    String currUserId = CurrentBizUserResolver.getCurrUserId();
    LoginUser kycUser =
        StrUtil.equals(currUserId, loginUser.getId())
            ? loginUser
            : sysBaseApi.getUserById(currUserId);
    String kycStatus = kycUser != null ? kycUser.getKycStatus() : loginUser.getKycStatus();
    loginUser.setKycStatus(kycStatus);
    loginUser.setKycStatus_dictText(
        commonAPI.translateDict("user_kyc_status", kycStatus));

    SysUserMfa totpMfa = sysUserMfaService.findEnabledTotp(loginUser.getId(), 0).orElse(null);
    loginUser.setTotpEnabled(totpMfa != null);

    return Result.ok(loginUser);
  }

  /** Log-only: classify stored password format without exposing the hash. */
  private static String storedPasswordFormat(String stored) {
    if (StrUtil.isBlank(stored)) {
      return "EMPTY";
    }
    if (stored.startsWith("SM3PWD$")) {
      return "SM3PWD";
    }
    if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
      return "BCRYPT";
    }
    if (stored.startsWith("PBE100K$")) {
      return "PBE_V2";
    }
    return "OTHER";
  }

  private Result<?> validateTeamMemberLogin(SysUser sysUser, Locale locale) {
    // NOTE: Jeecg 门户团队成员验证逻辑已清理, AI SaaS 使用 ai_team/ai_space 表管理团队
    return null;
  }

  private void populateLoginUserScope(LoginUser loginUser, List<String> roleCodes) {
    String roleCode = StrUtil.join(",", roleCodes);
    loginUser.setRoleCode(roleCode);
    loginUser.setTeamMember(false);
    loginUser.setBizOwnerUserId(loginUser.getId());
  }

  @PostMapping("/getPersonalSignMessage")
  @SaCheckLogin
  public Result getPersonalSignMessage(@RequestParam String address) {

    String message = "Hold signature verification:" + IdUtil.simpleUUID();
    redisUtil.set(address, message, 60 * 5);

    return Result.OK("", message);
  }

  @ApiOperation("Username and password verification API")
  @PostMapping("/verifyUsernameAndPassword")
  @AutoLog("Username and password verification")
  public Result<Boolean> verifyUsernameAndPassword(@RequestBody SysLoginModel sysLoginModel) {

    String username = sysLoginModel.getUsername();
    String password = sysLoginModel.getPassword();

    C7PreLoginInternalBlocker.assertAllowedForInteractiveLogin(username);

    // If RSA cache key provided, password is encrypted and must be decrypted
    if (StrUtil.isNotBlank(sysLoginModel.getRsaCacheKey())) {
      String decryptedPassword =
          rsaPasswordUtil.decryptPassword(sysLoginModel.getRsaCacheKey(), password);
      if (decryptedPassword == null) {
        return Result.OK(false);
      }
      password = decryptedPassword;
      // Delete private key after successful decryption
      rsaPasswordUtil.decryptPasswordAndClean(sysLoginModel.getRsaCacheKey(), password);
    }

    // 1. Validate input parameters
    if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
      return Result.OK(false);
    }

    // Input length hardening — reject abusive payloads before they hit crypto/SQL.
    if (username.length() > 128 || password.length() > 512) {
      return Result.OK(false);
    }

    // 2. QueryUserInfo — P4: XML getUserByName keeps password/salt available (select=false
    // on the entity omits them from MP auto SELECT; this endpoint is anonymous → C6 bypass).
    SysUser sysUser = sysUserService.getUserByName(username);

    if (sysUser == null) {
      return Result.OK(false);
    }

    // 3. Validate user is active
    Result<JSONObject> userStatusCheck = sysUserService.checkUserIsEffective(sysUser);
    if (!userStatusCheck.isSuccess()) {
      return Result.OK(false);
    }

    if (!org.jeecg.common.util.CredentialGuard.hasStoredPasswordHash(sysUser.getPassword())
        || !SmCryptoUtil.checkPassword(password, sysUser.getPassword())) {
      return Result.OK(false);
    }

    // 5. VerifySuccess
    return Result.OK(true);
  }

  /**
   * T3 (VA Pattern 4A P1): Server-issued per-session nonce for openSafeWithAuth.
   * Nonces are bound to (currentUserId, service) and expire after 300 seconds (5 minutes).
   * Client must pass the returned nonce to /sys/openSafeWithAuth so that fail-counts are
   * locked per-nonce (prevents VA-Case-4-style TTL-infinite-brute across IPs/users).
   */
  @ApiOperation("Generate safe-auth nonce (per-session per-service)")
  @PostMapping("/genSafeAuthNonce")
  @SaCheckLogin
  public Result<String> genSafeAuthNonce(
      @RequestParam String service,
      @RequestParam(required = false) String authType) {
    String userId = StpUtil.getLoginIdAsString();
    String nonce = IdUtil.fastSimpleUUID();
    String nonceKey = "safe_auth_nonce:" + userId + ":" + service + ":" + nonce;
    String authTypeValue = StrUtil.blankToDefault(authType, "DEFAULT");
    redisUtil.set(nonceKey, authTypeValue, 300L);
    return Result.OK(nonce);
  }

  /**
   * B5-1 / C-P0-2: resolve required second-auth factor from amount + accountType.
   * Frontend must call this before openSafeWithAuth and render fields from {@code requiredFactor}.
   */
  @PostMapping("/resolveSecondAuthType")
  public Result<JSONObject> resolveSecondAuthType(
      @RequestParam String service,
      @RequestParam(required = false) String amount,
      @RequestParam(required = false) String accountType) {
    if (amountBasedSecondAuthResolver == null) {
      return Result.error("Amount-based second auth resolver unavailable");
    }
    if (StrUtil.isBlank(amount)) {
      return Result.error(I18nUtil.getMessage("common.invalid.param"));
    }
    java.math.BigDecimal amt;
    try {
      amt = new java.math.BigDecimal(amount.trim());
    } catch (Exception e) {
      return Result.error(I18nUtil.getMessage("common.invalid.param"));
    }
    java.util.Map<String, Object> preview =
        amountBasedSecondAuthResolver.resolvePreview(accountType, amt, service);
    JSONObject obj = new JSONObject(preview);
    return Result.OK(obj);
  }

  /**
   * Open second-auth security verification for a service. Checks any of (email OTP / payment password / login password)
   * per authType; supports amount+accountType-based server-side factor enforcement (B5-1).
   * To enable per-nonce throttling, call /sys/genSafeAuthNonce first then pass the returned nonce here.
   *
   * @param service target service identifier (e.g. "withdrawal", "changePaymentPassword")
   * @param nonce optional server-issued nonce from /sys/genSafeAuthNonce; when present, fail-counts are locked per-nonce (recommended)
   * @param emailOTP email one-time password (required by VERIFICATION_CODE_ONLY, BOTH_REQUIRED, EMAIL_PASSWORD_REQUIRED)
   * @param paymentPassword payment password (required by PAYMENT_PASSWORD_ONLY, BOTH_REQUIRED)
   * @param loginPassword login password (required by EMAIL_PASSWORD_REQUIRED)
   * @param authType authentication type enum code: VERIFICATION_CODE_ONLY | PAYMENT_PASSWORD_ONLY | BOTH_REQUIRED | EMAIL_PASSWORD_REQUIRED
   * @param rsaCacheKey RSA key cache key (required if login password is RSA-encrypted)
   * @param cacheKey RSA key cache key (alias for rsaCacheKey; consistent with setPaymentPassword etc.)
   * @param paymentPasswordRsaCacheKey RSA key cache key (required if payment password is RSA-encrypted)
   * @param amount optional; with accountType, server enforces requiredFactor (client cannot downgrade)
   * @param accountType optional account type for amount-based second auth
   * @return Result
   */
  @PostMapping("/openSafeWithAuth")
  @FailCountLimit(maxFailures = 5, lockDuration = 900, windowSeconds = 900, keyExpression = "#nonce + ':' + #service")
  public Result openSafeWithAuth(
      @RequestParam String service,
      @RequestParam(required = false) String nonce,
      @RequestParam(required = false) String emailOTP,
      @RequestParam(required = false) String paymentPassword,
      @RequestParam(required = false) String loginPassword,
      @RequestParam(required = false, defaultValue = "VERIFICATION_CODE_ONLY") String authType,
      @RequestParam(required = false) String rsaCacheKey,
      @RequestParam(required = false) String cacheKey,
      @RequestParam(required = false) String paymentPasswordRsaCacheKey,
      @RequestParam(required = false) String amount,
      @RequestParam(required = false) String accountType) {

    if (StrUtil.isBlank(rsaCacheKey) && StrUtil.isNotBlank(cacheKey)) {
      rsaCacheKey = cacheKey;
    }
    if (StrUtil.isBlank(paymentPasswordRsaCacheKey) && StrUtil.isNotBlank(cacheKey)) {
      paymentPasswordRsaCacheKey = cacheKey;
    }

    // T3: per-nonce enforcement. If client sent nonce, server must verify nonce was issued AND not used/expired.
    String userIdBeforeAuth = StpUtil.getLoginIdAsString();
    if (StrUtil.isNotBlank(nonce)) {
      String nonceKey = "safe_auth_nonce:" + userIdBeforeAuth + ":" + service + ":" + nonce;
      if (!Boolean.TRUE.equals(redisUtil.hasKey(nonceKey))) {
        log.warn("openSafeWithAuth invalid/expired nonce: service={} userId={}", service, userIdBeforeAuth);
        return Result.error(I18nUtil.getMessage("common.invalid.param") + " (nonce invalid or expired)");
      }
    }

    String userId = userIdBeforeAuth;
    SysUser sysUser = sysUserService.getById(userId);

    if (sysUser == null) {
      log.warn("Enable security verification failed, user does not exist: {}", userId);
      return Result.error(I18nUtil.getMessage("login.userNotFound"));
    }

    String email = StrUtil.trim(sysUser.getEmail());
    if (StrUtil.isBlank(email)) {
      log.warn("Enable security verification failed, user email is empty: {}", userId);
      return Result.error(I18nUtil.getMessage("login.userEmailEmpty"));
    }

    Locale locale = LocaleResolver.getCurrLocale();
    SecondAuthType authTypeEnum;

    try {
      authTypeEnum = SecondAuthType.fromCode(authType);
    } catch (IllegalArgumentException e) {
      log.warn("Enable security verification failed, unknown authentication type: {}", authType);
      return Result.error(I18nUtil.getMessage("login.unknownAuthType", new Object[] {authType}));
    }

    // B5-1: when amount+accountType provided, requiredFactor is server-side; reject weaker client authType
    SecondAuthType requiredFactor = null;
    if (amountBasedSecondAuthResolver != null && StrUtil.isNotBlank(amount)) {
      try {
        java.math.BigDecimal amt = new java.math.BigDecimal(amount.trim());
        java.util.Map<String, Object> preview =
            amountBasedSecondAuthResolver.resolvePreview(accountType, amt, service);
        Object rf = preview.get("requiredFactor");
        if (rf != null) {
          requiredFactor = SecondAuthType.fromCode(String.valueOf(rf));
          if (!org.jeecg.common.aspect.security.SecondAuthFactorGuard.satisfies(
              requiredFactor, authTypeEnum)) {
            log.warn(
                "openSafeWithAuth factor downgrade blocked: service={} required={} client={}",
                service,
                requiredFactor.getCode(),
                authTypeEnum.getCode());
            return Result.error(
                org.jeecg.common.constant.CommonConstant.SC_SECOND_AUTH_REQUIRED,
                "Second authentication factor mismatch; required "
                    + requiredFactor.getCode()
                    + " but client sent "
                    + authTypeEnum.getCode()
                    + "; please call /sys/resolveSecondAuthType then /sys/openSafeWithAuth");
          }
          // Keep client authTypeEnum for credential verification; it already satisfies required.
        }
      } catch (Exception ex) {
        log.warn("openSafeWithAuth amount-based resolve failed: {}", ex.toString());
        return Result.error(I18nUtil.getMessage("common.invalid.param"));
      }
    }

    // Authenticate by Authentication type
    boolean authPassed = false;
    String errorMessage = null;

    switch (authTypeEnum) {
      case VERIFICATION_CODE_ONLY:
        // Only verification code authentication
        if (StrUtil.isBlank(emailOTP)) {
          return Result.error(I18nUtil.getMessage("login.verifyCodeEmpty"));
        }
        authPassed = verifyEmailOTP(email, emailOTP);
        if (!authPassed) {
          errorMessage = messageSource.getMessage("common.verifyCodeError", null, locale);
        }
        break;

      case PAYMENT_PASSWORD_ONLY:
        // Pay password authentication only
        if (StrUtil.isBlank(paymentPassword)) {
          return Result.error(I18nUtil.getMessage("login.paymentPasswordEmpty"));
        }

        // If RSA cache key provided, payment password is encrypted and must be decrypted
        String decryptedPaymentPassword = paymentPassword;
        if (StrUtil.isNotBlank(paymentPasswordRsaCacheKey)) {
          decryptedPaymentPassword =
              rsaPasswordUtil.decryptPasswordAndClean(
                  paymentPasswordRsaCacheKey, paymentPassword);
          if (decryptedPaymentPassword == null) {
            log.warn("Enable security verification failed, RSA payment password decryption failed: {}", userId);
            return Result.error(
                messageSource.getMessage("common.authentication_failed_msg", null, locale));
          }
        }

        authPassed = verifyPaymentPassword(userId, decryptedPaymentPassword);
        if (!authPassed) {
          errorMessage = I18nUtil.getMessage("login.paymentPasswordError");
        }
        break;

      case BOTH_REQUIRED:
        // Both verification code and payment password are required
        if (StrUtil.isBlank(emailOTP)) {
          return Result.error(I18nUtil.getMessage("login.verifyCodeEmpty"));
        }
        if (StrUtil.isBlank(paymentPassword)) {
          return Result.error(I18nUtil.getMessage("login.paymentPasswordEmpty"));
        }

        // If RSA cache key provided, payment password is encrypted and must be decrypted
        String decryptedPaymentPasswordForBoth = paymentPassword;
        if (StrUtil.isNotBlank(paymentPasswordRsaCacheKey)) {
          decryptedPaymentPasswordForBoth =
              rsaPasswordUtil.decryptPasswordAndClean(
                  paymentPasswordRsaCacheKey, paymentPassword);
          if (decryptedPaymentPasswordForBoth == null) {
            log.warn("Enable security verification failed, RSA payment password decryption failed: {}", userId);
            return Result.error(
                messageSource.getMessage("common.authentication_failed_msg", null, locale));
          }
        }

        boolean codeValid = verifyEmailOTP(email, emailOTP);
        boolean passwordValid = verifyPaymentPassword(userId, decryptedPaymentPasswordForBoth);

        if (!codeValid) {
          errorMessage = messageSource.getMessage("common.verifyCodeError", null, locale);
          authPassed = false;
        } else if (!passwordValid) {
          errorMessage = I18nUtil.getMessage("login.paymentPasswordError");
          authPassed = false;
        } else {
          authPassed = true;
        }
        break;

      case EMAIL_PASSWORD_REQUIRED:
        // Both email verification code and login password are required
        if (StrUtil.isBlank(emailOTP)) {
          return Result.error(I18nUtil.getMessage("login.verifyCodeEmpty"));
        }
        if (StrUtil.isBlank(loginPassword)) {
          return Result.error(I18nUtil.getMessage("login.loginPasswordEmpty"));
        }

        // If RSA cache key provided, login password is encrypted and must be decrypted
        String decryptedLoginPassword = loginPassword;
        if (StrUtil.isNotBlank(rsaCacheKey)) {
          log.debug(
              "Starting login password decryption, userId: {}, rsaCacheKey: {}, encryptedPassword length: {}",
              userId,
              rsaCacheKey,
              loginPassword != null ? loginPassword.length() : 0);

          decryptedLoginPassword =
              rsaPasswordUtil.decryptPasswordAndClean(rsaCacheKey, loginPassword);
          if (decryptedLoginPassword == null) {
            log.warn("Enable security verification failed, RSA login password decryption failed, userId: {}, rsaCacheKey: {}", userId, rsaCacheKey);
            return Result.error(
                messageSource.getMessage("common.authentication_failed_msg", null, locale));
          }
          log.debug("RSA login password decrypted successfully, userId: {}", userId);
        } else {
          log.debug("rsaCacheKey not provided, treating as plaintext password, userId: {}", userId);
        }

        boolean emailCodeValid = verifyEmailOTP(email, emailOTP);
        boolean loginPasswordValid = verifyLoginPassword(userId, decryptedLoginPassword);

        if (!emailCodeValid) {
          errorMessage = messageSource.getMessage("common.verifyCodeError", null, locale);
        } else if (!loginPasswordValid) {
          errorMessage = messageSource.getMessage("common.authentication_failed_msg", null, locale);
        } else {
          authPassed = true;
        }
        break;

      default:
        return Result.error(I18nUtil.getMessage("login.unknownAuthType", new Object[] {authType}));
    }

    if (!authPassed) {
      log.warn("User [{}] failed to enable security verification, auth type: {}, error: {}", email, authTypeEnum, errorMessage);
      return Result.error(
          errorMessage != null ? errorMessage : I18nUtil.getMessage("login.secondAuthFailed"));
    }

    // Auth passed; enable security verification (10-minute validity)
    StpUtil.openSafe(service, 60 * 10);
    // B5-1: bind factor into session — email OTP cannot later satisfy payment-password endpoints
    org.jeecg.common.aspect.security.SecondAuthFactorGuard.bind(service, authTypeEnum);

    // T3: consume per-nonce once auth succeeds (CUD: Use succeeded → Delete nonce)
    if (StrUtil.isNotBlank(nonce)) {
      String nonceKey = "safe_auth_nonce:" + userId + ":" + service + ":" + nonce;
      redisUtil.del(nonceKey);
    }

    // Delete used captcha
    if (StrUtil.isNotBlank(emailOTP)) {
      String redisKey = CommonConstant.EMAIL_SECOND_AUTH + email;
      redisUtil.del(redisKey);
    }

    log.info("User [{}] enabled security verification successfully, service: {}, auth type: {}", email, service, authTypeEnum);
    return Result.OK(I18nUtil.getMessage("common.success.operation"));
  }

  /** Verify email verification code */
  private boolean verifyEmailOTP(String email, String emailOTP) {
    String redisKey = CommonConstant.EMAIL_SECOND_AUTH + StrUtil.trim(email);
    Object codeInRedis = redisUtil.get(redisKey);
    if (codeInRedis == null) {
      return false;
    }
    String sv = String.valueOf(codeInRedis);
    if (StrUtil.equals(sv, emailOTP)) {
      return true; // Transitional plain-equals compatibility: legacy captcha codes expire naturally within 11 minutes
    }
    return otpHasher.verify(emailOTP, sv, "TWO_FACTOR_AUTHENTICATION", email);
  }

  /** Verify payment password */
  private boolean verifyPaymentPassword(String userId, String paymentPassword) {
    if (paymentPasswordUtil == null) {
      log.warn("PaymentPasswordUtil not injected; cannot verify payment password");
      return false;
    }
    return paymentPasswordUtil.verifyPaymentPassword(userId, paymentPassword);
  }

  /** Verify login password */
  private boolean verifyLoginPassword(String userId, String loginPassword) {
    if (!org.jeecg.common.util.CredentialGuard.hasText(userId)
        || !org.jeecg.common.util.CredentialGuard.isPresentLoginPassword(loginPassword)) {
      log.warn("User ID or login password is empty, userId: {}, password empty: {}", userId, StrUtil.isBlank(loginPassword));
      return false;
    }

    try {
      // L27-2: use the pinned self-credential statement instead of an MP-generated selectOne.
      // The MP statement id (...)SysUserMapper.selectOne is not in any C6 carve-out, so a
      // non-admin logged-in session (the only kind that reaches second-auth) was blocked with
      // C6_COLUMN_BLOCKED and the exception was swallowed as "wrong password". The pinned
      // statement is admitted by SensitiveTableBlacklistInterceptor only when the bound id equals
      // the caller's own login id.
      SysUser sysUser = sysUserMapper.selectCredentialById(userId);
      if (sysUser == null) {
        log.warn("User does not exist：{}", userId);
        return false;
      }

      String storedPassword = sysUser.getPassword();
      if (!org.jeecg.common.util.CredentialGuard.hasStoredPasswordHash(storedPassword)) {
        log.warn("User [{}] login password not set", userId);
        return false;
      }

      log.debug(
          "Starting login password verification, userId: {}, input password length: {}, stored password hash length: {}",
          userId,
          loginPassword.length(),
          storedPassword.length());

      // Verify password with BCrypt
      boolean isValid = SmCryptoUtil.checkPassword(loginPassword, storedPassword);

      if (!isValid) {
        log.warn("User [{}] login password verification failed, input password length: {}", userId, loginPassword.length());
      } else {
        log.debug("User [{}] login password verified successfully", userId);
      }

      return isValid;
    } catch (Exception e) {
      log.error("Login password verification exception, userId: {}, error: {}", userId, e);
      return false;
    }
  }
}
