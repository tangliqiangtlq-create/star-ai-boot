package org.jeecg.modules.system.vo.tenant;

import lombok.Data;

/**
 * User and department info
 * @Author taoYan
 * @Date 2023/2/17 10:10
 **/
@Data
public class UserDepart {

    /**
     * UserID
     */
    private String userId;

    /**
     * DepartmentName
     */
    private String departName;
}
