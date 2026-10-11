package org.jeecg.common.constant.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * @Description: SMS enumeration class
 * @author: jeecg-boot
 */
public enum DySmsEnum {

    /**Login SMS templatecoding*/
	LOGIN_TEMPLATE_CODE("SMS_175435174","Qiaoqiao Cloud","code"),
    /**forgetPasswordSMS templatecoding*/
	FORGET_PASSWORD_TEMPLATE_CODE("SMS_175435174","Qiaoqiao Cloud","code"),
	/**Change passwordSMS templatecoding*/
	CHANGE_PASSWORD_TEMPLATE_CODE("SMS_465391221","Qiaoqiao Cloud","code"),
	/**Register account SMS templatecoding*/
	REGISTER_TEMPLATE_CODE("SMS_175430166","Qiaoqiao Cloud","code"),
	/**Meeting notice*/
	MEET_NOTICE_TEMPLATE_CODE("SMS_201480469","JEECG","username,title,minute,time"),
	/**My plan notifications*/
	PLAN_NOTICE_TEMPLATE_CODE("SMS_201470515","JEECG","username,title,time"),
	/**SMS notification of successful payment*/
	PAY_SUCCESS_NOTICE_CODE("SMS_461735163","Qiaoqiao Cloud","realname,money,endTime"),
	/**Member expiry notification reminder*/
	VIP_EXPIRE_NOTICE_CODE("SMS_461885023","Qiaoqiao Cloud","realname,endTime");

	/**
	 * SMS template code
	 */
	private String templateCode;
	/**
	 * Signature
	 */
	private String signName;
	/**
	 * Required SMS template data keys, comma-separated; used for validation
	 */
	private String keys;
	
	private DySmsEnum(String templateCode,String signName,String keys) {
		this.templateCode = templateCode;
		this.signName = signName;
		this.keys = keys;
	}
	
	public String getTemplateCode() {
		return templateCode;
	}
	
	public void setTemplateCode(String templateCode) {
		this.templateCode = templateCode;
	}
	
	public String getSignName() {
		return signName;
	}
	
	public void setSignName(String signName) {
		this.signName = signName;
	}
	
	public String getKeys() {
		return keys;
	}

	public void setKeys(String keys) {
		this.keys = keys;
	}

	public static DySmsEnum toEnum(String templateCode) {
		if(StringUtils.isEmpty(templateCode)){
			return null;
		}
		for(DySmsEnum item : DySmsEnum.values()) {
			if(item.getTemplateCode().equals(templateCode)) {
				return item;
			}
		}
		return null;
	}
}

