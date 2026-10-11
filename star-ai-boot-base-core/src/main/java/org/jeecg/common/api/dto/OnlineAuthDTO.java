package org.jeecg.common.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * online blocker permission judgment
 * DTO used by cloud API for interface transfer
 * @author: jeecg-boot
 */
@Data
public class OnlineAuthDTO implements Serializable {
    private static final long serialVersionUID = 1771827545416418203L;


    /**
     * Username
     */
    private String username;

    /**
     * Possible request URLs
     */
    private List<String> possibleUrl;

    /**
     * Online development menu URL.
     */
    private String onlineFormUrl;

    //update-begin---author:chenrui ---date:20240123  for: [QQYUN-7992][online] The online form under the work order application is not configured with the online form development menu, and the operation error is reported without permission------------
    /**
     * Online work order URL.
     */
    private String onlineWorkOrderUrl;
    //update-end---author:chenrui ---date:20240123  for: [QQYUN-7992][online] The online form under the work order application is not configured with the online form development menu, and the operation error is reported without permission------------

    public OnlineAuthDTO(){

    }

    public OnlineAuthDTO(String username, List<String> possibleUrl, String onlineFormUrl){
        this.username = username;
        this.possibleUrl = possibleUrl;
        this.onlineFormUrl = onlineFormUrl;
    }
}
