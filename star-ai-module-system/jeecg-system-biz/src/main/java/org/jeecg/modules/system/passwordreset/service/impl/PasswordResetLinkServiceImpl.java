package org.jeecg.modules.system.passwordreset.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.system.passwordreset.validator.PasswordPolicyValidator;
import org.jeecg.common.system.passwordreset.vo.PasswordResetLinkVO;
import org.jeecg.common.system.passwordreset.vo.PasswordResetMetaVO;
import org.jeecg.common.system.passwordreset.exception.PasswordResetErrorCode;
import org.jeecg.common.system.passwordreset.exception.PasswordResetException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.BusinessRuleConfig;
import org.jeecg.modules.base.service.IBusinessRuleService;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.jeecg.modules.system.passwordreset.PasswordResetConstants;
import org.jeecg.modules.system.passwordreset.config.PasswordResetProperties;
import org.jeecg.modules.system.passwordreset.entity.SysPasswordResetAccessLog;
import org.jeecg.modules.system.passwordreset.entity.SysPasswordResetLink;
import org.jeecg.modules.system.passwordreset.entity.SysPasswordResetUsageLog;
import org.jeecg.modules.system.passwordreset.helper.ClientIpResolver;
import org.jeecg.modules.system.passwordreset.helper.PasswordResetCaptchaHelper;
import org.jeecg.modules.system.passwordreset.helper.PasswordResetMetrics;
import org.jeecg.modules.system.passwordreset.helper.PasswordResetTokenHelper;
import org.jeecg.modules.system.passwordreset.mapper.SysPasswordResetAccessLogMapper;
import org.jeecg.modules.system.passwordreset.mapper.SysPasswordResetLinkMapper;
import org.jeecg.modules.system.passwordreset.mapper.SysPasswordResetUsageLogMapper;
import org.jeecg.modules.system.passwordreset.service.IPasswordResetLinkService;
import org.jeecg.modules.system.util.RsaPasswordUtil;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetLinkServiceImpl
        extends ServiceImpl<SysPasswordResetLinkMapper, SysPasswordResetLink>
        implements IPasswordResetLinkService, PasswordResetConstants {

    private static final String UPDATE_BY_PREFIX = "PWD_RESET_LINK:";

    private final PasswordResetProperties props;
    private final PasswordResetTokenHelper tokenHelper;
    private final ClientIpResolver clientIpResolver;
    private final SysPasswordResetAccessLogMapper accessLogMapper;
    private final SysPasswordResetUsageLogMapper usageLogMapper;
    private final SysUserMapper userMapper;
    private final IBusinessRuleService businessRuleService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final RsaPasswordUtil rsaPasswordUtil;
    private final PasswordResetCaptchaHelper captchaHelper;
    private final PasswordResetMetrics metrics;
    private final org.jeecg.modules.system.service.impl.SessionKickService sessionKickService;

    // =========================== create ===========================
    /**
     * REQUIRES_NEW: ops account creation builds chain in afterCommit; outer tx committed but sync callback not cleaned up;
     * must open new tx or insert may land on committed connection, failing chain build and aborting account email.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PasswordResetLinkVO create(String userId,
                                   String username,
                                   int ttlMinutes,
                                   int maxFailAttempts,
                                   String source,
                                   String creator,
                                   String creatorIp,
                                   String remark) {
        boolean ok = false;
        try {
            // 1. Validate params: at least one of userId / username
            boolean hasUserId = StringUtils.hasText(userId);
            boolean hasUsername = StringUtils.hasText(username);
            if (!hasUserId && !hasUsername) {
                throw new PasswordResetException(PasswordResetErrorCode.NOT_FOUND, "userId and username cannot both be empty");
            }
        ttlMinutes = clamp(ttlMinutes, PasswordResetLinkFacade_MIN, PasswordResetLinkFacade_MAX_TTL, props.getDefaultTtlMinutes());
        maxFailAttempts = clamp(maxFailAttempts, PasswordResetLinkFacade_MIN_FAIL, PasswordResetLinkFacade_MAX_FAIL, props.getDefaultMaxFailAttempts());
        source = StringUtils.hasText(source) ? source : "OPS_RESET";

        // 2. Ensure user exists and enabled (prefer userId; if both passed, usernames must match)
        SysUser user = null;
        if (hasUserId) {
            user = userMapper.selectById(userId);
            if (user == null || (user.getDelFlag() != null && user.getDelFlag() == 1)) {
                throw new PasswordResetException(PasswordResetErrorCode.USER_DISABLED, "User not found (by userId)");
            }
            // If username also passed, must match; else throw (misuse guard)
            if (hasUsername && !username.equals(user.getUsername())) {
                throw new PasswordResetException(PasswordResetErrorCode.NOT_FOUND,
                        "userId and username do not match");
            }
            // Normalize username to real value so link.username is never empty when omitted
            username = user.getUsername();
        } else {
            user = userMapper.getUserByName(username);
            if (user == null || (user.getDelFlag() != null && user.getDelFlag() == 1)) {
                throw new PasswordResetException(PasswordResetErrorCode.USER_DISABLED, "User not found");
            }
            userId = user.getId();
        }
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw new PasswordResetException(PasswordResetErrorCode.USER_DISABLED, "User is frozen or locked");
        }

        // 3. Concurrency limit: PENDING rows per username+source <= maxPendingPerUserSource
        long pendingCount = count(new LambdaQueryWrapper<SysPasswordResetLink>()
                .eq(SysPasswordResetLink::getUsername, username)
                .eq(SysPasswordResetLink::getSource, source)
                .eq(SysPasswordResetLink::getStatus, PENDING));
        if (pendingCount >= props.getMaxPendingPerUserSource()) {
            throw new PasswordResetException(PasswordResetErrorCode.TOO_MANY_PENDING,
                    "Too many unused reset links for this account (limit " + props.getMaxPendingPerUserSource() + "). Please try again later");
        }

        // 4. Generate token + signature
        String token = tokenHelper.generateToken();
        String signature = tokenHelper.computeSignature(token);

        // 5. Persist
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + (long) ttlMinutes * 60L * 1000L);
        String creatorVal = StringUtils.hasText(creator) ? creator : "SYS";
        SysPasswordResetLink link = new SysPasswordResetLink()
                .setToken(token)
                .setSignature(signature)
                .setTokenVersion("01")
                .setUsername(username)
                .setUserId(user.getId())
                .setSource(source)
                .setCreatorIp(creatorIp)
                .setTtlMinutes(ttlMinutes)
                .setExpireTime(expireAt)
                .setMaxFailAttempts(maxFailAttempts)
                .setFailCount(0)
                .setStatus(PENDING)
                .setConsumedFlag("N")
                .setCreateBy(creatorVal)
                // Dual-write create_by: legacy creator NOT NULL without default; omit breaks link + account email
                .setCreator(creatorVal)
                // Legacy challenge NOT NULL: challenge now in Redis; insert empty placeholder
                .setChallenge("")
                .setCreateTime(now)
                .setUpdateBy(creatorVal)
                .setUpdateTime(now)
                .setVersion(0)
                .setRemark(remark);
        try {
            baseMapper.insert(link);
        } catch (DataAccessException e) {
            log.error(
                    "password reset link insert failed userId={} username={} source={} root={}",
                    user.getId(),
                    username,
                    source,
                    rootMessage(e),
                    e);
            throw e;
        }
        log.info(
                "password reset link created id={} userId={} username={} source={} ttlMinutes={}",
                link.getId(),
                user.getId(),
                username,
                source,
                ttlMinutes);

        // 6. Build response (join pathSegment parts so "/" in prefix is not encoded as %2F)
        String resetUrl = buildResetUrl(token);
        LocalDateTime expireLdt = expireAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        PasswordResetLinkVO vo = new PasswordResetLinkVO()
                .setToken(token)
                .setResetUrl(resetUrl)
                .setExpireTime(expireLdt)
                .setTtlMinutes(ttlMinutes);
        ok = true;
        return vo;
        } finally {
            metrics.countCreate(source, ok);
        }
    }

    // =========================== getLinkByTokenSafe ===========================
    @Override
    public PasswordResetLinkVO getLinkByTokenSafe(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        SysPasswordResetLink link = baseMapper.selectOne(
                new LambdaQueryWrapper<SysPasswordResetLink>()
                        .eq(SysPasswordResetLink::getToken, token)
                        .last("LIMIT 1"));
        if (link == null) {
            return null;
        }
        // Only a still-usable PENDING link is worth embedding in a notification email.
        if (!PENDING.equals(resolveFinalStatus(link))) {
            return null;
        }
        LocalDateTime expireLdt = link.getExpireTime() == null
                ? null
                : link.getExpireTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return new PasswordResetLinkVO()
                .setToken(link.getToken())
                .setResetUrl(buildResetUrl(link.getToken()))
                .setExpireTime(expireLdt)
                .setTtlMinutes(link.getTtlMinutes());
    }

    // =========================== getMeta ===========================
    @Override
    public PasswordResetMetaVO getMeta(String token, HttpServletRequest request) {
        long start = System.currentTimeMillis();
        ClientIpResolver.ClientIp ci = clientIpResolver.resolve(request);
        String clientIp = ci.getClientIp();
        String ua = header(request, "User-Agent");
        String referer = header(request, "Referer");
        String al = header(request, "Accept-Language");
        boolean metaOk = false;
        try {
        // 1. HMAC first: lookup token → load signature from DB → verify with dual secrets
        SysPasswordResetLink link = baseMapper.selectOne(
                new LambdaQueryWrapper<SysPasswordResetLink>().eq(SysPasswordResetLink::getToken, token).last("LIMIT 1"));
        if (link == null) {
            writeAccess(token, null, "META", "INVALID", "TOKEN_NOT_FOUND", ci, ua, referer, al, start);
            throw new PasswordResetException(PasswordResetErrorCode.LINK_INVALID, "Invalid link");
        }
        PasswordResetTokenHelper.VerifyResult verify = tokenHelper.verifySignature(token, link.getSignature());
        if (verify == PasswordResetTokenHelper.VerifyResult.MISMATCH) {
            writeAccess(token, link.getId(), "META", "SIGN_ERR", "SIGNATURE_INVALID", ci, ua, referer, al, start);
            throw new PasswordResetException(PasswordResetErrorCode.LINK_INVALID, "Invalid link");
        }
        // 2. Resolve status
        String status = resolveFinalStatus(link);
        if (!PENDING.equals(status)) {
            String errCode = resolveErrCode(status);
            writeAccess(token, link.getId(), "META", errCode, errCode, ci, ua, referer, al, start);
            throw new PasswordResetException(PasswordResetErrorCode.LINK_INVALID, "Invalid link");
        }
        // 3. Remaining failure attempts
        int remaining = Math.max(0, link.getMaxFailAttempts() - link.getFailCount());
        boolean needCaptcha = link.getFailCount() >= props.getNeedCaptchaAfterFailures();
        // 4. challenge
        String challenge = tokenHelper.generateChallenge(token, clientIp);
        // 5. Log access OK
        writeAccess(token, link.getId(), "META", "OK", null, ci, ua, referer, al, start);

        PasswordResetMetaVO.PasswordPolicyVO policy = new PasswordResetMetaVO.PasswordPolicyVO()
                .setMinLength(passwordPolicyValidator.getMinLength())
                .setMaxLength(passwordPolicyValidator.getMaxLength())
                .setRequireUppercase(passwordPolicyValidator.isRequireUppercase())
                .setRequireLowercase(passwordPolicyValidator.isRequireLowercase())
                .setRequireNumber(passwordPolicyValidator.isRequireNumber())
                .setRequireSymbol(passwordPolicyValidator.isRequireSymbol())
                .setDisallowUsernameSubstring(passwordPolicyValidator.isDisallowUsernameSubstring());
        String expStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(
                link.getExpireTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        PasswordResetMetaVO meta = new PasswordResetMetaVO()
                .setStatus(status)
                .setExpireTime(expStr)
                .setRemainingAttempts(remaining)
                .setNeedCaptcha(needCaptcha)
                .setChallenge(challenge)
                .setPasswordPolicy(policy);
        metaOk = true;
        return meta;
        } finally {
            metrics.countMeta(metaOk);
        }
    }

    // =========================== preValidate ===========================
    @Override
    public SysPasswordResetLink preValidate(String token, HttpServletRequest request) {
        long start = System.currentTimeMillis();
        ClientIpResolver.ClientIp ci = clientIpResolver.resolve(request);
        String ua = header(request, "User-Agent");
        String referer = header(request, "Referer");
        String al = header(request, "Accept-Language");

        SysPasswordResetLink link = baseMapper.selectOne(
                new LambdaQueryWrapper<SysPasswordResetLink>().eq(SysPasswordResetLink::getToken, token).last("LIMIT 1"));
        if (link == null) {
            // access_type varchar(16): use PRE_CHECK, not SUBMIT_PRE_VALIDATE (18 chars truncates)
            writeAccess(token, null, "PRE_CHECK", "INVALID", "TOKEN_NOT_FOUND", ci, ua, referer, al, start);
            throw new PasswordResetException(PasswordResetErrorCode.LINK_INVALID, "Invalid link");
        }
        PasswordResetTokenHelper.VerifyResult verify = tokenHelper.verifySignature(token, link.getSignature());
        if (verify == PasswordResetTokenHelper.VerifyResult.MISMATCH) {
            writeAccess(token, link.getId(), "PRE_CHECK", "SIGN_ERR", "SIGNATURE_INVALID", ci, ua, referer, al, start);
            throw new PasswordResetException(PasswordResetErrorCode.LINK_INVALID, "Invalid link");
        }
        String status = resolveFinalStatus(link);
        if (!PENDING.equals(status)) {
            String errCode = resolveErrCode(status);
            writeAccess(token, link.getId(), "PRE_CHECK", errCode, errCode, ci, ua, referer, al, start);
            throw new PasswordResetException(PasswordResetErrorCode.LINK_INVALID, "Invalid link");
        }
        writeAccess(token, link.getId(), "PRE_CHECK", "OK", null, ci, ua, referer, al, start);
        return link;
    }

    // =========================== submitPassword (core transaction) ===========================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPassword(String token,
                               String challenge,
                               String rsaCacheKey,
                               String encryptedPassword1,
                               String encryptedPassword2,
                               String captcha,
                               String captchaKey,
                               HttpServletRequest request) {
        long start = System.currentTimeMillis();
        ClientIpResolver.ClientIp ci = clientIpResolver.resolve(request);
        String clientIp = ci.getClientIp();
        String xffRaw = ci.getXForwardedForRaw();
        String ua = header(request, "User-Agent");
        boolean submitOk = false;
        String submitStage = null;
        // Step 1: quickly validate link + challenge
        SysPasswordResetLink link = preValidate(token, request); // Throw LINK_INVALID
        try {
            try {
                tokenHelper.verifyChallenge(challenge, token, clientIp, request);
            } catch (PasswordResetException e) {
                submitStage = "CHALLENGE_FAIL";
                incrementFailAndMaybeLock(link, "CHALLENGE_FAIL", e.getMessage(), request, ci, xffRaw, ua, rsaCacheKey, start);
                // Keep CHALLENGE_FAIL so front-end can refresh challenge; do not collapse to LINK_INVALID
                throw e;
            }

            // Step 2: captcha required when fail_count >= needCaptchaAfterFailures
            boolean needCaptcha = link.getFailCount() >= props.getNeedCaptchaAfterFailures();
            if (needCaptcha) {
                if (!captchaHelper.verify(captcha, captchaKey)) {
                    submitStage = "CAPTCHA_FAIL";
                    incrementFailAndMaybeLock(link, "CAPTCHA_FAIL", "Invalid captcha", request, ci, xffRaw, ua, rsaCacheKey, start);
                    throw new PasswordResetException(PasswordResetErrorCode.CAPTCHA_REQUIRED, "Invalid captcha");
                }
            }

            // Step 3: RSA decrypt both passwords. One rsaCacheKey maps to one private key,
            // Do not decryptPasswordAndClean twice: first success deletes Redis key; second fails as Session expired.
            String pwd1, pwd2;
            try {
                pwd1 = rsaPasswordUtil.decryptPassword(rsaCacheKey, encryptedPassword1);
                pwd2 = rsaPasswordUtil.decryptPassword(rsaCacheKey, encryptedPassword2);
            } catch (Exception e) {
                submitStage = "RSA_DECRYPT";
                incrementFailAndMaybeLock(link, "RSA_DECRYPT", "RSA decryption failed: key may have expired. Please reopen the link", request, ci, xffRaw, ua, rsaCacheKey, start);
                throw new PasswordResetException(PasswordResetErrorCode.RSA_DECRYPT_FAIL, "Session expired. Please refresh and try again", e);
            }
            if (!StringUtils.hasText(pwd1) || !StringUtils.hasText(pwd2)) {
                submitStage = "RSA_DECRYPT";
                incrementFailAndMaybeLock(link, "RSA_DECRYPT", "RSA decryption returned empty result", request, ci, xffRaw, ua, rsaCacheKey, start);
                throw new PasswordResetException(PasswordResetErrorCode.RSA_DECRYPT_FAIL, "Session expired. Please refresh and try again");
            }
            // Delete private key only after both decrypts succeed (anti-replay)
            rsaPasswordUtil.evictPrivateKey(rsaCacheKey);

            // Step 4: both passwords match
            if (!pwd1.equals(pwd2)) {
                submitStage = "PWD_MISMATCH";
                incrementFailAndMaybeLock(link, "PWD_MISMATCH", "Passwords do not match", request, ci, xffRaw, ua, rsaCacheKey, start);
                throw new PasswordResetException(PasswordResetErrorCode.PWD_MISMATCH, "Passwords do not match");
            }

            // Step 5: strength compliance
            PasswordPolicyValidator.ValidationResult vr = passwordPolicyValidator.validate(pwd1, link.getUsername());
            if (!vr.isValid()) {
                submitStage = "PWD_WEAK";
                incrementFailAndMaybeLock(link, "PWD_WEAK", vr.getMessage(), request, ci, xffRaw, ua, rsaCacheKey, start);
                throw new PasswordResetException(PasswordResetErrorCode.PWD_WEAK, vr.getMessage());
            }

            // Step 6: not equal to old password
            SysUser user = userMapper.getUserByName(link.getUsername());
            if (user == null) {
                submitStage = "USER_NOT_FOUND";
                incrementFailAndMaybeLock(link, "USER_NOT_FOUND", "Account not found", request, ci, xffRaw, ua, rsaCacheKey, start);
                throw new PasswordResetException(PasswordResetErrorCode.USER_DISABLED, "User not found");
            }

            // P3-4: password must not contain >=4 consecutive chars from user email/phone
            String[] extras = new String[]{user.getEmail(), user.getPhone()};
            int minLen = passwordPolicyValidator.getDisallowUsernameSubstringLength();
            for (String extra : extras) {
                if (!org.springframework.util.StringUtils.hasText(extra)) continue;
                String lowExtra = extra.toLowerCase();
                String lowPwd = pwd1.toLowerCase();
                if (lowExtra.length() >= minLen) {
                    boolean bad = false;
                    for (int i = 0; i + minLen <= lowExtra.length(); i++) {
                        String sub = lowExtra.substring(i, i + minLen);
                        if (lowPwd.contains(sub)) { bad = true; break; }
                    }
                    if (bad) {
                        submitStage = "PWD_WEAK";
                        String msg = "Password must not contain a consecutive substring from email/phone (≥" + minLen + " characters)";
                        incrementFailAndMaybeLock(link, "PWD_WEAK", msg, request, ci, xffRaw, ua, rsaCacheKey, start);
                        throw new PasswordResetException(PasswordResetErrorCode.PWD_WEAK, msg);
                    }
                }
            }
            if (StringUtils.hasText(user.getPassword())
                && org.jeecg.common.util.CredentialGuard.isPresentLoginPassword(pwd1)
                && org.jeecg.common.util.CredentialGuard.hasStoredPasswordHash(user.getPassword())) {
                try {
                    if (SmCryptoUtil.checkPassword(pwd1, user.getPassword())) {
                        submitStage = "PWD_SAME_AS_OLD";
                        incrementFailAndMaybeLock(link, "PWD_SAME_AS_OLD", "New password must be different from the current password", request, ci, xffRaw, ua, rsaCacheKey, start);
                        throw new PasswordResetException(PasswordResetErrorCode.PWD_SAME_AS_OLD, "New password must be different from the current password");
                    }
                } catch (PasswordResetException pre) {
                    throw pre;
                } catch (Exception ignore) {
                    // If old password is not BCrypt, skip same-as-old check (force change)
                }
            }

            // Step 7: hash new password with BCrypt
            int rounds = props.getBcryptLogRounds();
            String newPasswordHashed;
            try {
                newPasswordHashed = SmCryptoUtil.hashPassword(pwd1);
            } catch (Exception e) {
                submitStage = "BCYPT_FAIL";
                incrementFailAndMaybeLock(link, "BCYPT_FAIL", "Failed to hash password", request, ci, xffRaw, ua, rsaCacheKey, start);
                throw new PasswordResetException(PasswordResetErrorCode.RSA_DECRYPT_FAIL, "Internal server error. Please try again later", e);
            }

            // Step 8: UPDATE sys_user
            Date now = new Date();
            String updateBy = UPDATE_BY_PREFIX + link.getSource();
            int userRows = userMapper.updatePasswordAndLastChangeTimeByUsername(
                    newPasswordHashed, updateBy, now, now, link.getUsername());
            if (userRows != 1) {
                submitStage = "BCYPT_FAIL";
                incrementFailAndMaybeLock(link, "BCYPT_FAIL", "Failed to update sys_user, affected rows=" + userRows, request, ci, xffRaw, ua, rsaCacheKey, start);
                throw new PasswordResetException(PasswordResetErrorCode.LINK_INVALID, "Internal server error. Please try again later");
            }

            // Step 9: optimistic UPDATE link → USED
            int linkRows = baseMapper.consumeLinkById(link.getId(), link.getVersion(), new Date(), clientIp, ua);
            if (linkRows != 1) {
                // Concurrency conflict! rollback sys_user UPDATE (restore password + update_by/time)
                try {
                    int rollback = userMapper.rollbackPasswordByUsername(user.getPassword(), user.getUpdateBy(), user.getUpdateTime(),
                            user.getLastPasswordChangeTime(), link.getUsername());
                    log.warn("Password reset concurrent conflict, rolled back sys_user affected rows={}", rollback);
                } catch (Exception rb) {
                    log.error("Password reset concurrent conflict, failed to roll back user table", rb);
                }
                submitStage = "CONCURRENT_CONFLICT";
                incrementFailAndMaybeLock(link, "CONCURRENT_CONFLICT", "Concurrent conflict: another request already processed this link", request, ci, xffRaw, ua, rsaCacheKey, start);
                throw new PasswordResetException(PasswordResetErrorCode.LINK_INVALID, "Link is no longer valid");
            }

            // Consume challenge after password set to avoid false already-used on fail/double-submit
            try {
                tokenHelper.consumeChallenge(challenge, token);
            } catch (PasswordResetException e) {
                // Link already USED: duplicate challenge consume only logs (secondary concurrent success)
                log.warn("password-reset consumeChallenge after success: {}");
            }

        // Step 10: usage_log SUCCESS + force logout + clear fail count + revoke other PENDING
        writeUsageSuccess(link, ci, xffRaw, ua, rsaCacheKey, start);
        // Business rule: clear fail count and unlock
        try {
            businessRuleService.resetFailedAttempts(BusinessRuleConfig.PASSWORD, link.getUsername(), request);
            businessRuleService.unlockAccount(link.getUsername(), request);
        } catch (Exception br) {
            log.warn("Failed to clear failure count/unlock after password reset: {}");
        }
        if (props.isKickOutSessionsOnSuccess()) {
            try {
                sessionKickService.kickAllDevices(user.getId(), "user_password_reset");
            } catch (Exception so) {
                log.warn("Kick offline exception after password reset: {}", so.toString());
            }
        }
        if (props.isRevokeSameUserPendingOnSuccess()) {
            try {
                int n = revokeAllPendingByUsername(link.getUsername(), UPDATE_BY_PREFIX + "AUTO", "Auto-revoked other pending links after successful reset");
                log.info("Auto-revoked PENDING links for same account after reset: {}", n);
            } catch (Exception e) {
                log.warn("Auto-revoke same-account PENDING link exception: {}");
            }
        }
        submitOk = true;
        } finally {
            metrics.countSubmit(submitOk, submitStage);
        }
    }

    // =========================== revoke ===========================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revoke(String token, String revokedBy, String revokeReason) {
        if (!StringUtils.hasText(token)) { metrics.countRevoke("token", false); return false; }
        SysPasswordResetLink link = baseMapper.selectOne(
                new LambdaQueryWrapper<SysPasswordResetLink>().eq(SysPasswordResetLink::getToken, token).last("LIMIT 1"));
        if (link == null) { metrics.countRevoke("token", false); return false; }
        if (!PENDING.equals(link.getStatus())) { metrics.countRevoke("token", false); return false; }
        int n = baseMapper.revokeLinkById(link.getId(), link.getVersion(), revokedBy, revokeReason, new Date());
        boolean ok = n == 1;
        metrics.countRevoke("token", ok);
        return ok;
    }

    /**
     * REQUIRES_NEW: account-creation email may revoke then create in afterCommit/async;
     * REQUIRED may attach to outer committed-but-not-cleaned-up tx, causing UPDATE lock and INSERT lock wait timeout.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int revokeAllPendingByUsername(String username, String revokedBy, String revokeReason) {
        if (!StringUtils.hasText(username)) { metrics.countRevoke("byUsername", false); return 0; }
        Date now = new Date();
        int n = baseMapper.batchRevokePendingByUsername(username, revokedBy, revokeReason, now);
        metrics.countRevoke("byUsername", n > 0);
        return n;
    }

    // =========================== Internal helpers ===========================

    /**
     * Build the reset URL from trusted config only: portalOrigin + pathPrefix + token.
     * Shared by link creation and {@link #getLinkByTokenSafe(String)} so both stay identical.
     */
    private String buildResetUrl(String token) {
        String pathPrefix = props.getResetPathPrefix() == null ? "reset-password" : props.getResetPathPrefix().trim();
        while (pathPrefix.startsWith("/")) {
            pathPrefix = pathPrefix.substring(1);
        }
        while (pathPrefix.endsWith("/")) {
            pathPrefix = pathPrefix.substring(0, pathPrefix.length() - 1);
        }
        UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromUriString(props.getPortalOrigin());
        if (!pathPrefix.isEmpty()) {
            for (String seg : pathPrefix.split("/")) {
                if (!seg.isEmpty()) {
                    urlBuilder.pathSegment(seg);
                }
            }
        }
        return urlBuilder.pathSegment(token).build().encode().toUriString();
    }

    private String resolveFinalStatus(SysPasswordResetLink link) {
        String st = link.getStatus();
        if (!PENDING.equals(st)) return st;
        // 1. PENDING but past expire time
        if (link.getExpireTime() != null && link.getExpireTime().before(new Date())) {
            // Lazy mark via batch Job; here only return EXPIRED to avoid meta optimistic conflicts
            return EXPIRED;
        }
        // 2. LOCKED past LOCKED TTL (1h) is treated as EXPIRED
        if (LOCKED.equals(st)) {
            if (link.getLockedAt() != null && (System.currentTimeMillis() - link.getLockedAt().getTime()) > (long) props.getLockedTtlMinutes() * 60L * 1000L) {
                return EXPIRED;
            }
        }
        // 3. fail_count hit max → LOCKED (atomically via incrementFailAndMaybeLock if DB lagging)
        if (link.getFailCount() != null && link.getMaxFailAttempts() != null
                && link.getFailCount() >= link.getMaxFailAttempts() && !LOCKED.equals(st)) {
            return LOCKED;
        }
        return PENDING;
    }

    private static String resolveErrCode(String finalStatus) {
        switch (finalStatus) {
            case USED:    return "TOKEN_USED";
            case REVOKED: return "TOKEN_REVOKED";
            case LOCKED:  return "TOKEN_LOCKED";
            case EXPIRED: return "TOKEN_EXPIRED";
            default:      return "TOKEN_UNKNOWN";
        }
    }

    private void writeAccess(String token, String linkId, String type, String result, String errCode,
                             ClientIpResolver.ClientIp ci, String ua, String referer, String al, long startMs) {
        try {
            SysPasswordResetAccessLog log = new SysPasswordResetAccessLog()
                    .setCreateTime(new Date())
                    .setToken(token)
                    .setLinkId(linkId)
                    // access_type / access_result Default varchar(16)
                    .setAccessType(truncate(type, 16))
                    .setAccessResult(truncate(result, 16))
                    .setAccessErrorCode(truncate(errCode, 64))
                    .setClientIp(ci.getClientIp())
                    .setXForwardedForRaw(ci.getXForwardedForRaw())
                    .setUserAgent(truncate(ua, 512))
                    .setReferer(truncate(referer, 512))
                    .setAcceptLanguage(truncate(al, 128))
                    .setServerLatencyMs((int) (System.currentTimeMillis() - startMs));
            accessLogMapper.insert(log);
        } catch (Exception e) {
            PasswordResetLinkServiceImpl.log.error("writeAccess Exception：{}");
        }
    }

    private void incrementFailAndMaybeLock(SysPasswordResetLink link, String stage, String reason,
                                           HttpServletRequest request,
                                           ClientIpResolver.ClientIp ci, String xffRaw, String ua,
                                           String rsaKey, long startMs) {
        String username = link.getUsername();
        // Business rule: fail count +1 (same counter as login password fails)
        try {
            businessRuleService.registerFailedAttempt(BusinessRuleConfig.PASSWORD, username, request);
        } catch (Exception ignore) {}
        // link.fail_count + 1; at max → LOCKED
        Integer current = link.getFailCount() == null ? 0 : link.getFailCount();
        int next = current + 1;
        boolean lock = next >= link.getMaxFailAttempts();
        try {
            baseMapper.incrementFailAndMaybeLock(link.getId(), lock, new Date());
        } catch (Exception ignore) {}
        // usage_log FAIL
        try {
            SysPasswordResetUsageLog u = new SysPasswordResetUsageLog()
                    .setCreateTime(new Date())
                    .setUpdateTime(new Date())
                    .setToken(link.getToken())
                    .setLinkId(link.getId())
                    .setUsername(username)
                    .setUsageResult("FAIL")
                    .setFailStage(stage)
                    .setFailReason(truncate(reason, 512))
                    .setPasswordChanged("N")
                    .setRsaCacheKey(truncate(rsaKey, 64))
                    .setClientIp(ci.getClientIp())
                    .setXForwardedForRaw(truncate(xffRaw, 512))
                    .setUserAgent(truncate(ua, 512))
                    .setServerLatencyMs((int) (System.currentTimeMillis() - startMs));
            usageLogMapper.insert(u);
        } catch (Exception e) {
            log.error("writeUsageFail Exception：{}");
        }
    }

    private void writeUsageSuccess(SysPasswordResetLink link, ClientIpResolver.ClientIp ci, String xffRaw, String ua,
                                   String rsaKey, long startMs) {
        try {
            SysPasswordResetUsageLog u = new SysPasswordResetUsageLog()
                    .setCreateTime(new Date())
                    .setUpdateTime(new Date())
                    .setToken(link.getToken())
                    .setLinkId(link.getId())
                    .setUsername(link.getUsername())
                    .setUsageResult("SUCCESS")
                    .setFailStage(null)
                    .setFailReason(null)
                    .setPasswordChanged("Y")
                    .setRsaCacheKey(truncate(rsaKey, 64))
                    .setClientIp(ci.getClientIp())
                    .setXForwardedForRaw(truncate(xffRaw, 512))
                    .setUserAgent(truncate(ua, 512))
                    .setServerLatencyMs((int) (System.currentTimeMillis() - startMs));
            usageLogMapper.insert(u);
        } catch (Exception e) {
            log.error("writeUsageSuccess Exception：{}");
        }
    }

    private static PasswordResetException wrapInvalid(PasswordResetException e) {
        if (PasswordResetErrorCode.CHALLENGE_FAIL.equals(e.getCode())) {
            return new PasswordResetException(PasswordResetErrorCode.LINK_INVALID, e.getMessage());
        }
        return e;
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage();
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    private static String header(HttpServletRequest r, String name) {
        if (r == null) return "";
        String v = r.getHeader(name);
        return v == null ? "" : v;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    // Define constants here to avoid static-import clashes
    private static final int PasswordResetLinkFacade_MIN = 5;
    private static final int PasswordResetLinkFacade_MAX_TTL = 1440;
    private static final int PasswordResetLinkFacade_MIN_FAIL = 1;
    private static final int PasswordResetLinkFacade_MAX_FAIL = 20;

    /** Persist access_log/usage_log even if TX rolls back (keep evidence on conflict) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forceFlushLogs() { /* Reserved: if an independent transaction for logs is needed, split into a separate Service here */ }
}
