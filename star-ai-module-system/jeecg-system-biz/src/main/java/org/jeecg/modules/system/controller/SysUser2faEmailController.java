package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sys/user/2fa")
@Api(tags = "User 2FA email helpers")
@Slf4j
public class SysUser2faEmailController {

  @Autowired private ISysUserService sysUserService;
  private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> OTP_FAIL_COUNT = new java.util.concurrent.ConcurrentHashMap<>();

  @SaCheckLogin
  @AutoLog("Query 2FA masked email for current user")
  @ApiOperation("Return current user masked email (e.g. ab***cd@example.com) so UI can render 'send to' without exposing plaintext")
  @GetMapping("/email")
  public Result<Map<String, Object>> getMaskedEmail() {
    Map<String, Object> payload = new HashMap<>();
    String masked = sysUserService.getLoggedInUserMaskedEmail();
    String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
    SysUser user = sysUserService.getById(userId);
    boolean hasEmail = user != null && StrUtil.isNotBlank(user.getEmail());
    payload.put("maskedEmail", masked);
    payload.put("hasEmail", hasEmail);
    if (hasEmail && user.getEmail().contains("@")) {
      payload.put("emailDomain", user.getEmail().substring(user.getEmail().indexOf('@')));
    } else {
      payload.put("emailDomain", "");
    }
    return Result.OK(payload);
  }

  @SaCheckLogin
  @AutoLog("Send 2FA email OTP to current user (no email param)")
  @ApiOperation("Send TWO_FACTOR email OTP to the currently logged-in user; server resolves email internally (G9: no external email param accepted)")
  @PostMapping("/email/send")
  public Result<String> sendTwoFactorOtp() {
    String uid = cn.dev33.satoken.stp.StpUtil.getLoginIdDefaultNull() == null ? "anon" : cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
    try {
      sysUserService.sendLoggedInUserTwoFactorEmailOtp();
    } catch (Exception e) {
      long cnt = OTP_FAIL_COUNT.computeIfAbsent(uid, k -> new java.util.concurrent.atomic.AtomicLong(0)).incrementAndGet();
      log.warn("sendTwoFactorOtp failed uid={}", uid, e);
      if (cnt >= 10L) { log.error("ALERT_OTP_LOCK uid={} failCount={}", uid, cnt); }
      return Result.error(e.getMessage() == null ? I18nUtil.getMessage("common.system_error_msg") : e.getMessage());
    }
    return Result.OK("");
  }
}
