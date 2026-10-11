package org.jeecg.common.api.dto.message;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

/**
 * templatemessage
 * @author: jeecg-boot
 */
@Data
public class TemplateMessageDTO extends TemplateDTO implements Serializable {

    private static final long serialVersionUID = 411137565170647585L;


    /**
     * Sender (user login account)
     */
    protected String fromUser;

    /**
     * Recipient (user login account)
     */
    protected String toUser;

    /**
     * Message subject
     */
    protected String title;


    public TemplateMessageDTO(){

    }

    /**
     * Constructor 1 for template messages
     */
    public TemplateMessageDTO(String fromUser, String toUser,String title, Map<String, String> templateParam, String templateCode){
        super(templateCode, templateParam);
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.title = title;
    }



}
