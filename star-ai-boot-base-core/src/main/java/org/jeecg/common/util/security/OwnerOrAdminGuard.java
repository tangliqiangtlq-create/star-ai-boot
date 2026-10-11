package org.jeecg.common.util.security;

import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.config.oss.OssTenantOwnerUtil;

/**
 * PR-CL-A2: Base row-level owner-or-admin guard. Every high-sensitivity bucket (OSS files, KYC
 * documents, bank statements, beneficiaries, remittance records etc.) plugs in a concrete {@link
 * BucketOwnerResolver} via the static helper methods; the guard short-circuits with a 403-equivalent
 * exception if the caller is neither (a) the row's original owner nor (b) any of the file-admin /
 * ops-admin roles declared in {@link OssTenantOwnerUtil#FILE_ADMIN_ROLES}.
 *
 * <p>Usage pattern (inside any Controller / Service with user context):
 *
 * <pre>{@code
 * OssFile file = ossFileMapper.selectById(id);
 * OwnerOrAdminGuard.assertOwnerOrAdmin(file, BUCKET_RESOLVER_OSS_FILE, "ossFile");
 * }</pre>
 *
 * <p>Fail-close: if {@code entity} is {@code null} the assertion treats it as "not found" which in
 * turn throws the same unauthorized exception (avoids 403-disclosing vs. 404 enumeration attacks).
 */
public final class OwnerOrAdminGuard {

  private OwnerOrAdminGuard() {}

  /**
   * Lambda-style extractor for an arbitrary row-like {@code T} so the same guard logic applies to
   * every table without forcing a shared interface.
   *
   * @param <T> entity/row type (typically MyBatis-Plus entity)
   */
  @FunctionalInterface
  public interface BucketOwnerResolver<T> {

    /** @return the owning user's id (typically the DB {@code user_id} or {@code create_by} column) */
    String ownerUserId(T entity);
  }

  /**
   * Shortcut for the most common shape: entities that store the owner in a {@code createBy} column
   * (as created by the Jeecg code generator for the dcs_* schema).
   */
  public static final BucketOwnerResolver<Object> DEFAULT_CREATE_BY =
      entity -> {
        if (entity == null) {
          return null;
        }
        try {
          java.lang.reflect.Method m = entity.getClass().getMethod("getCreateBy");
          Object v = m.invoke(entity);
          return v == null ? null : String.valueOf(v);
        } catch (ReflectiveOperationException ignored) {
          return null;
        }
      };

  public static <T> void assertOwnerOrAdmin(T entity, BucketOwnerResolver<T> resolver, String bucket) {
    assertOwnerOrAdmin(entity, resolver, bucket, OssTenantOwnerUtil.FILE_ADMIN_ROLES);
  }

  public static <T> void assertOwnerOrAdmin(
      T entity, BucketOwnerResolver<T> resolver, String bucket, List<String> adminRoles) {
    if (entity == null) {
      // Fail-close: treat "not found" as a denial and answer 403 to avoid 403-vs-404 enumeration.
      throw Rejections.forbidden(I18nUtil.getMessage("common.error.unauthorized"));
    }
    String owner = resolver.ownerUserId(entity);
    String callerId;
    try {
      callerId = StpUtil.getLoginIdAsString();
    } catch (Exception notLoggedIn) {
      // Not authenticated -> 401 (please sign in), not HTTP 200 + business 500.
      throw Rejections.unauthorized(I18nUtil.getMessage("common.error.unauthorized"));
    }
    if (callerId == null) {
      throw Rejections.unauthorized(I18nUtil.getMessage("common.error.unauthorized"));
    }
    if (callerId.equals(owner)) {
      return;
    }
    if (hasAnyRole(adminRoles)) {
      return;
    }
    // Authenticated but neither owner nor admin -> 403.
    throw Rejections.forbidden(I18nUtil.getMessage("common.error.unauthorized"));
  }

  private static boolean hasAnyRole(List<String> adminRoles) {
    return RoleCacheHelper.hasAnyRole(adminRoles);
  }
}
