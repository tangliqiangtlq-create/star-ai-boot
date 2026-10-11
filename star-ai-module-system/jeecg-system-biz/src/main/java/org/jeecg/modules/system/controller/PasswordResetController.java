package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.aspect.annotation.LimitSubmit;
import org.jeecg.common.system.passwordreset.dto.PasswordResetSubmitDTO;
import org.jeecg.common.system.passwordreset.exception.PasswordResetErrorCode;
import org.jeecg.common.system.passwordreset.exception.PasswordResetException;
import org.jeecg.common.system.passwordreset.vo.PasswordResetLinkVO;
import org.jeecg.common.system.passwordreset.vo.PasswordResetMetaVO;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.system.passwordreset.entity.SysPasswordResetAccessLog;
import org.jeecg.modules.system.passwordreset.entity.SysPasswordResetLink;
import org.jeecg.modules.system.passwordreset.entity.SysPasswordResetUsageLog;
import org.jeecg.modules.system.passwordreset.helper.ClientIpResolver;
import org.jeecg.modules.system.passwordreset.helper.PasswordResetCaptchaHelper;
import org.jeecg.modules.system.passwordreset.mapper.SysPasswordResetAccessLogMapper;
import org.jeecg.modules.system.passwordreset.mapper.SysPasswordResetUsageLogMapper;
import org.jeecg.modules.system.passwordreset.service.IPasswordResetLinkService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Api(tags = "Password reset external link")
@RequestMapping("/sys/password-reset")
@RestController
@RequiredArgsConstructor
public class PasswordResetController {

    private final IPasswordResetLinkService passwordResetLinkService;
    private final ClientIpResolver clientIpResolver;
    private final PasswordResetCaptchaHelper captchaHelper;
    private final org.jeecg.modules.system.passwordreset.config.PasswordResetProperties props;
    private final SysPasswordResetAccessLogMapper accessLogMapper;
    private final SysPasswordResetUsageLogMapper usageLogMapper;
    private final org.jeecg.modules.system.service.impl.SessionKickService sessionKickService;

    private void checkEnabled() {
        if (!props.isEnabled()) {
            throw new PasswordResetException(PasswordResetErrorCode.NOT_PERMITTED, "Password reset link feature is disabled");
        }
    }

    /* =============================== Anonymous APIs: external users open links =============================== */

    /**
     * Generate captcha image (Base64 JPEG + captchaKey), 5 minute TTL, deleted after check.
     * Frontend calls when needCaptcha=true.
     */
    @SaIgnore
    @GetMapping("/captcha")
    @ApiOperation("Password reset captcha")
    public ResponseEntity<Result<java.util.Map<String, String>>> captcha() {
        HttpHeaders headers = securityHeaders();
        try {
            checkEnabled();
            java.util.Map<String, String> m = captchaHelper.generate();
            if (m == null || m.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).headers(headers)
                        .body(Result.error("Failed to generate captcha. Please try again later"));
            }
            return ResponseEntity.ok().headers(headers).body(Result.OK(m));
        } catch (Exception e) {
            log.error("password-reset captcha System exception", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).headers(headers)
                    .body(Result.error("Service error. Please try again later"));
        }
    }

    /**
     * Fetch metadata when opening the link (status + remaining attempts + needCaptcha + challenge + password policy).
     * Each call writes an access_log entry.
     */
    @SaIgnore
    @GetMapping("/meta/{token}")
    @ApiOperation("Password reset link metadata")
    public ResponseEntity<Result<PasswordResetMetaVO>> meta(@PathVariable String token,
                                                            HttpServletRequest request) {
        HttpHeaders headers = securityHeaders();
        try {
            checkEnabled();
            PasswordResetMetaVO meta = passwordResetLinkService.getMeta(token, request);
            return ResponseEntity.ok().headers(headers).body(Result.OK(meta));
        } catch (PasswordResetException e) {
            log.warn("password-reset meta Failed：{} {}", e.getCode());
            Result<PasswordResetMetaVO> err = Result.error("Link is invalid or expired. Please request a new one");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(err);
        } catch (Exception e) {
            log.error("password-reset meta System exception", e);
            Result<PasswordResetMetaVO> err = Result.error("Service error. Please try again later");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).headers(headers).body(err);
        }
    }

    /**
     * Submit the new password after double RSA encryption.
     * Idempotent with 3-second replay protection.
     */
    @SaIgnore
    @PostMapping("/submit")
    @ApiOperation("Submit password reset")
    @LimitSubmit(key = "PasswordReset_submit:%s", limit = 3)
    public ResponseEntity<Result<PasswordResetLinkVO>> submit(@Validated @RequestBody PasswordResetSubmitDTO dto,
                                                              HttpServletRequest request) {
        HttpHeaders headers = securityHeaders();
        try {
            checkEnabled();
            passwordResetLinkService.submitPassword(
                    dto.getToken(),
                    dto.getChallenge(),
                    dto.getRsaCacheKey(),
                    dto.getEncryptedPassword1(),
                    dto.getEncryptedPassword2(),
                    dto.getCaptcha(),
                    dto.getCaptchaKey(),
                    request);
            return ResponseEntity.ok().headers(headers).body(Result.OK());
        } catch (PasswordResetException e) {
            // Surface original message for recoverable errors; include specific reason for LINK_INVALID when possible
            switch (e.getCode()) {
                case PasswordResetErrorCode.PWD_MISMATCH:
                case PasswordResetErrorCode.PWD_WEAK:
                case PasswordResetErrorCode.PWD_SAME_AS_OLD:
                case PasswordResetErrorCode.RSA_DECRYPT_FAIL:
                case PasswordResetErrorCode.CAPTCHA_REQUIRED:
                case PasswordResetErrorCode.SUBMIT_TOO_FAST:
                case PasswordResetErrorCode.CHALLENGE_FAIL:
                    return ResponseEntity.badRequest().headers(headers).body(Result.error(e.getMessage()));
                case PasswordResetErrorCode.LINK_INVALID:
                    return ResponseEntity.badRequest().headers(headers)
                            .body(Result.error(
                                    org.springframework.util.StringUtils.hasText(e.getMessage())
                                            ? e.getMessage()
                                            : "Link is invalid or expired. Please request a new one"));
                default:
                    log.warn("password-reset submit failed code={} msg={}", e.getCode());
                    return ResponseEntity.badRequest().headers(headers)
                            .body(Result.error(
                                    org.springframework.util.StringUtils.hasText(e.getMessage())
                                            ? e.getMessage()
                                            : "Link is invalid or expired. Please request a new one"));
            }
        } catch (Exception e) {
            log.error("password-reset submit System exception", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).headers(headers)
                    .body(Result.error("Service error. Please try again later"));
        }
    }

    /* =============================== Auth APIs: operations/admin use (P2 will refine permission codes) =============================== */

    /**
     * Admin console: generate reset link.
     * Permission: passwordReset:create.
     * Params: userId / username at least one; if both passed, userId takes precedence; internally check username match.
     */
    @PostMapping("/admin/create-link")
    @ApiOperation("Admin generate reset link")
    @AutoLog(value = "Password reset - Generate external link", logType = 1, operateType = 2)
    @SaCheckPermission("passwordReset:create")
    public Result<PasswordResetLinkVO> adminCreateLink(@RequestParam(required = false) String userId,
                                                       @RequestParam(required = false) String username,
                                                       @RequestParam(required = false, defaultValue = "OPS_RESET") String source,
                                                       @RequestParam(required = false, defaultValue = "30") Integer ttlMinutes,
                                                       @RequestParam(required = false, defaultValue = "5") Integer maxFailAttempts,
                                                       @RequestParam(required = false) String remark,
                                                       HttpServletRequest request) {
        checkEnabled();
        if (org.jeecg.common.util.oConvertUtils.isEmpty(userId) && org.jeecg.common.util.oConvertUtils.isEmpty(username)) {
            return Result.error("Either userId or username is required");
        }
        String creator = currentLoginUserId();
        String creatorIp = clientIp(request, clientIpResolver);
        PasswordResetLinkVO vo = passwordResetLinkService.create(
                userId,
                username,
                ttlMinutes == null ? 30 : ttlMinutes,
                maxFailAttempts == null ? 5 : maxFailAttempts,
                source,
                creator,
                creatorIp,
                remark);
        return Result.OK(vo);
    }

    /** Revoke a single link */
    @PostMapping("/admin/revoke/{token}")
    @ApiOperation("Admin revoke reset link")
    @AutoLog(value = "Password reset - Revoke link", logType = 1, operateType = 3)
    @SaCheckPermission("passwordReset:revoke")
    public Result<Boolean> adminRevoke(@PathVariable String token,
                                       @RequestParam(required = false) String reason) {
        checkEnabled();
        String op = currentLoginUserId();
        boolean ok = passwordResetLinkService.revoke(token, op, reason);
        return Result.OK(ok);
    }

    /** Batch revoke by account */
    @PostMapping("/admin/revoke-by-username/{username}")
    @ApiOperation("Admin batch revoke reset links by account")
    @AutoLog(value = "Password reset - Batch revoke links by account", logType = 1, operateType = 3)
    @SaCheckPermission("passwordReset:revokeByUsername")
    public Result<Integer> adminRevokeAll(@PathVariable String username,
                                          @RequestParam(required = false) String reason) {
        checkEnabled();
        String op = currentLoginUserId();
        int n = passwordResetLinkService.revokeAllPendingByUsername(username, op, reason);
        return Result.OK(n);
    }

    /* =============================== Admin backend: list/detail/audit log (P2-2 / P2-5) =============================== */

    /**
     * Password reset link - paginated list.
     * Supports filtering by username, source, status, createTime range; default sort by create time desc.
     * Permission: passwordReset:list.
     * <p>POST method: admin axios globally converts GET to form POST (submitForm).
     */
    @RequestMapping(value = "/list", method = RequestMethod.POST)
    @ApiOperation("Password reset link - Paginated list")
    @SaCheckPermission("passwordReset:list")
    public Result<IPage<SysPasswordResetLink>> list(SysPasswordResetLink query,
                                                    @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                    @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                    @RequestParam(name = "createTime_begin", required = false) String createTimeBegin,
                                                    @RequestParam(name = "createTime_end", required = false) String createTimeEnd) {
        QueryWrapper<SysPasswordResetLink> qw = new QueryWrapper<>();
        if (oConvertUtils.isNotEmpty(query.getUsername())) qw.like("username", query.getUsername());
        if (oConvertUtils.isNotEmpty(query.getSource()))   qw.eq("source", query.getSource());
        if (oConvertUtils.isNotEmpty(query.getStatus()))   qw.eq("status", query.getStatus());
        if (oConvertUtils.isNotEmpty(query.getToken()))    qw.eq("token", query.getToken());
        if (oConvertUtils.isNotEmpty(createTimeBegin))     qw.ge("create_time", createTimeBegin);
        if (oConvertUtils.isNotEmpty(createTimeEnd))       qw.le("create_time", createTimeEnd);
        qw.orderByDesc("create_time");
        Page<SysPasswordResetLink> page = new Page<>(pageNo, pageSize);
        IPage<SysPasswordResetLink> pageList = passwordResetLinkService.page(page, qw);
        return Result.OK(pageList);
    }

    /**
     * Password reset link - detail (query by id).
     * Permission: passwordReset:list.
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.POST)
    @ApiOperation("Password reset link - Detail")
    @SaCheckPermission("passwordReset:list")
    public Result<SysPasswordResetLink> detail(@PathVariable String id) {
        SysPasswordResetLink one = passwordResetLinkService.getById(id);
        if (one == null) return Result.error("Record not found");
        return Result.OK(one);
    }

    /**
     * View access audit log for a link (query access_log by token, descending order).
     * Permission: passwordReset:list.
     */
    @RequestMapping(value = "/access-logs/{token}", method = RequestMethod.POST)
    @ApiOperation("Password reset link - Access log list")
    @SaCheckPermission("passwordReset:list")
    public Result<List<SysPasswordResetAccessLog>> accessLogs(@PathVariable String token) {
        List<SysPasswordResetAccessLog> list = accessLogMapper.selectList(
                new LambdaQueryWrapper<SysPasswordResetAccessLog>()
                        .eq(SysPasswordResetAccessLog::getToken, token)
                        .orderByDesc(SysPasswordResetAccessLog::getCreateTime));
        return Result.OK(list);
    }

    /**
     * View usage audit log for a link (query usage_log by token, descending order).
     * Permission: passwordReset:list.
     */
    @RequestMapping(value = "/usage-logs/{token}", method = RequestMethod.POST)
    @ApiOperation("Password reset link - Usage log list")
    @SaCheckPermission("passwordReset:list")
    public Result<List<SysPasswordResetUsageLog>> usageLogs(@PathVariable String token) {
        List<SysPasswordResetUsageLog> list = usageLogMapper.selectList(
                new LambdaQueryWrapper<SysPasswordResetUsageLog>()
                        .eq(SysPasswordResetUsageLog::getToken, token)
                        .orderByDesc(SysPasswordResetUsageLog::getCreateTime));
        return Result.OK(list);
    }

    /* =============================== Internal utilities =============================== */

    /**
     * Tight NON-CSP headers for password-reset pages (high-sensitivity surface).
     * <p>
     * CSP — including frame-ancestors/form-action/base-uri — is written by the
     * gateway single-source-of-truth {@code SecurityHeadersGlobalWebFilter}.
     * Adding a local CSP header here would force Enforce even when the gateway
     * mode is ReportOnly, producing a double-header and violating the
     * single-source-of-truth principle.
     *
     * @return non-CSP defensive headers only
     */
    private HttpHeaders securityHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.add("Referrer-Policy", "no-referrer");
        h.add("X-Content-Type-Options", "nosniff");
        h.add("X-Frame-Options", "DENY");
        h.add("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        return h;
    }

    private static String clientIp(HttpServletRequest r, ClientIpResolver resolver) {
        if (r == null) return null;
        ClientIpResolver.ClientIp ci = resolver.resolve(r);
        return ci.getClientIp();
    }

    private static String currentLoginUserId() {
        try {
            return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        } catch (Exception e) {
            return "SYS";
        }
    }
}
