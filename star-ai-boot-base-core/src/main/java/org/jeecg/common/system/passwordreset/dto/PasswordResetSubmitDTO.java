package org.jeecg.common.system.passwordreset.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;

@Data
@ApiModel(description = "Submit password reset request")
public class PasswordResetSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "token is required")
    @Size(max = 64, message = "token is too long")
    @ApiModelProperty(value = "Link token", required = true)
    private String token;

    @NotBlank(message = "challenge is required")
    @Size(max = 512, message = "challenge is too long")
    @ApiModelProperty(value = "One-time challenge returned by meta", required = true)
    private String challenge;

    @NotBlank(message = "RSA cacheKey is required")
    @Size(max = 128, message = "rsaCacheKey is too long")
    @ApiModelProperty(value = "cacheKey returned by generateRsaKeyPair", required = true)
    private String rsaCacheKey;

    @NotBlank(message = "encryptedPassword1 is required")
    @Size(max = 4096, message = "encryptedPassword1 is too long")
    @ApiModelProperty(value = "New password Base64 after public-key encryption", required = true)
    private String encryptedPassword1;

    @NotBlank(message = "encryptedPassword2 is required")
    @Size(max = 4096, message = "encryptedPassword2 is too long")
    @ApiModelProperty(value = "Confirm password Base64 after public-key encryption", required = true)
    private String encryptedPassword2;

    @Size(max = 16, message = "captcha is too long")
    @ApiModelProperty(value = "Captcha (required when needCaptcha=true)")
    private String captcha;

    @Size(max = 64, message = "captchaKey is too long")
    @ApiModelProperty(value = "Captcha key")
    private String captchaKey;
}
