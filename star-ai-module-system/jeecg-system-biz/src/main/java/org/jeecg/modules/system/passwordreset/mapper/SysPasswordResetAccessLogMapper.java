package org.jeecg.modules.system.passwordreset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.system.passwordreset.entity.SysPasswordResetAccessLog;

import java.util.Date;

public interface SysPasswordResetAccessLogMapper extends BaseMapper<SysPasswordResetAccessLog> {

    /** Clean access logs older than 15 days in LIMIT batches */
    @Delete("DELETE FROM sys_password_reset_access_log WHERE create_time < #{before} LIMIT #{limit}")
    int deleteBefore(@Param("before") Date before, @Param("limit") int limit);
}
