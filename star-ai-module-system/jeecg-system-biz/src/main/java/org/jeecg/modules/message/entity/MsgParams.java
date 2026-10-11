package org.jeecg.modules.message.entity;

import java.io.Serializable;

import lombok.Data;

/**
 * Message send entity
 * @author: jeecg-boot
 */
@Data
public class MsgParams implements Serializable  {
	
	private static final long serialVersionUID = 1L;
	/**
     * MessageType
     */
	private String msgType;

    /**
     * Message recipient
     */
	private String receiver;

    /**
     * Message template code
     */
	private String templateCode;

    /**
     * Test data
     */
	private String testData;
	
}
