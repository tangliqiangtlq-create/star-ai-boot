package org.jeecg.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Static parameter initialization
 * @author: jeecg-boot
 */
@Component
@Data
public class StaticConfig {

    @Value("${jeecg.oss.accessKey:}")
    private String accessKeyId;

    @Value("${jeecg.oss.secretKey:}")
    private String accessKeySecret;

    @Value(value = "${spring.mail.username:}")
    private String emailFrom;

    /** Optional; when not configured, use email address only as From */
    @Value(value = "${spring.mail.fromName:}")
    private String emailFromName;

    /** Optional; Reply-To omitted when not configured */
    @Value(value = "${spring.mail.replyTo:}")
    private String emailReplyTo;

//    /**
//     * Signature secret
//     */
//    @Value(value = "${jeecg.signatureSecret}")
//    private String signatureSecret;


    /*@Bean
    public void initStatic() {
       DySmsHelper.setAccessKeyId(accessKeyId);
       DySmsHelper.setAccessKeySecret(accessKeySecret);
       EmailSendMsgHandle.setEmailFrom(emailFrom);
    }*/

}
