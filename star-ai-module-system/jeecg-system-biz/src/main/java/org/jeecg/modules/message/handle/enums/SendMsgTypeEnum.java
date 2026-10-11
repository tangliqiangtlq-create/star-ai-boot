package org.jeecg.modules.message.handle.enums;

import org.jeecg.common.util.oConvertUtils;

/**
 * Message send type enum
 * @author: jeecg-boot
 */
public enum SendMsgTypeEnum {

    /**
     * SMS
     */
	SMS("1", "org.jeecg.modules.message.handle.impl.SmsSendMsgHandle"),
    /**
     * Email
     */
	EMAIL("2", "org.jeecg.modules.message.handle.impl.EmailSendMsgHandle"),
    /**
     * WeChat
     */
	WX("3","org.jeecg.modules.message.handle.impl.WxSendMsgHandle"),
    /**
     * SystemMessage
     */
	SYSTEM_MESSAGE("4","org.jeecg.modules.message.handle.impl.SystemSendMsgHandle");

	private String type;

	private String implClass;

	private SendMsgTypeEnum(String type, String implClass) {
		this.type = type;
		this.implClass = implClass;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getImplClass() {
		return implClass;
	}

	public void setImplClass(String implClass) {
		this.implClass = implClass;
	}

	public static SendMsgTypeEnum getByType(String type) {
		if (oConvertUtils.isEmpty(type)) {
			return null;
		}
		for (SendMsgTypeEnum val : values()) {
			if (val.getType().equals(type)) {
				return val;
			}
		}
		return null;
	}
}
