package org.jeecg.modules.system.passwordreset.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.system.passwordreset.mapper.SysPasswordResetLinkMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Expired password-reset link cleanup job.
 *
 * <p>resolveFinalStatus only returns EXPIRED without writing DB; this Job at 03:00 batch-scans PENDING with expire_time<now
 * UPDATE links to EXPIRED in LIMIT 1000 batches to avoid long locks.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetExpireJob {

    private static final int BATCH_SIZE = 1000;
    private static final int MAX_LOOPS = 500;

    private final SysPasswordResetLinkMapper linkMapper;

    @Scheduled(cron = "0 0 3 * * ?")
    public void markExpired() {
        long t0 = System.currentTimeMillis();
        Date now = new Date();
        long total = 0L;
        int loops = 0;
        try {
            while (loops++ < MAX_LOOPS) {
                List<String> ids = linkMapper.selectPendingExpiredIdsBatch(now, BATCH_SIZE);
                if (ids == null || ids.isEmpty()) break;
                int updated = linkMapper.batchMarkExpiredByIds(ids);
                total += updated;
                log.info("[PasswordResetExpireJob] batch={}, updated={}, accumulated={}", ids.size(), updated, total);
                if (ids.size() < BATCH_SIZE) break;
            }
        } catch (Exception e) {
            log.error("[PasswordResetExpireJob] execution exception, total processed={}", total, e);
        } finally {
            long cost = System.currentTimeMillis() - t0;
            log.info("[PasswordResetExpireJob] done, totalExpired={}, costMs={}, loops={}", total, cost, loops - 1);
        }
    }
}
