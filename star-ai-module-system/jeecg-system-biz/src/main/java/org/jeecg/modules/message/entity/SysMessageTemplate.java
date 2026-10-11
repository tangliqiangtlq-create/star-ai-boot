package org.jeecg.modules.message.entity;

import org.jeecg.common.system.base.entity.JeecgEntity;
import org.jeecgframework.poi.excel.annotation.Excel;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_sms_template")
public class SysMessageTemplate extends JeecgEntity{
	@Excel(name = "Template CODE", width = 15)
	private java.lang.String templateCode;
	@Excel(name = "Template title", width = 30)
	private java.lang.String templateName;
	@Excel(name = "Template content", width = 50)
	private java.lang.String templateContent;
	@Excel(name = "Template test json", width = 15)
	private java.lang.String templateTestJson;
	@Excel(name = "Template type", width = 15)
	private java.lang.String templateType;

	@Excel(name = "Application status", width = 15)
	private String useStatus;

	@Excel(name = "Approval status", width = 15)
	private java.lang.String status;

}
