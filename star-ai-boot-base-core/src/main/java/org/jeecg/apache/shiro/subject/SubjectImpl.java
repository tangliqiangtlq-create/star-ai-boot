package org.apache.shiro.subject;

import org.jeecg.common.system.vo.LoginUser;

// SubjectImpl class containing Principal (logged-in user info)
public class SubjectImpl implements Subject {
  private Object principal;

  public SubjectImpl(LoginUser principal) {
    this.principal = principal;
  }

  public Object getPrincipal() {
    return principal;
  }
}
