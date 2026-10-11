package org.jeecg.common.system.passwordreset.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class PasswordResetLinkVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;

    private String resetUrl;

    private LocalDateTime expireTime;

    private Integer ttlMinutes;
}
