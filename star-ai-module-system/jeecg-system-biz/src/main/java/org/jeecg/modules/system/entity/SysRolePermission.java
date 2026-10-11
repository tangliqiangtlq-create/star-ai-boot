package org.jeecg.modules.system.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * <p>
 * RolePermissionTable
 * </p>
 *
 * @Author scott
 * @since 2018-12-21
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SysRolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * Roleid
     */
    private String roleId;

    /**
     * Permissionid
     */
    private String permissionId;
    
    /**
     * DataPermission
     */
    private String dataRuleIds;

    /**
     * Operation time
     */
    @JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date operateDate;

    /**
     * Operation IP
     */
    private String operateIp;

    public SysRolePermission() {
   	}
       
   	public SysRolePermission(String roleId, String permissionId) {
   		this.roleId = roleId;
   		this.permissionId = permissionId;
   	}

}
