package org.jeecg.common.api.dto.message;

import lombok.Data;
import org.jeecg.common.constant.CommonConstant;

import java.io.Serializable;
import java.util.*;

/**
 * Normal message
 * @author: jeecg-boot
 */
@Data
public class MessageDTO implements Serializable {
    private static final long serialVersionUID = -5690444483968058442L;
    
    /**
     * Sender (user login account)
     */
    protected String fromUser;

    /**
     * Recipient (user login account)
     */
    protected String toUser;

    /**
     * Send to everyone
     */
    protected Boolean toAll;

    /**
     * Message subject
     */
    protected String title;

    /**
     * Message content
     */
    protected String content;

    /**
     * MessageType 1:Message  2:SystemMessage
     */
    protected String category;
    
    /**
     * Message type: org.jeecg.common.constant.enums.MessageTypeEnum
     *  XT("system",  "System message")
     *  YJ("email",  "Email message")
     *  DD("dingtalk", "DingTalk message")
     *  QYWX("wechat_enterprise", "Enterprise WeChat")
     */
    protected String type;
    

    //---[Push template related params]-------------------------------------------------------------
    /**
     * Whether to send a Markdown-formatted message
     */
    protected boolean isMarkdown;
    /**
     * Template code for template message
     */
    protected String templateCode;
    /**
     * Parse the data corresponding to Template content
     */
    protected Map<String, Object> data;
    //---[Push template related params]-------------------------------------------------------------

    //---[Email related params]-------------------------------------------------------------
    /**
     * cc to email
     */
    private String copyToUser;

    /**
     * Email Push Address
     */
    protected Set<String> toEmailList;

    /**
     * Email CC Address
     */
    protected Set<String> ccEmailList;
    //---[Email related params]-------------------------------------------------------------
    
    public MessageDTO(){
    }

    /**
     * Constructor 1 for system messages
     */
    public MessageDTO(String fromUser,String toUser,String title, String content){
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.title = title;
        this.content = content;
        //The default is 2System message
        this.category = CommonConstant.MSG_CATEGORY_2;
    }

    /**
     * Constructor 2 supports category: 1 message, 2 system message
     */
    public MessageDTO(String fromUser,String toUser,String title, String content, String category){
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.title = title;
        this.content = content;
        this.category = category;
    }

    public boolean isMarkdown() {
        return this.isMarkdown;
    }

    public void setIsMarkdown(boolean isMarkdown) {
        this.isMarkdown = isMarkdown;
    }
}
