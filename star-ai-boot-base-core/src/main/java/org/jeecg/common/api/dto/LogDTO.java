package org.jeecg.common.api.dto;
import lombok.Data;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecg.common.system.vo.LoginUser;
import java.io.Serializable;
import java.util.Date;

/**
 * logobject
 * DTO used by cloud API for interface transfer
 * @author: jeecg-boot
 */
@Data
public class LogDTO implements Serializable {

    private static final long serialVersionUID = 8482720462943906924L;

    /**Content*/
    private String logContent;

    /**logtype (0: operation log; 1: login log; 2: scheduled task)  */
    private Integer logType;

    /**Operation Type(1:Add;2:Modify;3:Delete;) */
    private Integer operateType;

    /**loginuser */
    private LoginUser loginUser;

    private String id;
    private String createBy;
    private Date createTime;
    private Long costTime;
    private String ip;

    /**Request parameters */
    private String requestParam;

    /**Request type*/
    private String requestType;

    /**Request path*/
    private String requestUrl;

    /**Request method */
    private String method;

    /**Operator user name*/
    private String username;

    /**Operator user account*/
    private String userid;

    /**
     * TenantID
     */
    private Integer tenantId;

    /**
     * Client terminal type: pc desktop, app mobile, h5 mobile web
     */
    private String clientType;
    
    public LogDTO(){

    }

    public LogDTO(String logContent, Integer logType, Integer operatetype){
        this.logContent = logContent;
        this.logType = logType;
        this.operateType = operatetype;
    }

    public LogDTO(String logContent, Integer logType, Integer operatetype, LoginUser loginUser){
        this.logContent = logContent;
        this.logType = logType;
        this.operateType = operatetype;
        this.loginUser = loginUser;
    }
}
