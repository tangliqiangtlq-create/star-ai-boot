package org.jeecg.modules.system.entity;

import java.util.Date;

import org.jeecg.common.aspect.annotation.Dict;
import org.springframework.format.annotation.DateTimeFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * SystemLogTable
 * </p>
 *
 * @Author zhangweijian
 * @since 2018-12-26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SysLog implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/**
	 * Created by
	 */
	private String createBy;

	/**
	 * Create time
	 */
	@JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Date createTime;

	/**
	 * Updated by
	 */
	private String updateBy;

	/**
	 * Update time
	 */
	private Date updateTime;

	/**
	 * Elapsed
	 */
	private Long costTime;

	/**
	 * IP
	 */
	private String ip;

	/**
	 * Request parameters
	 */
	private String requestParam;

	/**
	 * Request type
	 */
	private String requestType;

	/**
	 * Request path
	 */
	private String requestUrl;
	/**
	 * Request method
	 */
	private String method;

	/**
	 * Operator display name
	 */
	private String username;
	/**
	 * Operator account
	 */
	private String userid;
	/**
	 * Operation detail log
	 */
	private String logContent;

	/**
	 * Log type (1 login log, 2 operation log)
	 */
	@Dict(dicCode = "log_type")
	private Integer logType;

	/**
	 * Operation type (1 query, 2 add, 3 update, 4 delete, 5 import, 6 export)
	 */
	@Dict(dicCode = "operate_type")
	private Integer operateType;
	
	/**
	 * Client terminal type: pc desktop, app mobile app, h5 mobile web
	 */
	@Dict(dicCode = "client_type")
	private String clientType;

	/**
	 * TenantID
	 */
	private Integer tenantId;

}
