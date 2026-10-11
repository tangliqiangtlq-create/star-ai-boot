package org.jeecg.common.system.passwordreset.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class PasswordResetMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String status;

    private String expireTime;

    private Integer remainingAttempts;

    private Boolean needCaptcha;

    /** One-time challenge (2min TTL, and token+clientIp binding), submit must carry */
    private String challenge;

    private PasswordPolicyVO passwordPolicy;

    @Data
    @Accessors(chain = true)
    public static class PasswordPolicyVO implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer minLength;
        private Integer maxLength;
        private Boolean requireUppercase;
        private Boolean requireLowercase;
        private Boolean requireNumber;
        private Boolean requireSymbol;
        private Boolean disallowUsernameSubstring;
    }
}
