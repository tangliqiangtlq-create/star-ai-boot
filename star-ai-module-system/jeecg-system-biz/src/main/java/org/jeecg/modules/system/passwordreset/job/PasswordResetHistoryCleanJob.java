package org.jeecg.modules.system.passwordreset.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.system.passwordreset.mapper.SysPasswordResetAccessLogMapper;
import org.jeecg.modules.system.passwordreset.mapper.SysPasswordResetLinkMapper;
import org.jeecg.modules.system.passwordreset.mapper.SysPasswordResetUsageLogMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;

/**
 * Password-reset history cleanup job.
 *
 * <p>Policy (overridable in Nacos; currently hardcoded conservative):
 * <ul>
 *   <li>access_log: retain 15 days (high-frequency; realtime audit only)</li>
 *   <li>usage_log: retain 30 days (submit results; aligns with investigation window)</li>
 *   <li>link (terminal USED/REVOKED/LOCKED/EXPIRED): retain 30 days</li>
 * </ul>
 * Each round LIMIT 5000, up to 1000 rounds (5M row cap) to avoid long TX.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetHistoryCleanJob {

    private static final int BATCH = 5000;
    private static final int MAX_LOOPS = 1000;
    private static final int ACCESS_LOG_KEEP_DAYS = 15;
    private static final int USAGE_LOG_KEEP_DAYS = 30;
    private static final int LINK_KEEP_DAYS = 30;

    private final SysPasswordResetLinkMapper linkMapper;
    private final SysPasswordResetAccessLogMapper accessLogMapper;
    private final SysPasswordResetUsageLogMapper usageLogMapper;

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanHistory() {
        long t0 = System.currentTimeMillis();
        long linkDel = 0L, accDel = 0L, useDel = 0L;

        try {
            Date linkBefore = daysAgo(LINK_KEEP_DAYS);
            int loops = 0;
            while (loops++ < MAX_LOOPS) {
                int n = linkMapper.deleteTerminalLinksBefore(linkBefore, BATCH);
                linkDel += n;
                if (n < BATCH) break;
            }
        } catch (Exception e) {
            log.error("[PasswordResetHistoryCleanJob] link cleanup exception, deleted={}", linkDel, e);
        }

        try {
            Date accBefore = daysAgo(ACCESS_LOG_KEEP_DAYS);
            int loops = 0;
            while (loops++ < MAX_LOOPS) {
                int n = accessLogMapper.deleteBefore(accBefore, BATCH);
                accDel += n;
                if (n < BATCH) break;
            }
        } catch (Exception e) {
            log.error("[PasswordResetHistoryCleanJob] access_log cleanup exception, deleted={}", accDel, e);
        }

        try {
            Date useBefore = daysAgo(USAGE_LOG_KEEP_DAYS);
            int loops = 0;
            while (loops++ < MAX_LOOPS) {
                int n = usageLogMapper.deleteBefore(useBefore, BATCH);
                useDel += n;
                if (n < BATCH) break;
            }
        } catch (Exception e) {
            log.error("[PasswordResetHistoryCleanJob] usage_log cleanup exception, deleted={}", useDel, e);
        }

        long cost = System.currentTimeMillis() - t0;
        log.info("[PasswordResetHistoryCleanJob] done, link={}, accessLog={}, usageLog={}, costMs={}",
                linkDel, accDel, useDel, cost);
    }

    private static Date daysAgo(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        return cal.getTime();
    }
}
