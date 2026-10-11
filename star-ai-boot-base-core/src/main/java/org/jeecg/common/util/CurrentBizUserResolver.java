package org.jeecg.common.util;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import org.jeecg.common.system.vo.LoginUser;
import org.springframework.stereotype.Component;

/**
 * Unified analysis of the current login subject and the business attribution subject prevents business codes from being scattered and judged by dlygx.
 */
@Component
public class CurrentBizUserResolver {

  public static final String ROLE_TEAM_OPERATOR = "TEAM_OPERATOR";
  public static final String ROLE_TEAM_VIEWER = "TEAM_VIEWER";
  public static final String ROLE_MACKER = "MACKER";
  public static final String ROLE_CHECKER = "CHECKER";

  private static final String SESSION_LOGIN_USER = "loginUser";

  public String getLoginUserId() {
    return StpUtil.getLoginIdAsString();
  }

  public String getOperatorUserId() {
    return getLoginUserId();
  }

  public LoginUser getLoginUser() {
    Object sessionUser = StpUtil.getSession().get(SESSION_LOGIN_USER);
    if (sessionUser instanceof LoginUser) {
      return (LoginUser) sessionUser;
    }
    return null;
  }

  /**
   * Get the current business user ID: team member/acting role returns the main account ID, otherwise returns the current login user ID.
   */
  public String getBizOwnerUserId() {
    LoginUser loginUser = getLoginUser();
    if (loginUser == null) {
      return getLoginUserId();
    }
    if (isDelegatedOperator() && StrUtil.isNotBlank(loginUser.getDlygx())) {
      return loginUser.getDlygx();
    }
    return loginUser.getId();
  }

  /**
   * Resolve the business-owned user ID (Team Member → Master Account dlygx) from any {@link LoginUser} snapshot.
   *
   * <p>It is used in scenarios such as lock ownership check and does not depend on the current Sa-Token Session.
   */
  public static String resolveBizOwnerUserId(LoginUser loginUser) {
    if (loginUser == null || StrUtil.isBlank(loginUser.getId())) {
      return loginUser == null ? "" : StrUtil.blankToDefault(loginUser.getId(), "");
    }
    if (StrUtil.isNotBlank(loginUser.getBizOwnerUserId())) {
      return loginUser.getBizOwnerUserId();
    }
    if (Boolean.TRUE.equals(loginUser.getTeamMember()) && StrUtil.isNotBlank(loginUser.getDlygx())) {
      return loginUser.getDlygx();
    }
    return loginUser.getId();
  }

  /** Global static entry, equivalent to {@link #getBizOwnerUserId()}. */
  public static String getCurrUserId() {
    return SpringContextUtils.getBean(CurrentBizUserResolver.class).getBizOwnerUserId();
  }

  /**
   * The userId in the query parameter may be the team member ID. The business data belongs to the main account and needs to be normalized to the main account ID.
   *
   * @param userId User ID to be queried
   * @param dlygx corresponds to the user's dlygx (non-empty indicates team members)
   */
  public static String resolveBizOwnerUserId(String userId, String dlygx) {
    if (StrUtil.isBlank(userId)) {
      return userId;
    }
    return StrUtil.isNotBlank(dlygx) ? dlygx : userId;
  }

  public boolean isTeamMember() {
    LoginUser loginUser = getLoginUser();
    return loginUser != null
        && StrUtil.isNotBlank(loginUser.getDlygx())
        && Boolean.TRUE.equals(loginUser.getTeamMember());
  }

  public boolean isDelegatedOperator() {
    LoginUser loginUser = getLoginUser();
    return (loginUser != null && Boolean.TRUE.equals(loginUser.getTeamMember()))
        || StpUtil.hasRoleOr(
            ROLE_TEAM_OPERATOR, ROLE_TEAM_VIEWER, ROLE_MACKER, ROLE_CHECKER);
  }
}
