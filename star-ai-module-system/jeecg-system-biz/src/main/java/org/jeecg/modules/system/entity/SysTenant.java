package org.jeecg.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.jeecg.common.aspect.annotation.Dict;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * TenantInfo
 * @author: jeecg-boot
 */
@Data
@TableName("sys_tenant")
public class SysTenant implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Code
     */
    private Integer id;
    
    /**
     * Name
     */
    private String name;
    

    /**
     * Created by
     */
    @Dict(dictTable ="sys_user",dicText = "realname",dicCode = "username")
    private String createBy;

    /**
     * Create time
     */
    @JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * Start time
     */
    @JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date beginDate;

    /**
     * End time
     */
    @JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date endDate;

    /**
     * Status 1 normal 0 frozen
     */
    @Dict(dicCode = "tenant_status")
    private Integer status;

    /**
     * Industry
     */
    @Dict(dicCode = "trade")
    private String trade;

    /**
     * Company size.
     */
    @Dict(dicCode = "company_size")
    private String companySize;

    /**
     * Company address.
     */
    private String companyAddress;

    /**
     * Company logo.
     */
    private String companyLogo;

    /**
     * house number
     */
    private String houseNumber;

    /**
     * Work location
     */
    private String workPlace;

    /**
     * Secondary domain (reserved, currently unused).
     */
    private String secondaryDomain;

    /**
     * Login background image (unused for now, reserved field)
     */
    private String loginBkgdImg;

    /**
     * Job level
     */
    @Dict(dicCode = "company_rank")
    private String position;

    /**
     * Department
     */
    @Dict(dicCode = "company_department")
    private String department;
    
    @TableLogic
    private Integer delFlag;

    /**Updater login name */
    private String updateBy;
    
    /**Update date */
    @JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * Allow admin application: 1 allow, 0 deny.
     */
    private Integer applyStatus;
    
}
