package org.jeecg.modules.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.message.entity.SysTemplateEditLog;

import java.util.List;

public interface SysTemplateEditLogMapper extends BaseMapper<SysTemplateEditLog> {

    List<SysTemplateEditLog> listByTemplateId(@Param("templateId") String templateId);
}
