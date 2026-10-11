package org.jeecg.modules.system.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/** Ops manual create-user / master-agent request body */
@Data
public class OpsCreateUserRequest implements Serializable {
  private static final long serialVersionUID = 1L;

  /** user | agent1 */
  private String createType;

  private String username;
  private String email;
  private String password;
  private String realname;
  /** 1 individual / 2 enterprise */
  private String customerType;

  private String avatar;
  private String inviteCode;
  private String inviterId;

  /** Account type list; or comma-separated string (Controller parses) */
  private List<String> accountTypes;

  /** Default true: when accountTypes omitted, open all group accounts for the role */
  private Boolean openAllAccounts = true;
}
