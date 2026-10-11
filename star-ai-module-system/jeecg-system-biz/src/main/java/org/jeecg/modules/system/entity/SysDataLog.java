package org.jeecg.modules.system.entity;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import org.jeecg.common.system.vo.LoginUser;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: SystemDataLog
 * @author: jeecg-boot
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Slf4j
public class SysDataLog implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@TableId(type = IdType.ASSIGN_ID)
    /**
     * id
     */
	private String id;

    /**
     * Creator login name.
     */
	private String createBy;

    /**
     * Creator real name.
     */
	private String createName;

    /**
     * Creation date.
     */
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
	private Date createTime;

    /**
     * Updater login name
     */
	private String updateBy;
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")

    /**
     * Update date
     */
    private Date updateTime;

    /**
     * table name
     */
    private String dataTable;

    /**
     * DataID
     */
    private String dataId;

    /**
     * Data content
     */
    private String dataContent;

    /**
     * Version number
     */
    private String dataVersion;


    //update-begin-author:taoyan date:2022-7-26 for: Used to record logs in form comments to differentiate data
    /**
     * Type
     */
    private String type;
    //update-end-author:taoyan date:2022-7-26 for: Used for form comment recording logs to distinguish data

    /**
     * Set createName via loginUser
     */
    public void autoSetCreateName() {
        try {
            LoginUser sysUser =  (LoginUser) StpUtil.getSession().get("loginUser");
            this.setCreateName(sysUser.getRealname());
        } catch (Exception e) {
            log.warn("SecurityUtils.getSubject() GetUserInfoException：" + "[redacted]");
        }
    }

}
