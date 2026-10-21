package org.jeecg.modules.system.vo.lowapp;

import lombok.Data;
import org.jeecg.modules.system.vo.UserAvatar;

import java.io.Serializable;
import java.util.List;

/**
 * User or department info
 * Used for member/department search
 * @Author taoYan
 * @Date 2022/12/30 10:47
 **/
@Data
public class DepartAndUserInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    List<UserAvatar> userList;
    
    List<DepartInfo> departList;
    
}