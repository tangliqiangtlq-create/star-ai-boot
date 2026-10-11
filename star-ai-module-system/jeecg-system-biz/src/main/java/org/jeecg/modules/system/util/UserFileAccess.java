package org.jeecg.modules.system.util;

import cn.hutool.core.util.StrUtil;
import java.util.List;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.vo.LoginUser;

/**
 * Access evaluator for file downloads bound to {@code UsageScenarios} MVEL expressions.
 *
 * <p>Security hardening (Ramp-A2): the previous {@code isAuthenticated()} granted any logged-in
 * tenant user read access to every file sharing the same scenario. That enabled IDOR against
 * KYC/PROOF/MONTHLY_STATEMENT/DEPOSIT attachments. Methods now follow a stricter matrix:
 *
 * <ul>
 *   <li>{@link #isOwner()} — uploader matches current login ID, always allowed.
 *   <li>{@link #isFileAdmin()} — ops_admin/sec_admin/file_admin role, always allowed.
 *   <li>{@link #isLoggedIn()} — any authenticated session (minimum bar for every scenario).
 *   <li>{@link #canViewPublicScenario()} — BANNER/CURRENCY/BLOCKCHAIN assets, logged-in users only.
 *   <li>{@link #canViewWithSignedUrl(boolean)} — signed URL with matching object/user/scenario/ttl.
 * </ul>
 */
public class UserFileAccess {

  private static final List<String> FILE_ADMIN_ROLES =
      List.of("ops_admin", SecurityRoleConstants.ROLE_OPS_ADMIN, SecurityRoleConstants.ROLE_SEC_ADMIN, SecurityRoleConstants.ROLE_FILE_ADMIN, "admin");

  private final LoginUser loginUser;
  private final List<String> roleList;
  private final String ownerId;
  private final boolean signedUrlValid;
  private final boolean publicScenario;

  public UserFileAccess(
      LoginUser loginUser,
      List<String> roleList,
      String ownerId,
      boolean signedUrlValid,
      boolean publicScenario) {
    this.loginUser = loginUser;
    this.roleList = roleList;
    this.ownerId = ownerId;
    this.signedUrlValid = signedUrlValid;
    this.publicScenario = publicScenario;
  }

  /** Legacy constructor — disables signed-url shortcut; kept for tests only. */
  @Deprecated
  public UserFileAccess(LoginUser loginUser, List<String> roleList, String ownerId) {
    this(loginUser, roleList, ownerId, false, false);
  }

  public boolean hasRole(String role) {
    return roleList != null && roleList.contains(role);
  }

  /**
   * True only when the request carries a resolved, logged-in user id.
   *
   * <p>A2-1 hardening: every scenario — including the "public" BANNER/CURRENCY/BLOCKCHAIN
   * assets — now requires at least an authenticated session. Anonymous callers only stay able
   * to download through a server-issued signed URL on the sensitive scenarios.
   */
  public boolean isLoggedIn() {
    return loginUser != null && StrUtil.isNotBlank(loginUser.getId());
  }

  /** True only for the original uploader; random logged-in users are NOT sufficient. */
  public boolean isOwner() {
    return loginUser != null
        && StrUtil.isNotBlank(loginUser.getId())
        && StrUtil.isNotBlank(ownerId)
        && loginUser.getId().equals(ownerId);
  }

  public boolean isFileAdmin() {
    if (roleList == null || roleList.isEmpty()) {
      return false;
    }
    for (String r : FILE_ADMIN_ROLES) {
      if (roleList.contains(r)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Public-scenario assets (BANNER/CURRENCY/BLOCKCHAIN).
   *
   * <p><b>No longer anonymous</b> (A2-1 hardening): the scenario flag only widens access to any
   * <i>logged-in</i> user; anonymous callers are always rejected. Callers that also want admin /
   * owner access must combine this with {@link #isFileAdmin()} / {@link #isOwner()}.
   */
  public boolean canViewPublicScenario() {
    return publicScenario && isLoggedIn();
  }

  /**
   * Allows owner or file_admin. Use for PROOF/MONTHLY_STATEMENT/DEPOSIT where signed URLs are
   * issued server-side and passed directly into {@code canViewWithSignedUrl}.
   */
  public boolean canViewOwnerOrAdmin() {
    return isOwner() || isFileAdmin();
  }

  /** Three-way allow: owner | admin | signed URL that the caller already validated. */
  public boolean canViewWithSignedUrl() {
    return isOwner() || isFileAdmin() || signedUrlValid;
  }

  /**
   * KYC / PROOF attachments: uploader self OR signed URL OR admin.
   *
   * <p>This replaces the previous {@code canViewByOwnerOrLoginUser()} which leaked every
   * attachment to every authenticated user.
   */
  public boolean canViewKycProof() {
    return isOwner() || isFileAdmin() || signedUrlValid;
  }

  /**
   * Deprecated name retained for compiled references in older MVEL expressions; behaviour now
   * matches the strict {@link #canViewKycProof()}.
   */
  public boolean canViewByOwnerOrLoginUser() {
    return canViewKycProof();
  }

  /**
   * Deprecated — used by old MVEL strings; previously returned {@code loginUser != null} which
   * granted cross-tenant access. Now strictly requires a login session AND (public asset OR admin
   * OR owner).
   */
  public boolean isAuthenticated() {
    return isLoggedIn() && (canViewPublicScenario() || canViewOwnerOrAdmin());
  }
}
