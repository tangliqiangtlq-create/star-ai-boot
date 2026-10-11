package org.jeecg.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.system.entity.SysDenyAudit;

import java.util.Date;

public interface SysDenyAuditMapper extends BaseMapper<SysDenyAudit> {

    @Delete("DELETE FROM sys_deny_audit WHERE create_time < #{before} LIMIT #{limit}")
    int deleteBatchBefore(@Param("before") Date before, @Param("limit") int limit);
}
