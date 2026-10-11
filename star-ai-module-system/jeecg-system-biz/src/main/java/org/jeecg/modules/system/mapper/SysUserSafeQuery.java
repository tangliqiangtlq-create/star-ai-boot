package org.jeecg.modules.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.modules.system.entity.SysUser;

/**
 * C6-safe SELECT column lists for {@code sys_user} (CodePlan-SensitiveColumnLeakage-Fix P2).
 *
 * <p>Single source of truth for MyBatis-Plus (Java) queries — the XML mirror is the
 * {@code <sql id="safeUserColumns"/>} snippet in SysUserMapper.xml. Every item is a member of
 * {@code SensitiveTableBlacklistInterceptor.SYS_USER_ALLOWED_COLUMNS}, so queries built here pass
 * the firewall for anonymous, OWNER_ADMIN and authenticated non-admin callers alike, and never
 * pull password/salt/reason/is_sender/wallet_address/financial_state/kyc_attachment_1..3/
 * last_password_change_time into the result set.
 *
 * <p>Queries that genuinely need other non-whitelisted columns must use an explicit narrow
 * {@code .select(SysUser::...)} AND the column must be admitted by the C6 whitelist first.
 */
public final class SysUserSafeQuery {

  private SysUserSafeQuery() {}

  /**
   * Default column list for authenticated user-info queries (getById/list/page/getOne).
   * Equals the XML {@code safeUserColumns} set plus {@code dlygx} (upstream manager/agent id,
   * needed by maker/checker assisted-account flows and LoginUser serialization).
   */
  public static final String[] COLUMNS =
      new String[] {
        "id", "username", "realname", "work_no", "avatar", "birthday", "sex", "email", "phone",
        "telephone",
        "depart_ids", "user_identity", "status", "activiti_sync", "del_flag",
        "create_by", "create_time", "update_by", "update_time", "home_path",
        "customer_type", "user_type", "kyc_status", "inviter_id", "biz_owner_user_id",
        "tenant_id", "nation", "country", "dlygx"
      };

  /** New {@link QueryWrapper} pre-configured with the C6-safe SELECT column list. */
  public static QueryWrapper<SysUser> wrapper() {
    return new QueryWrapper<SysUser>().select(COLUMNS);
  }
}
