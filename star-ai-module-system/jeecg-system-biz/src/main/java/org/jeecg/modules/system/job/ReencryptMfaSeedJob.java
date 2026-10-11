package org.jeecg.modules.system.job;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.encryption.AesEncryptUtil;
import org.jeecg.modules.system.entity.SysUserMfa;
import org.jeecg.modules.system.service.ISysUserMfaService;
import org.jeecg.modules.system.service.crypto.MfaSeedCryptoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PR-CL-A4-3.1: Migrate legacy AES-wrapped MFA secrets to the new per-user SM4 + HMAC bound format.
 *
 * <p>Semantics:
 *
 * <ol>
 *   <li>Cursor SELECTs rows where {@code LEFT(secret_enc, 8) != 'MFASM4$'} ordered by (tenantId, id)
 *       LIMIT 500 each batch.
 *   <li>For each row: {@code oldSeed = AesEncryptUtil.desEncrypt(secret_enc)} (old key) → {@code new =
 *       MfaSeedCryptoService.wrapSeed(oldSeed, userId, tenantId)}.
 *   <li>Save back in a REQUIRES_NEW short transaction (project_memory hard constraint: 2000ms leak
 *       detection, 8000ms max-wait, 30 pool size, keep the lock short).
 *   <li>Expose progress counters as structured logs so SRE dashboards + DBA validation SELECT can
 *       be compared.
 * </ol>
 *
 * <p>7-day transition window aligns with Ramp-A4 design: any concurrent decrypt in LoginController
 * that still carries a legacy ciphertext will round-trip via {@link
 * MfaSeedCryptoService#tryDecryptWithAesThenMigrate(String, String, Integer)} if enabled.
 */
@Slf4j
@Component
public class ReencryptMfaSeedJob {

  private static final int BATCH = 500;
  private static final long SAFETY_STOP_AFTER_ROWS = 2_000_000L;

  @Resource private ISysUserMfaService sysUserMfaService;
  @Resource private MfaSeedCryptoService mfaSeedCryptoService;
  @Resource private PlatformTransactionManager transactionManager;

  @Value("${mfa.reencrypt.enabled:${MFA_REENCRYPT_ENABLED:false}}")
  private boolean enabled;

  @Value("${mfa.reencrypt.batch:${MFA_REENCRYPT_BATCH:500}}")
  private int batchSize;

  @Value("${mfa.reencrypt.resume-from-id:${MFA_REENCRYPT_RESUME_ID:}}")
  private String resumeFromId;

  @XxlJob(value = "reencryptMfaSeedJob")
  @Scheduled(cron = "${mfa.reencrypt.cron:0 25 2 * * ?}")
  public void reencryptBatch() {
    if (!enabled) {
      log.info("ReencryptMfaSeedJob disabled (mfa.reencrypt.enabled=false) — skip.");
      return;
    }
    final int actualBatch = Math.max(10, Math.min(2000, batchSize));
    final AtomicLong migrated = new AtomicLong(0L);
    final AtomicLong failed = new AtomicLong(0L);
    final AtomicLong skipped = new AtomicLong(0L);
    String lastSeenId = StrUtil.blankToDefault(resumeFromId, "");
    try {
      log.info(
          "ReencryptMfaSeedJob START legacy_cursor_id={} batch_size={}", lastSeenId, actualBatch);
      TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
      txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      txTemplate.setTimeout(30);
      while (true) {
        LambdaQueryWrapper<SysUserMfa> q = new LambdaQueryWrapper<>();
        q.select(SysUserMfa::getId, SysUserMfa::getUserId, SysUserMfa::getTenantId, SysUserMfa::getSecretEnc);
        q.eq(SysUserMfa::getDelFlag, 0);
        q.and(w -> w.isNull(SysUserMfa::getSecretEnc).or().notLike(SysUserMfa::getSecretEnc, MfaSeedCryptoService.MFA_CIPHER_PREFIX));
        if (StrUtil.isNotBlank(lastSeenId)) {
          q.gt(SysUserMfa::getId, lastSeenId);
        }
        q.orderByAsc(SysUserMfa::getTenantId, SysUserMfa::getId);
        q.last("LIMIT " + actualBatch);
        List<SysUserMfa> batch = sysUserMfaService.list(q);
        if (batch == null || batch.isEmpty()) {
          break;
        }
        for (SysUserMfa row : batch) {
          if (row == null || StrUtil.isBlank(row.getId())) {
            continue;
          }
          lastSeenId = row.getId();
          try {
            if (StrUtil.isBlank(row.getSecretEnc())) {
              skipped.incrementAndGet();
              continue;
            }
            String plain;
            try {
              plain = AesEncryptUtil.desEncrypt(row.getSecretEnc());
            } catch (Exception legacyUnreadable) {
              // Possibly already migrated but SELECT raced? re-check prefix.
              if (StrUtil.startWith(row.getSecretEnc(), MfaSeedCryptoService.MFA_CIPHER_PREFIX)) {
                skipped.incrementAndGet();
                continue;
              }
              throw legacyUnreadable;
            }
            if (StrUtil.isBlank(plain)) {
              failed.incrementAndGet();
              log.warn("ReencryptMfaSeedJob empty plaintext mfaId={}", row.getId());
              continue;
            }
            String newWrapped = mfaSeedCryptoService.wrapSeed(plain, row.getUserId(), row.getTenantId());
            final String finalNewWrapped = newWrapped;
            final String finalId = row.getId();
            final Date now = new Date();
            final String updater =
                row.getUserId() == null ? "__MFA_REENCRYPT_JOB__" : row.getUserId();
            txTemplate.execute(
                status -> {
                  SysUserMfa update = new SysUserMfa();
                  update.setId(finalId);
                  update.setSecretEnc(finalNewWrapped);
                  update.setUpdateTime(now);
                  update.setUpdateBy(updater);
                  return sysUserMfaService.updateById(update);
                });
            migrated.incrementAndGet();
          } catch (Exception ex) {
            failed.incrementAndGet();
            log.warn("ReencryptMfaSeedJob fail mfaId={} err={}", row.getId(), ex.toString());
          }
          if (migrated.get() + failed.get() + skipped.get() >= SAFETY_STOP_AFTER_ROWS) {
            log.warn("ReencryptMfaSeedJob SAFETY_STOP after {} rows", migrated.get() + failed.get() + skipped.get());
            break;
          }
        }
        if (batch.size() < actualBatch) {
          break;
        }
      }
    } finally {
      log.info(
          "ReencryptMfaSeedJob DONE migrated={} failed={} skipped={} last_cursor_id={}",
          migrated.get(), failed.get(), skipped.get(), lastSeenId);
    }
  }
}
