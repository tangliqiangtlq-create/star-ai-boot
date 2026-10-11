package org.apache.shiro;

import cn.dev33.satoken.stp.StpUtil;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.SubjectImpl;
import org.jeecg.common.system.vo.LoginUser;

public class SecurityUtils {

  // GetCurrent SubjectImpl
  public static Subject getSubject() {

    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");

    return new SubjectImpl(loginUser);
  }
}
