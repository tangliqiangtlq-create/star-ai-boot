package org.jeecg.modules.message.service;

import java.util.List;

import org.jeecg.common.system.base.service.JeecgService;
import org.jeecg.modules.message.entity.SysMessageTemplate;

/**
 * @Description: message template
 * @Author: jeecg-boot
 * @Date:  2019-04-09
 * @Version: V1.0
 */
public interface ISysMessageTemplateService extends JeecgService<SysMessageTemplate> {

    /**
     * Query message template by template CODE
     * @param code template CODE
     * @return
     */
    List<SysMessageTemplate> selectByCode(String code);
}
