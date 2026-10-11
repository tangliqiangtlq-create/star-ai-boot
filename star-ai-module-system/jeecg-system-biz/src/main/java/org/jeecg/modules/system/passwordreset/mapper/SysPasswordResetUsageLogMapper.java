package org.jeecg.modules.system.passwordreset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.system.passwordreset.entity.SysPasswordResetUsageLog;

import java.util.Date;

public interface SysPasswordResetUsageLogMapper extends BaseMapper<SysPasswordResetUsageLog> {

    /** Clean usage logs older than 30 days in LIMIT batches */
    @Delete("DELETE FROM sys_password_reset_usage_log WHERE create_time < #{before} LIMIT #{limit}")
    int deleteBefore(@Param("before") Date before, @Param("limit") int limit);
}
