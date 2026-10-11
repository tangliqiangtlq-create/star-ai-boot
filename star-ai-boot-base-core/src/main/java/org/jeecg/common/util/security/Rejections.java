package org.jeecg.common.util.security;

import cn.dev33.satoken.stp.StpUtil;
import org.jeecg.common.exception.JeecgBootException;

/**
 * 拒绝码统一标准 (rejection-code standard) factory + helper.
 *
 * <p>A rejection thrown from a guard / service must carry an explicit code so that {@link
 * org.jeecg.common.exception.JeecgBootExceptionHandler} mirrors it onto the matching HTTP status
 * (400/401/403/404/429) instead of falling back to the legacy {@code HTTP 200 + business 500}
 * shape.
 *
 * <p>Invariants (see {@code docs/安全问题修复/42-拒绝码统一标准-CodePlan-v1.0.md}):
 *
 * <ul>
 *   <li>HTTP status must equal the JSON {@code code} for every rejection.
 *   <li>Rejection semantics only — never widen/narrow the underlying guard decision.
 * </ul>
 *
 * <p>This does NOT map真正的服务端错误 (NPE/DB/IO): those keep {@code code=500} and the legacy HTTP
 * 200 contract (decision D1 = A).
 */
public final class Rejections {

  /** Not authenticated / session expired. */
  public static final int UNAUTHORIZED = 401;

  /** Authenticated but not allowed (permission / role / ownership / data scope). */
  public static final int FORBIDDEN = 403;

  /** Invalid or missing parameters. */
  public static final int BAD_REQUEST = 400;

  /** Requested resource does not exist (or is hidden to avoid enumeration). */
  public static final int NOT_FOUND = 404;

  /** Rate limited. */
  public static final int TOO_MANY_REQUESTS = 429;

  private Rejections() {}

  public static JeecgBootException unauthorized(String msg) {
    return new JeecgBootException(msg, UNAUTHORIZED);
  }

  public static JeecgBootException forbidden(String msg) {
    return new JeecgBootException(msg, FORBIDDEN);
  }

  public static JeecgBootException badRequest(String msg) {
    return new JeecgBootException(msg, BAD_REQUEST);
  }

  public static JeecgBootException notFound(String msg) {
    return new JeecgBootException(msg, NOT_FOUND);
  }

  public static JeecgBootException tooManyRequests(String msg) {
    return new JeecgBootException(msg, TOO_MANY_REQUESTS);
  }

  /**
   * Access denied where the caller may or may not be authenticated: anonymous callers get 401
   * (please sign in), authenticated callers get 403 (no permission). Use this from guards that
   * simply observe "the check failed" and cannot tell the two cases apart.
   */
  public static JeecgBootException unauthorizedOrForbidden(String msg) {
    return isLoggedIn() ? forbidden(msg) : unauthorized(msg);
  }

  /** Best-effort login probe; never throws. */
  public static boolean isLoggedIn() {
    try {
      return StpUtil.isLogin();
    } catch (Exception ignored) {
      return false;
    }
  }
}
