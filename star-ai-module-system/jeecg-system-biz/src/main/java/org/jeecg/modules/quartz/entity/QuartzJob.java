package org.jeecg.modules.quartz.entity;

import java.io.Serializable;

import org.jeecg.common.aspect.annotation.Dict;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * @Description: online scheduled task management
 * @Author: jeecg-boot
 * @Date:  2019-01-02
 * @Version: V1.0
 */
@Data
@TableName("sys_quartz_job")
public class QuartzJob implements Serializable {
    private static final long serialVersionUID = 1L;
    
	/**id*/
	@TableId(type = IdType.ASSIGN_ID)
	private java.lang.String id;
	/**Created by*/
	private java.lang.String createBy;
	/**Create time*/
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
	private java.util.Date createTime;
	/**DeleteStatus*/
	private java.lang.Integer delFlag;
	/**Updated by*/
	private java.lang.String updateBy;
	/**Update time*/
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
	private java.util.Date updateTime;
	/** Job class name */
	@Excel(name="Job handler class name",width=40)
	private java.lang.String jobClassName;
	/** Cron expression */
	@Excel(name="cron expression",width=30)
	private java.lang.String cronExpression;
	/**Parameter*/
	@Excel(name="Parameter",width=15)
	private java.lang.String parameter;
	/**Description*/
	@Excel(name="Description",width=40)
	private java.lang.String description;
	/** Status: 0 normal, -1 stopped */
	@Excel(name="Status",width=15,dicCode="quartz_status")
	@Dict(dicCode = "quartz_status")
	private java.lang.Integer status;

}
