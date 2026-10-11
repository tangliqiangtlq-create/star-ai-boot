package org.jeecg.config.vo;

import lombok.Data;

/** Nacos: jeecg.password-reset.* */
@Data
public class PasswordReset {

  /** Grayscale switch; Facade throws NOT_PERMITTED when false*/
  private boolean enabled = true;

  /** Portal Origin, such as https://ramp.mighty.africa*/
  private String portalOrigin = "";

  /** resetUrl path prefix, default /password-reset/*/
  private String resetPathPrefix = "/password-reset/";

  /** The maximum number of PENDING links for a single account at the same time*/
  private int maxPendingPerUser = 3;
}
