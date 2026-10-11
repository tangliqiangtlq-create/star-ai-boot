package org.jeecg.modules.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.system.entity.SysDenyAudit;
import org.jeecg.modules.system.mapper.SysDenyAuditMapper;
import org.jeecg.modules.system.service.ISysDenyAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class SysDenyAuditServiceImpl extends ServiceImpl<SysDenyAuditMapper, SysDenyAudit> implements ISysDenyAuditService {

    private static final int BATCH_SIZE = 100;
    private static final long FLUSH_INTERVAL_MS = 500L;
    private static final int QUEUE_CAPACITY = 10000;

    private final LinkedBlockingQueue<SysDenyAudit> pendingQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private volatile ThreadPoolExecutor flushExecutor;
    private volatile boolean running = false;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private Counter denyCounterNotLogin;
    private Counter denyCounterNoPermission;
    private Counter denyCounterAccess;
    private Counter denyCounterOther;

    @Resource
    private SysDenyAuditMapper sysDenyAuditMapper;

    @PostConstruct
    void init() {
        ThreadFactory tf = new ThreadFactory() {
            final AtomicInteger n = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "deny-audit-flush-" + n.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        };
        flushExecutor = new ThreadPoolExecutor(
                1, 1, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(32), tf,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        running = true;
        flushExecutor.submit(this::flushLoop);

        if (meterRegistry != null) {
            denyCounterNotLogin = Counter.builder("mpay_deny_total")
                    .tag("reason", "H8_DENIED_NOTLOGIN")
                    .description("Total denied calls: NotLoginException")
                    .register(meterRegistry);
            denyCounterNoPermission = Counter.builder("mpay_deny_total")
                    .tag("reason", "H8_DENIED_NOPERMISSION")
                    .description("Total denied calls: NotPermissionException")
                    .register(meterRegistry);
            denyCounterAccess = Counter.builder("mpay_deny_total")
                    .tag("reason", "H8_DENIED_ACCESS")
                    .description("Total denied calls: AccessDeniedException")
                    .register(meterRegistry);
            denyCounterOther = Counter.builder("mpay_deny_total")
                    .tag("reason", "H8_DENIED_OTHER")
                    .description("Total denied calls: other reasons")
                    .register(meterRegistry);
        }
    }

    public void incrementMetric(String reasonCode) {
        if (meterRegistry == null) return;
        try {
            if ("H8_DENIED_NOTLOGIN".equals(reasonCode)) {
                if (denyCounterNotLogin != null) denyCounterNotLogin.increment();
            } else if ("H8_DENIED_NOPERMISSION".equals(reasonCode)) {
                if (denyCounterNoPermission != null) denyCounterNoPermission.increment();
            } else if ("H8_DENIED_ACCESS".equals(reasonCode)) {
                if (denyCounterAccess != null) denyCounterAccess.increment();
            } else {
                if (denyCounterOther != null) denyCounterOther.increment();
            }
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void asyncRecord(SysDenyAudit audit) {
        if (audit == null) return;
        if (audit.getCreateTime() == null) {
            audit.setCreateTime(new Date());
        }
        if (running && pendingQueue.offer(audit)) {
            return;
        }
        try {
            saveOrUpdate(audit);
            incrementMetric(audit.getReasonCode());
        } catch (Throwable t) {
            log.warn("Deny audit sync persist failed: reasonCode={}", audit.getReasonCode(), t);
        }
    }

    @Override
    public int purgeBefore(Date before, int limit) {
        if (before == null || limit <= 0) return 0;
        try {
            return sysDenyAuditMapper.deleteBatchBefore(before, limit);
        } catch (Throwable t) {
            log.warn("Deny audit purge failed", t);
            return 0;
        }
    }

    private void flushLoop() {
        while (running) {
            try {
                List<SysDenyAudit> batch = new ArrayList<>(BATCH_SIZE);
                SysDenyAudit first = pendingQueue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                batch.add(first);
                pendingQueue.drainTo(batch, BATCH_SIZE - 1);
                flushBatch(batch);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                log.warn("Deny audit flush loop error", t);
            }
        }
    }

    private void flushBatch(List<SysDenyAudit> batch) {
        if (batch == null || batch.isEmpty()) return;
        int ok = 0;
        for (SysDenyAudit a : batch) {
            try {
                saveOrUpdate(a);
                incrementMetric(a.getReasonCode());
                ok++;
            } catch (Throwable ignore) {
            }
        }
        if (ok < batch.size()) {
            log.warn("Deny audit batch flush: {} of {} persisted", ok, batch.size());
        }
    }
}
