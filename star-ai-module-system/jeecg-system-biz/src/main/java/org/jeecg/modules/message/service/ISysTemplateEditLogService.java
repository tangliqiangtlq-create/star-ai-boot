package org.jeecg.modules.message.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.message.entity.SysTemplateEditLog;

public interface ISysTemplateEditLogService extends IService<SysTemplateEditLog> {

    Result<SysTemplateEditLog> approve(String id, String approverUserId, String approverUsername, String remark);

    Result<SysTemplateEditLog> reject(String id, String approverUserId, String approverUsername, String rejectReason);
}
