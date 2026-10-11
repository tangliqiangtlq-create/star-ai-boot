package org.jeecg.modules.system.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@Profile("prod")
public class D4MfaLegacyCutoffJob {

    @Resource
    private SysUserMapper sysUserMapper;

    @XxlJob("D4MfaLegacyCutoffJob")
    public void execute() {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<>();
        q.eq(SysUser::getMfaMasterVersion, 0)
         .lt(SysUser::getMfaLegacyCutoffAt, new Date())
         .last("LIMIT 500");
        List<SysUser> rows = sysUserMapper.selectList(q);
        if (rows != null && !rows.isEmpty()) {
            log.warn("D4MfaLegacyCutoffJob: found {} legacy MFA rows past cutoff_at; user must reset MFA.", rows.size());
            for (SysUser u : rows) {
                log.warn("D4 CUTOFF WARN: userId={} username={} legacy MFA cutoff_at={} master_version=0; force reset MFA flow.",
                    u.getId(), u.getUsername(), u.getMfaLegacyCutoffAt());
            }
        } else {
            log.info("D4MfaLegacyCutoffJob: no legacy MFA rows past cutoff_at. OK.");
        }
    }
}
