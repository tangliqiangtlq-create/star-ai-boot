package org.jeecg.modules.system.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * RoleTable
 * </p>
 *
 * @Author scott
 * @since 2018-12-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SysRole implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * RoleName
     */
    @Excel(name="Role name",width=15)
    private String roleName;
    
    /**
     * RoleCode
     */
    @Excel(name="RoleCode",width=15)
    private String roleCode;
    
    /**
          * Description
     */
    @Excel(name="Description",width=60)
    private String description;

    /**
     * Created by
     */
    private String createBy;

    /**
     * Create time
     */
    @JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * Updated by
     */
    private String updateBy;

    /**
     * Update time
     */
    @JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**TenantID*/
    private java.lang.Integer tenantId;
}
