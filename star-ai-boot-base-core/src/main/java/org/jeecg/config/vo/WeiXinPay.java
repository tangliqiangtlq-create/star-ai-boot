package org.jeecg.config.vo;

import lombok.Data;

@Data
public class WeiXinPay {
    /**
     * WeChat official account id
     */
    private String appId;
    /**
     * Merchant ID
     */
    private String mchId;
    /**
     * Merchant secret key
     */
    private String apiKey;
    /**
     * Callback URL
     */
    private String notifyUrl;
    /**
     * Whether member authentication is enabled
     */
    private Boolean openVipLimit;
    /**
     * Certificate path
     */
    private String certPath;
}
