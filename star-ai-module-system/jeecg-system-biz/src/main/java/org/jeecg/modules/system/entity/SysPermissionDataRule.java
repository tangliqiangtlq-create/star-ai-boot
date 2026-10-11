package org.jeecg.modules.system.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * Menu permissions rule table
 * </p>
 *
 * @Author huangzhilin
 * @since 2019-03-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SysPermissionDataRule implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	/**
	 * id
	 */
	@TableId(type = IdType.ASSIGN_ID)
	private String id;
	
	/**
	 * Corresponding menu id
	 */
	private String permissionId;
	
	/**
	 * Rule name
	 */
	private String ruleName;
	
	/**
	 * Field
	 */
	private String ruleColumn;
	
	/**
	 * Condition
	 */
	private String ruleConditions;
	
	/**
	 * Rule value
	 */
	private String ruleValue;
	
	/**
	 * Status: 1 valid, 0 invalid
	 */
	private String status;
	
	/**
	 * Create time
	 */
	private Date createTime;
	
	/**
	 * Created by
	 */
	private String createBy;
	
	/**
	 * Update time
	 */
	private Date updateTime;
	
	/**
	 * Updated by
	 */
	private String updateBy;
}
