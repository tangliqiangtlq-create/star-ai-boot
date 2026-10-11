package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.system.entity.SysDenyAudit;

public interface ISysDenyAuditService extends IService<SysDenyAudit> {

    void asyncRecord(SysDenyAudit audit);

    int purgeBefore(java.util.Date before, int limit);
}
