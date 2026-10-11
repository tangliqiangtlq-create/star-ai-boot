package org.jeecg.modules.system.vo.tenant;

import lombok.Data;

/**
 * User and position info
 * @Author taoYan
 * @Date 2023/2/17 10:10
 **/
@Data
public class UserPosition {

    /**
     * UserID
     */
    private String userId;

    /**
     * Position name
     */
    private String positionName;
}
