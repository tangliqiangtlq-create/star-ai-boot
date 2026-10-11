package org.jeecg.common.util;

import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import org.jeecg.common.constant.enums.DySmsEnum;
import org.jeecg.config.StaticConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created on 17/6/7.
 * The DEMO program of the Short message API product. The project contains a SmsDemo class, which can be directly passed
 * Execute the main function to experience the SMS product API function (just replace the AK with the AK that has enabled the cloud communication-SMS product function)
 * The project depends on 2 jar packages (stored in the libs directory of the project)
 * 1:aliyun-java-sdk-core.jar
 * 2:aliyun-java-sdk-dysmsapi.jar
 *
 * Remark: Demo project coding adopts UTF-8
 * Please do not refer to this DEMO when sending international text messages.
 * @author: jeecg-boot
 */
// ===== CLASS-LEVEL HARD RULE: NEVER log templateParam / captcha / verificationCode / sms code payload =====
// CI GATE (§7 P2-11): rg CODE-only log statements touching OTP keys must equal 0
public class DySmsHelper {
	
	private final static Logger logger=LoggerFactory.getLogger(DySmsHelper.class);

    /**Product name: Cloud Communication SMS API product, developers do not need to replace it*/
    static final String PRODUCT = "Dysmsapi";
    /**Product domain name, developers do not need to replace it*/
    static final String DOMAIN = "dysmsapi.aliyuncs.com";

    /**TODO needs to be replaced with the developer's own AK (find it in the Alibaba Cloud access console)*/
    static  String accessKeyId;
    static  String accessKeySecret;

    public static void setAccessKeyId(String accessKeyId) {
        DySmsHelper.accessKeyId = accessKeyId;
    }

    public static void setAccessKeySecret(String accessKeySecret) {
        DySmsHelper.accessKeySecret = accessKeySecret;
    }

    public static String getAccessKeyId() {
        return accessKeyId;
    }

    public static String getAccessKeySecret() {
        return accessKeySecret;
    }
    
    
    public static boolean sendSms(String phone, JSONObject templateParamJson, DySmsEnum dySmsEnum) throws ClientException {
    	//Self-adjustable timeout
        System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
        System.setProperty("sun.net.client.defaultReadTimeout", "10000");

        //update-begin-author:taoyan date:20200811 for: load config-class data
        StaticConfig staticConfig = SpringContextUtils.getBean(StaticConfig.class);
        //logger.info("Alibaba Dayu SMS secret key accessKeyId:" + staticConfig.getAccessKeyId());
        //logger.info("Alibaba Dayu SMS secret key accessKeySecret:"+ staticConfig.getAccessKeySecret());
        setAccessKeyId(staticConfig.getAccessKeyId());
        setAccessKeySecret(staticConfig.getAccessKeySecret());
        //update-end-author:taoyan date:20200811 for: load config-class data
        
        //Initialize acsClient, regionization is not supported yet
        IClientProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        DefaultProfile.addEndpoint("cn-hangzhou", "cn-hangzhou", PRODUCT, DOMAIN);
        IAcsClient acsClient = new DefaultAcsClient(profile);
        
        //Verify jsonParameter
        validateParam(templateParamJson,dySmsEnum);
        
        //Assemble the request object - for detailed description, see the console - document section Content
        SendSmsRequest request = new SendSmsRequest();
        //Required: Mobile phone number to be sent
        request.setPhoneNumbers(phone);
        //Required: SMS signature - can be found in the SMS console
        request.setSignName(dySmsEnum.getSignName());
        //Required: SMS template - found in the SMS console
        request.setTemplateCode(dySmsEnum.getTemplateCode());
        //Optional: Replace the JSON string with the variables in the template. For example, the Template content is "Dear ${name}, your Verification code is ${code}" hour, and the value here is
        request.setTemplateParam(templateParamJson.toJSONString());
        
        //Optional - Uplink SMS extension code (users without special needs please ignore this field)
        //request.setSmsUpExtendCode("90997");

        //Optional: outId is an extended Field provided to the business party, and this value is eventually returned to the caller in the SMS receipt message.
        //request.setOutId("yourOutId");

        boolean result = false;

        //hint An exception may be thrown here, please notecatch
        SendSmsResponse sendSmsResponse = acsClient.getAcsResponse(request);
        logger.info("SMS API response data----------------");
        logger.info("{Code:" + sendSmsResponse.getCode()+",Message:" + sendSmsResponse.getMessage()+",RequestId:"+ sendSmsResponse.getRequestId()+",BizId:"+sendSmsResponse.getBizId()+"}");
        String ok = "OK";
        if (ok.equals(sendSmsResponse.getCode())) {
            result = true;
        }
        return result;
        
    }
    
    private static void validateParam(JSONObject templateParamJson,DySmsEnum dySmsEnum) {
    	String keys = dySmsEnum.getKeys();
    	String [] keyArr = keys.split(",");
    	for(String item :keyArr) {
    		if(!templateParamJson.containsKey(item)) {
    			throw new RuntimeException("Template missing parameter: "+item);
    		}
    	}
    }
    

//    public static void main(String[] args) throws ClientException, InterruptedException {
//    	JSONObject obj = new JSONObject();
//    	obj.put("code", "1234");
//    	sendSms("13800138000", obj, DySmsEnum.FORGET_PASSWORD_TEMPLATE_CODE);
//    }
}
