package org.jeecg.modules.system.vo;

import lombok.Data;

/**
* @Description: User-position entity
*
* @author: wangshuai
* @date: 2023/6/14 16:41
*/
@Data
public class SysUserPositionVo {
    
    /**Position id*/
    private String id;

    /**Position name*/
    private String name;
    
    /**Userid*/
    private String userId;
}
