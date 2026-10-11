package org.jeecg.modules.message.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.jeecg.common.annotation.RenderRequiresApproved;
import org.jeecg.common.exception.E5RejectException;
import org.jeecg.config.aspect.E5TemplateRenderGuardAspect;
import org.jeecg.modules.message.entity.SysMessage;
import org.jeecg.modules.message.entity.SysMessageTemplate;
import org.jeecg.modules.message.handle.enums.SendMsgStatusEnum;
import org.jeecg.modules.message.service.ISysMessageService;
import org.jeecg.modules.message.service.ISysMessageTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Component
public class PushMsgUtil {
  private static final Logger log = LoggerFactory.getLogger(PushMsgUtil.class);


    @Autowired
    private ISysMessageService sysMessageService;

    @Autowired
    private ISysMessageTemplateService sysMessageTemplateService;

    @Autowired
    @Qualifier("sharedFreemarkerConfiguration")
    private Configuration freemarkerConfig;

    @RenderRequiresApproved
    public boolean sendMessage(String msgType, String templateCode, Map<String, String> map, String sentTo) {
        List<SysMessageTemplate> sysSmsTemplates = sysMessageTemplateService.selectByCode(templateCode);
        SysMessage sysMessage = new SysMessage();
        if (sysSmsTemplates.size() > 0) {
            SysMessageTemplate sysSmsTemplate = sysSmsTemplates.get(0);
            E5TemplateRenderGuardAspect.checkTemplateContentStatus(sysSmsTemplate);
            sysMessage.setEsType(msgType);
            sysMessage.setEsReceiver(sentTo);
            String title = sysSmsTemplate.getTemplateName();
            String content = sysSmsTemplate.getTemplateContent();
            StringWriter stringWriter = new StringWriter();
            Template template = null;
            try {
                template = new Template("SysMessageTemplate", content, freemarkerConfig);
                template.process(map, stringWriter);
            } catch (IOException e) {
                log.error("operation failed", e);
                return false;
            } catch (TemplateException e) {
                log.error("operation failed", e);
                return false;
            }
            content = stringWriter.toString();
            sysMessage.setEsTitle(title);
            sysMessage.setEsContent(content);
            sysMessage.setEsParam(JSONObject.toJSONString(map));
            sysMessage.setEsSendTime(new Date());
            sysMessage.setEsSendStatus(SendMsgStatusEnum.WAIT.getCode());
            sysMessage.setEsSendNum(0);
            if(sysMessageService.save(sysMessage)) {
				return true;
			}
        }
        return false;
    }

}
