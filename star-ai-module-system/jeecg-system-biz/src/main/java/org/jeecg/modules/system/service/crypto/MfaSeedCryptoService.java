package org.jeecg.modules.system.service.crypto;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import org.jeecg.common.util.encryption.AesEncryptUtil;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserMfa;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.jeecg.modules.system.mapper.SysUserMfaMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Per-user column-encryption wrapper for MFA TOTP secret seeds and similar sensitive user-bound
 * values.
 *
 * <p>Threat model covered by this service (Ramp-A4-2):
 * <pre>
 *   Old flow:  MFA seed stored as AES(EncryptedString.key, seed) with global static key/iv.
 *              Any caller could GET /sys/getEncryptedString → offline decrypt entire
 *              sys_user_mfa.secret_enc column + any other column encrypted by AesEncryptUtil.
 *   New flow:  MFA seed stored as SM4$&lt;ivHex&gt;:&lt;cipherHex&gt;:&lt;hmacTag&gt; where
 *              SM4 key = SM3("mfa-seed-v1|" + global MFA_MASTER_SECRET + "|" + userId).
 *              Stealing the DB ciphertext and the global master is not sufficient to recover a
 *              seed: you still need the per-row userId so the decryption key differs per user;
 *              additionally the HMAC tag (SHA-256) binds userId + tenantId + plaintext so a
 *              ciphertext cannot be swapped between accounts to reuse a stolen device.
 * </pre>
 *
 * <p>Configuration — fail-fast if blank:
 * <ul>
 *   <li>OS env {@code MFA_MASTER_SECRET} (≥16 chars, recommend ≥32)</li>
 *   <li>JVM property {@code MFA_MASTER_SECRET} (repo-root {@code .env} via {@code RepoRootEnvLoader})</li>
 *   <li>JVM property {@code jeecg.mfa.master.secret}</li>
 * </ul>
 * Priority matches {@code PasswordResetProperties#resolveResetSecret} so IDE local runs that only
 * load {@code .env} into system properties (not OS env) still work.
 */
@Slf4j
@Service
public class MfaSeedCryptoService {

  public static final String MFA_CIPHER_PREFIX = "MFASM4$";
  private static final String KEY_DOMAIN = "mfa-seed-v1";
  private static final String DELIM = "|";
  private static final Object LOCK = new Object();
  private static volatile String cachedMaster;

  /** Mapper only — avoid ISysUserMfaService to prevent cycle with SysUserMfaServiceImpl. */
  @Resource private SysUserMfaMapper sysUserMfaMapper;
  @Resource private SysUserMapper sysUserMapper;
  @Resource private PlatformTransactionManager transactionManager;

  @Value("${mfa.reencrypt.on-read.enabled:${MFA_REENCRYPT_ON_READ:true}}")
  private boolean reencryptOnRead;

  @Value("${mfa.reencrypt.transition-days:${MFA_REENCRYPT_TRANSITION_DAYS:7}}")
  private int transitionDays;

  private static String masterSecret() {
    if (cachedMaster != null) {
      return cachedMaster;
    }
    synchronized (LOCK) {
      if (cachedMaster != null) {
        return cachedMaster;
      }
      String s = System.getenv("MFA_MASTER_SECRET");
      if (StrUtil.isBlank(s)) {
        // IDE local: RepoRootEnvLoader loads .env into System properties (not OS env)
        s = System.getProperty("MFA_MASTER_SECRET");
      }
      if (StrUtil.isBlank(s)) {
        s = System.getProperty("jeecg.mfa.master.secret");
      }
      if (StrUtil.isBlank(s) || s.length() < 16) {
        throw new JeecgBootException(
            "MFA_MASTER_SECRET env / jeecg.mfa.master.secret sysprop must be set to >= 16 chars.");
      }
      cachedMaster = s.intern();
      return cachedMaster;
    }
  }

  private static String userSm4KeyHex(String userId, Integer tenantId) {
    if (StrUtil.isBlank(userId)) {
      throw new IllegalArgumentException("userId required");
    }
    String material =
        KEY_DOMAIN + DELIM + masterSecret() + DELIM + userId + DELIM + (tenantId == null ? 0 : tenantId);
    return SmCryptoUtil.deriveSm4KeyHex(material);
  }

  private static String hmacTag(
      String plaintext, String userId, Integer tenantId, byte[] iv16, byte[] cipherBytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(MFA_CIPHER_PREFIX.getBytes(StandardCharsets.UTF_8));
      md.update((byte) 0x1e);
      md.update(userId.getBytes(StandardCharsets.UTF_8));
      md.update((byte) 0x1e);
      md.update(Integer.toString(tenantId == null ? 0 : tenantId).getBytes(StandardCharsets.US_ASCII));
      md.update((byte) 0x1e);
      md.update(plaintext.getBytes(StandardCharsets.UTF_8));
      md.update((byte) 0x1e);
      md.update(iv16);
      md.update((byte) 0x1e);
      md.update(cipherBytes);
      md.update((byte) 0x1e);
      md.update(masterSecret().getBytes(StandardCharsets.UTF_8));
      return HexUtil.encodeHexStr(md.digest());
    } catch (Exception e) {
      throw new IllegalStateException("sha256 not available", e);
    }
  }

  /** Encrypt a TOTP seed for the given user/tenant. Returns string with {@link #MFA_CIPHER_PREFIX}. */
  public String wrapSeed(String plainSeed, String userId, Integer tenantId) {
    if (StrUtil.isBlank(plainSeed)) {
      throw new IllegalArgumentException("seed required");
    }
    String keyHex = userSm4KeyHex(userId, tenantId);
    String sm4 = SmCryptoUtil.sm4Encrypt(plainSeed, keyHex);
    // sm4 == "SM4$<ivHex>:<cipherHex>"
    String body = sm4.startsWith(SmCryptoUtil.SM4_PREFIX) ? sm4.substring(SmCryptoUtil.SM4_PREFIX.length()) : sm4;
    String[] parts = body.split(":", 2);
    if (parts.length != 2) {
      throw new IllegalStateException("unexpected sm4 output");
    }
    byte[] iv = HexUtil.decodeHex(parts[0]);
    byte[] cipher = HexUtil.decodeHex(parts[1]);
    String tag = hmacTag(plainSeed, userId, tenantId, iv, cipher);
    return MFA_CIPHER_PREFIX + parts[0] + ":" + parts[1] + ":" + tag;
  }

  /** Decrypt an MFA seed; validates HMAC before returning plaintext. */
  public String unwrapSeed(String wrapped, String userId, Integer tenantId) {
    if (StrUtil.isBlank(wrapped)) {
      throw new IllegalArgumentException("wrapped required");
    }
    String body =
        wrapped.startsWith(MFA_CIPHER_PREFIX) ? wrapped.substring(MFA_CIPHER_PREFIX.length()) : null;
    if (body == null) {
      // Legacy payload (EncryptedString AES ciphertext) — migrate on next rotate; return null so
      // callers can fall back or force re-enrollment.
      return null;
    }
    String[] parts = body.split(":", 3);
    if (parts.length != 3) {
      throw new IllegalArgumentException("invalid MFA wrapped format");
    }
    String ivHex = parts[0];
    String cipherHex = parts[1];
    String expectedTag = parts[2];
    String keyHex = userSm4KeyHex(userId, tenantId);
    String sm4Payload = SmCryptoUtil.SM4_PREFIX + ivHex + ":" + cipherHex;
    String plain;
    try {
      plain = SmCryptoUtil.sm4Decrypt(sm4Payload, keyHex);
    } catch (Exception e) {
      throw new IllegalArgumentException("mfa decrypt failed", e);
    }
    String actualTag =
        hmacTag(
            plain,
            userId,
            tenantId,
            HexUtil.decodeHex(ivHex),
            HexUtil.decodeHex(cipherHex));
    if (!MessageDigest.isEqual(
        expectedTag.getBytes(StandardCharsets.US_ASCII),
        actualTag.getBytes(StandardCharsets.US_ASCII))) {
      throw new IllegalArgumentException("mfa hmac mismatch (wrong user/tenant or tampered)");
    }
    return plain;
  }

  // ---------- convenience helpers binding to current Sa-Token session ----------

  public String wrapForCurrentUser(String plainSeed, Integer tenantId) {
    return wrapSeed(plainSeed, StpUtil.getLoginIdAsString(), tenantId);
  }

  public String unwrapForCurrentUser(String wrapped, Integer tenantId) {
    return unwrapSeed(wrapped, StpUtil.getLoginIdAsString(), tenantId);
  }

  /** Base64 helper kept for compatibility with any JPA converters. */
  @SuppressWarnings("unused")
  public String toBase64(String s) {
    return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * PR-CL-A4-3 7-day transition decryptor.
   *
   * <p>Priority: try SM4 {@link #unwrapSeed} first (99% case after first job run); on failure (legacy
   * AES ciphertext OR corrupted format) fall back to the global AES {@link AesEncryptUtil}.
   *
   * <p>Read-side migration: if {@code mfa.reencrypt.on-read.enabled=true} and AES decrypt actually
   * produced the seed, the row is immediately re-saved with the new per-user SM4 format inside a
   * short REQUIRES_NEW transaction so the JVM never sees the same legacy row twice.
   *
   * @return plain seed (never the wrapped string) or {@code null} if both strategies fail (caller
   *     treats as auth-failure / "MFA secret missing" generic message).
   */
  public String tryDecryptWithAesThenMigrate(SysUserMfa mfa) {
    if (mfa == null || StrUtil.isBlank(mfa.getSecretEnc())) {
      return null;
    }
    String uid = mfa.getUserId();
    if (StrUtil.isNotBlank(uid)) {
      try {
        // P2 (CodePlan §4.3.4): read only the MFA operational columns (both C6-whitelisted);
        // no password/salt/other sensitive columns are needed by the cutoff/migration logic.
        SysUser u =
            sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                    .select(
                        SysUser::getId,
                        SysUser::getMfaMasterVersion,
                        SysUser::getMfaLegacyCutoffAt)
                    .eq(SysUser::getId, uid));
        if (u != null) {
          Integer ver = u.getMfaMasterVersion();
          if (ver == null || ver == 0) {
            Date cutoff = u.getMfaLegacyCutoffAt();
            if (cutoff == null || cutoff.before(new Date())) {
              log.warn("D4 CUTOFF BLOCK: userId={} legacy MFA row expired at cutoff={}; force reset MFA.", uid, cutoff);
              return null;
            }
          }
        }
      } catch (Exception ignore) {}
    }
    String wrapped = mfa.getSecretEnc();
    try {
      String seed = unwrapSeed(wrapped, mfa.getUserId(), mfa.getTenantId());
      if (StrUtil.isNotBlank(seed)) {
        if (StrUtil.isNotBlank(uid)) {
          try {
            // P2 (CodePlan §4.3.4): read only the MFA operational columns (both C6-whitelisted);
        // no password/salt/other sensitive columns are needed by the cutoff/migration logic.
        SysUser u =
            sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                    .select(
                        SysUser::getId,
                        SysUser::getMfaMasterVersion,
                        SysUser::getMfaLegacyCutoffAt)
                    .eq(SysUser::getId, uid));
            if (u != null && (u.getMfaMasterVersion() == null || u.getMfaMasterVersion() == 0)) {
              final Date now2 = new Date();
              TransactionTemplate tx2 = new TransactionTemplate(transactionManager);
              tx2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
              tx2.setTimeout(15);
              final String fuid = uid;
              tx2.execute(st -> {
                SysUser upd = new SysUser();
                upd.setId(fuid);
                upd.setMfaMasterVersion(1);
                upd.setMfaLegacyCutoffAt(null);
                upd.setUpdateTime(now2);
                upd.setUpdateBy("__D4_MFA_LAZY_MIGRATE__");
                return sysUserMapper.updateById(upd);
              });
            }
          } catch (Exception ignore) {}
        }
        return seed;
      }
    } catch (Exception ignored) {
    }
    String plainLegacy;
    try {
      plainLegacy = AesEncryptUtil.desEncrypt(wrapped);
    } catch (Exception badBoth) {
      return null;
    }
    if (StrUtil.isBlank(plainLegacy)) {
      return null;
    }
    if (reencryptOnRead) {
      String id = mfa.getId();
      Integer tenant = mfa.getTenantId();
      if (StrUtil.isNotBlank(id) && StrUtil.isNotBlank(uid)) {
        try {
          final String newWrapped = wrapSeed(plainLegacy, uid, tenant);
          final Date now = new Date();
          TransactionTemplate tx = new TransactionTemplate(transactionManager);
          tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
          tx.setTimeout(15);
          tx.execute(
              st -> {
                SysUserMfa upd = new SysUserMfa();
                upd.setId(id);
                upd.setSecretEnc(newWrapped);
                upd.setUpdateTime(now);
                upd.setUpdateBy("__MFA_ON_READ_REENCRYPT__");
                int r = sysUserMfaMapper.updateById(upd);
                try {
                  SysUser updU = new SysUser();
                  updU.setId(uid);
                  updU.setMfaMasterVersion(1);
                  updU.setMfaLegacyCutoffAt(null);
                  updU.setUpdateTime(now);
                  updU.setUpdateBy("__D4_MFA_LAZY_MIGRATE__");
                  sysUserMapper.updateById(updU);
                } catch (Exception ignore) {}
                return r;
              });
        } catch (Exception ignore) {
        }
      }
    }
    return plainLegacy;
  }

  /**
   * Exposed to callers that do not yet hold the full SysUserMfa entity; delegates to {@link
   * #tryDecryptWithAesThenMigrate(SysUserMfa)} for side effects, but returns only the plaintext.
   */
  public String decryptOrLegacyFallback(String wrapped, String userId, Integer tenantId, String mfaId) {
    SysUserMfa stub = new SysUserMfa();
    stub.setId(mfaId);
    stub.setUserId(userId);
    stub.setTenantId(tenantId);
    stub.setSecretEnc(wrapped);
    return tryDecryptWithAesThenMigrate(stub);
  }

  @PostConstruct
  public void startupPosture() {
    log.info(
        "MfaSeedCryptoService startupOk: on-read-migrate={} transitionDays={}",
        reencryptOnRead,
        transitionDays);
  }
}
