package org.jeecg.modules.message.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.dto.message.MessageDTO;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.util.DateUtils;
import org.jeecg.modules.message.entity.SysMessage;
import org.jeecg.modules.message.handle.enums.SendMsgStatusEnum;
import org.jeecg.modules.message.service.ISysMessageService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * SendMessageJob
 * @author: jeecg-boot
 */

@Slf4j
public class SendMsgJob implements Job {

	@Autowired
	private ISysMessageService sysMessageService;

	@Autowired
	private ISysBaseAPI sysBaseAPI;

	@Override
	public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

		log.info(String.format(" Jeecg-Boot Send Message Job SendMsgJob !  Time:" + DateUtils.getTimestamp()));

		// 1. Read message center data; only unsent and failed-within-retry-limit
		QueryWrapper<SysMessage> queryWrapper = new QueryWrapper<SysMessage>();
		queryWrapper.eq("es_send_status", SendMsgStatusEnum.WAIT.getCode())
				.or(i -> i.eq("es_send_status", SendMsgStatusEnum.FAIL.getCode()).lt("es_send_num", 6));
		List<SysMessage> sysMessages = sysMessageService.list(queryWrapper);
		log.debug(String.valueOf(sysMessages));
		// 2. Route to different send implementations by type
		for (SysMessage sysMessage : sysMessages) {
			//update-begin-author:taoyan date:2022-7-8 for: template message send test invoke method change
			Integer sendNum = sysMessage.getEsSendNum();
			try {
				MessageDTO md = new MessageDTO();
				md.setTitle(sysMessage.getEsTitle());
				md.setContent(sysMessage.getEsContent());
				md.setToUser(sysMessage.getEsReceiver());
				md.setType(sysMessage.getEsType());
				md.setToAll(false);
				sysBaseAPI.sendTemplateMessage(md);
				//SendMessageSuccess
				sysMessage.setEsSendStatus(SendMsgStatusEnum.SUCCESS.getCode());
				//update-end-author:taoyan date:2022-7-8 for: template message send test invoke method change
			} catch (Exception e) {
				log.error("operation failed", e);
				// Exception while sending message
				sysMessage.setEsSendStatus(SendMsgStatusEnum.FAIL.getCode());
			}
			sysMessage.setEsSendNum(++sendNum);
			// Write send result back to database
			sysMessageService.updateById(sysMessage);
		}

	}

}
