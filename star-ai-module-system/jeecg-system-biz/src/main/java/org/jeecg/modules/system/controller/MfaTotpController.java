package org.jeecg.modules.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.Date;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.jeecg.common.aspect.FailCountLimit;
import org.jeecg.common.aspect.annotation.H8LoginAudit;
import org.jeecg.common.aspect.annotation.RequireSecondAuth;
import org.jeecg.common.aspect.enumeration.SecondAuthType;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.LocaleResolver;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserMfa;
import org.jeecg.modules.system.service.ISysUserMfaService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.system.service.crypto.MfaSeedCryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cn.dev33.satoken.annotation.SaCheckLogin;

@RestController
@RequestMapping("/sys/mfa/totp")
@Api(tags = "MFA-TOTP")

@SaCheckLogin
public class MfaTotpController {

  private static final String MFA_TYPE_TOTP = "TOTP";
  private static final String DEFAULT_ISSUER = "mightyPay";
  private static final String ERR_TOTP_ALREADY_ENABLED = "TOTP_ALREADY_ENABLED";
  private static final String ERR_TOTP_SETUP_NOT_INIT = "TOTP_SETUP_NOT_INIT";
  private static final String ERR_TOTP_INVALID = "TOTP_INVALID";
  private static final String ERR_TOTP_NOT_ENABLED = "TOTP_NOT_ENABLED";
  private static final String ERR_TOTP_LOCKED = "TOTP_LOCKED";
  private static final String ERR_TOTP_REPLAY = "TOTP_REPLAY";

  @Autowired private ISysUserService sysUserService;
  @Autowired private ISysUserMfaService sysUserMfaService;
  @Autowired private MfaSeedCryptoService mfaSeedCryptoService;
  @Autowired private MessageSource messageSource;
  @Autowired private org.jeecg.modules.system.service.impl.SessionKickService sessionKickService;

  @ApiOperation("Initialize TOTP binding")
  @PostMapping("/setup/init")
  public Result<JSONObject> initSetup() throws Exception {
    Locale locale = LocaleResolver.getCurrLocale();

    String userId = StpUtil.getLoginIdAsString();
    SysUser user = sysUserService.getById(userId);
    if (user == null) {
      return Result.error(messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    Integer tenantId = 0;
    SysUserMfa existing =
        sysUserMfaService.findAnyTotp(userId, tenantId).orElse(null);
    if (existing != null && Integer.valueOf(1).equals(existing.getEnabled())) {
      return errorResp(409, "TOTP enabled", ERR_TOTP_ALREADY_ENABLED);
    }

    GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();
    GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
    String secret = key.getKey();
    String secretEnc = mfaSeedCryptoService.wrapSeed(secret, userId, tenantId);

    Date now = new Date();

    SysUserMfa mfa = existing != null ? existing : new SysUserMfa();
    mfa.setTenantId(tenantId);
    mfa.setUserId(userId);
    mfa.setMfaType(MFA_TYPE_TOTP);
    mfa.setSecretEnc(secretEnc);
    mfa.setEnabled(0);
    mfa.setVerifiedAt(null);
    mfa.setFailCount(0);
    mfa.setLockedUntil(null);
    mfa.setDelFlag(0);
    if (StrUtil.isBlank(mfa.getId())) {
      mfa.setCreateBy(user.getUsername());
      mfa.setCreateTime(now);
    }
    mfa.setUpdateBy(user.getUsername());
    mfa.setUpdateTime(now);
    sysUserMfaService.saveOrUpdate(mfa);

    String accountName = StrUtil.blankToDefault(user.getUsername(), user.getEmail());
    String otpauthUri = buildOtpAuthUri(DEFAULT_ISSUER, accountName, secret);

    JSONObject obj = new JSONObject();
    obj.put("otpauth_uri", otpauthUri);
    obj.put("masked_secret", maskSecret(secret));
    Result<JSONObject> result = new Result<>();
    result.setResult(obj);
    result.success("");
    return result;
  }

  @ApiOperation("Confirm TOTP binding")
  @PostMapping("/setup/confirm")
  @H8LoginAudit(value = "MFA TOTP setup confirm: /sys/mfa/totp/setup/confirm", endpoint = "/sys/mfa/verify")
  @FailCountLimit(maxFailures = 5, lockDuration = 900, windowSeconds = 900, keyExpression = "#totp")
  public Result<JSONObject> confirmSetup(@RequestParam("totp") String totp) throws Exception {
    Locale locale = LocaleResolver.getCurrLocale();

    String userId = StpUtil.getLoginIdAsString();
    SysUser user = sysUserService.getById(userId);
    if (user == null) {
      return Result.error(messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    Integer tenantId = 0;
    SysUserMfa mfa = sysUserMfaService.findAnyTotp(userId, tenantId).orElse(null);
    if (mfa == null || StrUtil.isBlank(mfa.getSecretEnc())) {
      return errorResp(410, "Initialization incomplete or expired", ERR_TOTP_SETUP_NOT_INIT);
    }
    if (Integer.valueOf(1).equals(mfa.getEnabled())) {
      return errorResp(409, "TOTP enabled", ERR_TOTP_ALREADY_ENABLED);
    }

    String secret = mfaSeedCryptoService.tryDecryptWithAesThenMigrate(mfa);
    if (StrUtil.isBlank(secret)) {
      return Result.error(messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    Integer otp;
    try {
      otp = Integer.parseInt(StrUtil.trim(totp));
    } catch (Exception e) {
      return errorResp(400, "Verification code format error", ERR_TOTP_INVALID);
    }

    Date now = new Date();
    Date lastUsed = mfa.getLastUsedAt();
    if (lastUsed != null) {
      long delta30 = (now.getTime() / 30000L) - (lastUsed.getTime() / 30000L);
      if (delta30 <= 0) {
        return errorResp(400, "Verification code reused in the same 30s slot", ERR_TOTP_REPLAY);
      }
    }

    GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();
    boolean ok = googleAuthenticator.authorize(secret, otp);
    if (!ok) {
      return errorResp(400, "Incorrect verification code", ERR_TOTP_INVALID);
    }

    mfa.setLastUsedAt(now);
    mfa.setEnabled(1);
    mfa.setVerifiedAt(now);
    String reWrapped = mfaSeedCryptoService.wrapSeed(secret, mfa.getUserId(), mfa.getTenantId());
    if (!StrUtil.equals(reWrapped, mfa.getSecretEnc())) {
      mfa.setSecretEnc(reWrapped);
    }
    mfa.setFailCount(0);
    mfa.setLockedUntil(null);
    mfa.setUpdateBy(user.getUsername());
    mfa.setUpdateTime(now);
    sysUserMfaService.updateById(mfa);

    sessionKickService.kickAllDevices(userId, "mfa_enabled");

    Result<JSONObject> result = new Result<>();
    result.setResult(new JSONObject());
    result.success(I18nUtil.getMessage("common.success.operation"));
    return result;
  }

  private static Result<JSONObject> errorResp(int code, String message, String errorCode) {
    Result<JSONObject> result = new Result<>();
    result.setSuccess(false);
    result.setCode(code);
    result.setMessage(message);
    JSONObject payload = new JSONObject();
    payload.put("errorCode", errorCode);
    result.setResult(payload);
    return result;
  }

  private static Result<JSONObject> errorResp(
      int code, String message, String errorCode, Long remainingSeconds) {
    Result<JSONObject> result = errorResp(code, message, errorCode);
    JSONObject payload = result.getResult();
    if (payload != null && remainingSeconds != null) {
      payload.put("remainingSeconds", remainingSeconds);
    }
    return result;
  }


  @ApiOperation("Close TOTP")
  @RequireSecondAuth(value = SecondAuthType.EMAIL_PASSWORD_REQUIRED, service = "disableTotp")
  @PostMapping("/disable")
  @FailCountLimit(maxFailures = 5, lockDuration = 900, windowSeconds = 900, keyExpression = "#totp")
  public Result<JSONObject> disable(@RequestParam("totp") String totp) throws Exception {
    Locale locale = LocaleResolver.getCurrLocale();

    String userId = StpUtil.getLoginIdAsString();
    SysUser user = sysUserService.getById(userId);
    if (user == null) {
      return Result.error(messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    Integer tenantId = 0;
    SysUserMfa mfa = sysUserMfaService.findEnabledTotp(userId, tenantId).orElse(null);
    if (mfa == null || StrUtil.isBlank(mfa.getSecretEnc())) {
      return errorResp(400, "TOTP disabled", ERR_TOTP_NOT_ENABLED);
    }

    Date now = new Date();
    if (sysUserMfaService.isLocked(mfa, now)) {
      long remainingSeconds =
          Math.max(0, (mfa.getLockedUntil().getTime() - now.getTime()) / 1000);
      return errorResp(423, "Too many consecutive errors; please try again later", ERR_TOTP_LOCKED, remainingSeconds);
    }

    String secret = mfaSeedCryptoService.tryDecryptWithAesThenMigrate(mfa);
    if (StrUtil.isBlank(secret)) {
      return Result.error(messageSource.getMessage("common.authentication_failed_msg", null, locale));
    }

    Integer otp;
    try {
      otp = Integer.parseInt(StrUtil.trim(totp));
    } catch (Exception e) {
      return errorResp(400, "Verification code format error", ERR_TOTP_INVALID);
    }

    Date lastUsed = mfa.getLastUsedAt();
    if (lastUsed != null) {
      long delta30 = (now.getTime() / 30000L) - (lastUsed.getTime() / 30000L);
      if (delta30 <= 0) {
        return errorResp(400, "Verification code reused in the same 30s slot", ERR_TOTP_REPLAY);
      }
    }

    GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();
    boolean ok = googleAuthenticator.authorize(secret, otp);
    if (!ok) {
      int fail = mfa.getFailCount() == null ? 0 : mfa.getFailCount();
      fail++;
      mfa.setFailCount(fail);
      if (fail >= 5) {
        mfa.setLockedUntil(cn.hutool.core.date.DateUtil.offsetMinute(now, 5));
      }
      mfa.setUpdateBy(user.getUsername());
      mfa.setUpdateTime(now);
      sysUserMfaService.updateById(mfa);
      return errorResp(400, "Incorrect verification code", ERR_TOTP_INVALID);
    }

    mfa.setLastUsedAt(now);
    mfa.setEnabled(0);
    mfa.setSecretEnc(null);
    mfa.setVerifiedAt(null);
    mfa.setFailCount(0);
    mfa.setLockedUntil(null);
    mfa.setDelFlag(0);
    mfa.setUpdateBy(user.getUsername());
    mfa.setUpdateTime(now);
    sysUserMfaService.updateById(mfa);

    sessionKickService.kickAllDevices(userId, "mfa_disabled");

    Result<JSONObject> result = new Result<>();
    result.setResult(new JSONObject());
    result.success(I18nUtil.getMessage("common.success.operation"));
    return result;
  }

  private static String buildOtpAuthUri(String issuer, String accountName, String secret) {
    String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
    String label =
        URLEncoder.encode(issuer + ":" + StrUtil.blankToDefault(accountName, ""), StandardCharsets.UTF_8);
    return "otpauth://totp/"
        + label
        + "?secret="
        + secret
        + "&issuer="
        + encodedIssuer
        + "&digits=6&period=30";
  }

  private static String maskSecret(String secret) {
    if (StrUtil.isBlank(secret) || secret.length() <= 8) {
      return "********";
    }
    return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
  }
}
