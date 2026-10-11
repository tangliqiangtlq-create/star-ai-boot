package org.jeecg.modules.system.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Login form @Author scott
 *
 * @since 2019-01-18
 */
@ApiModel(value = "Login subject", description = "Login subject")
public class SysLoginModel {
  @ApiModelProperty(value = "Account")
  @NotBlank
  private String username;

  @ApiModelProperty(value = "Password")
  @NotBlank
  private String password;

  /** Ember: login captcha removed; field optional; still validated by LoginController if present */
  @ApiModelProperty(value = "Verification code (optional)")
  private String captcha;

  @ApiModelProperty(value = "Verification code key (optional)")
  private String checkKey;

  @ApiModelProperty(value = "RSA key cache key (optional; required if password is RSA-encrypted)")
  private String rsaCacheKey;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getCaptcha() {
    return captcha;
  }

  public void setCaptcha(String captcha) {
    this.captcha = captcha;
  }

  public String getCheckKey() {
    return checkKey;
  }

  public void setCheckKey(String checkKey) {
    this.checkKey = checkKey;
  }

  public String getRsaCacheKey() {
    return rsaCacheKey;
  }

  public void setRsaCacheKey(String rsaCacheKey) {
    this.rsaCacheKey = rsaCacheKey;
  }
}
