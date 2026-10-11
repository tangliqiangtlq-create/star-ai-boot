package org.jeecg.modules.system.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import lombok.Data;

/**
 * @Description: Phone verification request
 * @author: jeecg-boot
 */
@Data
@ApiModel(value = "Mobile number verification request", description = "Used for frontend form async validation of duplicate phone numbers")
public class PhoneValidationRequest implements Serializable {

  private static final long serialVersionUID = 1L;

  /** User id (optional; defaults to current user to exclude self) */
  @ApiModelProperty(value = "User ID (optional; uses current logged-in user if empty)")
  private String userId;

  /** Phone area code */
  @ApiModelProperty(value = "Mobile country code", required = true)
  private String phoneCode;

  /** Mobile phone */
  @ApiModelProperty(value = "Mobile number", required = true)
  private String phone;
}
