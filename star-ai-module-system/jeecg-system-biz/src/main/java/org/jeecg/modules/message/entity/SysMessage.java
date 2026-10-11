package org.jeecg.modules.message.entity;

import org.jeecg.common.aspect.annotation.Dict;
import org.jeecg.common.system.base.entity.JeecgEntity;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: Message
 * @Author: jeecg-boot
 * @Date:  2019-04-09
 * @Version: V1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_sms")
public class SysMessage extends JeecgEntity {
	/** Push content */
	@Excel(name = "Push content", width = 15)
	private java.lang.String esContent;
	/** Push parameters in JSON format */
	@Excel(name = "Required push parameters in JSON format", width = 15)
	private java.lang.String esParam;
	/** Recipient */
	@Excel(name = "Recipient", width = 15)
	private java.lang.String esReceiver;
	/** Push failure reason */
	@Excel(name = "Push failure reason", width = 15)
	private java.lang.String esResult;
	/** Send count */
	@Excel(name = "Send count", width = 15)
	private java.lang.Integer esSendNum;
	/** Push status: 0 not pushed, 1 success, 2 failed */
	@Excel(name = "Push status 0=not pushed 1=push succeeded 2=push failed", width = 15)
	@Dict(dicCode = "msgSendStatus")
	private java.lang.String esSendStatus;
	/** Push time */
	@Excel(name = "Push time", width = 20, format = "yyyy-MM-dd HH:mm:ss")
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
	private java.util.Date esSendTime;
	/** Message title */
	@Excel(name = "Message title", width = 15)
	private java.lang.String esTitle;
	/**
	 * Push method: see MessageTypeEnum
	 */
	@Excel(name = "Push method", width = 15)
	@Dict(dicCode = "messageType")
	private java.lang.String esType;
	/**Remark*/
	@Excel(name = "Remark", width = 15)
	private java.lang.String remark;
}
