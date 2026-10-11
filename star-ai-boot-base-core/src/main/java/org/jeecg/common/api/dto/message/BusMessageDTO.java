package org.jeecg.common.api.dto.message;

import lombok.Data;

import java.io.Serializable;

/**
 * message with business parameter
* 
* @author: taoyan
* @date: 2022/8/17
*/
@Data
public class BusMessageDTO extends MessageDTO implements Serializable {

    private static final long serialVersionUID = 9104793287983367669L;
    /**
     * BusinessType
     */
    private String busType;

    /**
     * Businessid
     */
    private String busId;

    public BusMessageDTO(){

    }

    /**
     * Build message with business parameters
     * @param fromUser
     * @param toUser
     * @param title
     * @param msgContent
     * @param msgCategory
     * @param busType
     * @param busId
     */
    public BusMessageDTO(String fromUser, String toUser, String title, String msgContent, String msgCategory, String busType, String busId){
        super(fromUser, toUser, title, msgContent, msgCategory);
        this.busId = busId;
        this.busType = busType;
    }
}
