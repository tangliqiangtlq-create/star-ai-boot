package org.jeecg.modules.system.vo.thirdapp;

import lombok.Data;

import java.util.List;

/**
 * WeCom implementation class
 */
@Data
public class JwSysUserDepartVo {

    /**
     * WeChat Work to user mapping class.
     */
    private List<JwUserDepartVo> jwUserDepartVos;

    /**
     * UserList
     */
    private List<JwUserDepartVo> userList;

}
