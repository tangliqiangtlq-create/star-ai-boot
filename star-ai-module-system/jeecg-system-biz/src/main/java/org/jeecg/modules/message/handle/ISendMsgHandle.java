package org.jeecg.modules.message.handle;

import org.jeecg.common.api.dto.message.MessageDTO;

/**
 * @Description: SendInfoAPI
 * @author: jeecg-boot
 */
public interface ISendMsgHandle {

    /**
     * Send a message
     * @param esReceiver recipient
     * @param esTitle title
     * @param esContent content
     */
	void sendMsg(String esReceiver, String esTitle, String esContent);

    /**
     * SendInfo
     * @param messageDTO
     */
	default void sendMessage(MessageDTO messageDTO){

    }
}
