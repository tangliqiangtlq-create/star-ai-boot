package org.jeecg.modules.system.vo.thirdapp;

import lombok.Data;

/**
* @Description: WeCom user sync utility
*
* @author: wangshuai
* @date: 2023/11/28 18:17
*/
@Data
public class JwUserDepartVo {
    
    /**
     * Userid
     */
    private String userId;

    /**
     * User avatar
     */
    private String avatar;

    /**
     * Real name
     */
    private String realName;

    /**
     * WeChat Work display name.
     */
    private String wechatRealName;

    /**
     * WeChat Work department.
     */
    private String wechatDepartId;

    /**
     * WeChat Work user ID.
     */
    private String wechatUserId;

    /**
     * Third-party id
     */
    private String thirdId;
}
