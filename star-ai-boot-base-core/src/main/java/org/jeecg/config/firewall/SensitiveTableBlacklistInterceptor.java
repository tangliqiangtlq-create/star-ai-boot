package org.jeecg.config.firewall;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.constant.TenantConstant;
import org.jeecg.common.system.security.CommonRoleGuard;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.config.JeecgBaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * MyBatis-Plus inner interceptor that blocks SELECT / INSERT / UPDATE / DELETE against known
 * sensitive tables for any caller that is neither (a) an admin role (ops_admin/sec_admin/admin/
 * super_admin), (b) the "internal_call" system user, nor (c) explicitly allowed by Mapper id
 * prefix.
 *
 * <p>Designed to close Ramp-A3-4: any user with dict or low-code write access was able to point
 * a {@code DictTableWhiteList} / raw dict-data SQL at {@code sys_user_mfa.secret_enc},
 * {@code dcs_blockchain_api_key.api_secret}, {@code *_password}, or {@code *_token} columns;
 * the SQL passed through at MyBatis layer without any business identity check.
 *
 * <p>The {@code internal_call} bypass keeps Feign-internal flows (e.g. MFA validation itself)
 * working without hard-coding every Mapper id. Non-admin interactive callers always hit the
 * blacklist.
 */
public class SensitiveTableBlacklistInterceptor implements InnerInterceptor, InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(SensitiveTableBlacklistInterceptor.class);

  public static final String AUDIT_LOG_PREFIX = "[SENSITIVE_TABLE_BLOCKED]";
  public static final String ALERT_REDIS_KEY_TMPL = "sec:alert:sens_table:daily:%s";

  private static final List<String> SENSITIVE_PREFIXES =
      Arrays.asList(
          "sys_user_mfa",
          "sys_log",
          "sys_user_role",
          "sys_user_depart",
          "sys_user_post",
          "dcs_blockchain_api_key",
          "sys_permission",
          "sys_depart_permission",
          "sys_role_permission",
          "sys_role",
          "sys_depart_role_user",
          "sys_data_source",
          "dcs_withdrawal_account",
          "dcs_bank_card",
          // A5-2: Approval engine core tables. Without this the Dict SQL whitelist / low-code
          // reporter roles can list assignees by querying act_ru_task.assignee_ directly and
          // reconstruct the approval graph.
          "sys_process_",
          "act_ru_",
          "act_hi_",
          "act_re_",
          "act_ge_",
          "sys_state_definition",
          "event_log",
          "sys_user",
          "sys_config"
      );

  private static final List<String> SENSITIVE_REGEXES =
      Arrays.asList(
          "(?i)^(.*_)?secret(.*)?$",
          "(?i)^(.*_)?password(.*)?$",
          "(?i)^(.*_)?private[_-]?key(.*)?$",
          "(?i)^(.*_)?seed(.*)?$",
          "(?i)^(.*_)?otp(.*)?$",
          "(?i)^(.*_)?token(.*)?$"
      );

  private static final List<String> ADMIN_ROLE_CODES =
      Arrays.asList(
          "admin",
          "ops_admin",
          SecurityRoleConstants.ROLE_OPS_ADMIN,
          SecurityRoleConstants.ROLE_SEC_ADMIN,
          TenantConstant.SUPER_ADMIN);

  /**
   * Instance-free sensitive-name test sharing the exact prefix/regex rules this interceptor applies
   * to table names. Used by the dict endpoints to hard-reject a sensitive dict code/table with HTTP
   * 403 instead of answering 200 with an empty list (audit gap: "敏感 dict 必须 403/硬拒绝").
   */
  public static boolean isSensitiveTableName(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    String tl = name.trim().toLowerCase();
    for (String p : SENSITIVE_PREFIXES) {
      String pl = p.trim().toLowerCase();
      if (tl.equals(pl) || tl.startsWith(pl + "_") || tl.startsWith(pl)) {
        return true;
      }
    }
    for (String r : SENSITIVE_REGEXES) {
      if (Pattern.compile(r).matcher(tl).matches()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Minimum required columns that normal (non-OWNER_ADMIN) interactive business roles
   * (USER, AGENT, merchant_*, ops_*, kyc_*, etc.) need so /sys/user/getUserInfo and
   * /sys/getCurrUser can finish with a 200 after StpUtil.login().
   *
   * DESIGN NOTES:
   *  - This is PHASE A (P0 stop-bleed, < 10 min patch). The real fix (PHASE B) is to
   *    eliminate every `SELECT *` in SysUserMapper.xml and hand-write explicit column
   *    lists per query; this list can then shrink to ~12 fields.
   *  - DELIBERATELY EXCLUDED (NEVER ADD without a 4-eyes CISO review):
   *      password / salt / secret (2FA pepper bits) / token* / otp_seed_enc /
   *      kyc_attachment_1..3 (blob CLOB of ID documents) / private_key / seed /
   *      tfa* / totp* / hotp* / api_key* / mfa*_secret / last_login_ip_raw /
   *      *_signature / *_hmac / aes_key_* / jwe_*.
   *    Any attempt to SELECT these columns via SysUserMapper still gets intercepted by
   *    SENSITIVE_REGEXES (validate() stage) BEFORE we even call validateSysUserColumns().
   *  - Password-reset flows use a dedicated Mapper (passwordreset.mapper.*) which is in
   *    ALLOWED_MAPPER_PREFIXES, so they bypass validateSysUserColumns() entirely.
   *  - Dict/low-code tables are protected via the sys_table_white_list.status=1 gate; an
   *    interactive user (not a Mapper id) trying to SELECT password via the generic
   *    dict endpoint still gets blocked in validate() (SENSITIVE_PREFIXES + role check).
   *  - owner_admin roles (admin/operations_admin/file_admin/sec_admin/super_admin, i.e.
   *    CommonRoleGuard.OWNER_ADMIN_ROLE_SET) do NOT hit this whitelist: they return
   *    early from validateSysUserColumns() at L501-L507.
   */
  private static final Set<String> SYS_USER_ALLOWED_COLUMNS =
      new HashSet<>(
          Arrays.asList(
              "id",
              "username",
              "realname",
              "work_no",
              "avatar",
              "birthday",
              "sex",
              "email",
              "phone",
              "telephone",
              "depart_ids",
              "role_code",
              "user_identity",
              "status",
              "activiti_sync",
              "del_flag",
              "create_by",
              "create_time",
              "update_by",
              "update_time",
              "home_path",
              "customer_type",
              "user_type",
              "kyc_status",
              "inviter_id",
              "biz_owner_user_id",
              "tenant_id",
              "nation",
              "country",
              // Non-secret operational columns required by authenticated business flows
              // (CodePlan-SensitiveColumnLeakage-Fix P2). They are NOT credentials/PII and are not
              // in the DELIBERATELY EXCLUDED list:
              //  - dlygx: upstream manager/agent user id (business relation, like inviter_id)
              //  - allow_login: login-permitted flag (status flag, like status)
              //  - mfa_master_version / mfa_legacy_cutoff_at: MFA crypto migration enforcement
              "dlygx",
              "allow_login",
              "mfa_master_version",
              "mfa_legacy_cutoff_at",
              // Remaining non-sensitive operational columns mapped by the SysUser entity.
              // Default MP queries (getById/selectList/page) project EVERY column that is not
              // @TableField(select=false); C6 must accept all of them or generic paths such as
              // SysUserController.listTeamMembers fail with C6_COLUMN_BLOCKED (2026-09-30).
              // Keep this list a superset of {entity fields - select=false - exist=false}.
              "email_notice",
              "org_code",
              "login_tenant_id",
              "client_id",
              "bpm_status",
              "phone_code",
              "id_type",
              "new_submit",
              "last_login_time"));

  /**
   * P3: MappedStatement ids permitted to project sys_user credential columns (password/salt)
   * for TRUSTED S2S sessions only. Both conditions are mandatory:
   * <ol>
   *   <li>The Sa-Token login id must be an ephemeral {@code s2s:<module>} principal created by
   *       {@code InternalTrustedS2sFilter.ensureEphemeralS2sSession} AFTER CIDR + shared-secret
   *       verification — an interactive browser session can never hold it.</li>
   *   <li>The statement id must be this exact, code-reviewed projection
   *       ({@code selectCredentialByUsername}). MP-generated statements (selectById/selectList/
   *       selectOne) are never listed, so the carve-out cannot be reached from ordinary queries.</li>
   * </ol>
   * Purpose: system service performs password verification itself for dcs via
   * {@code POST /sys/api/verifyPassword}, so password hashes never cross Feign.
   */
  private static final Set<String> S2S_CREDENTIAL_VERIFY_MAPPER_IDS =
      new HashSet<>(
          Arrays.asList(
              "org.jeecg.modules.system.mapper.SysUserMapper.selectCredentialByUsername"));

  /**
   * L27-2: MappedStatement ids permitted to project sys_user.password for <b>self</b> credential
   * verification. Admission requires BOTH the pinned statement id AND
   * {@code bound id == StpUtil.getLoginIdAsString()} (see {@link #isSelfLoginId}). Rationale:
   * second-auth {@code EMAIL_PASSWORD_REQUIRED} (setPaymentPassword / changePaymentPassword /
   * disableTotp) must compare the caller's own login password for <b>non-admin</b> sessions, which
   * the generic whitelist deliberately blocks. A foreign id can never match, so this does not
   * reopen cross-account credential reads.
   */
  private static final Set<String> SELF_CREDENTIAL_VERIFY_MAPPER_IDS =
      new HashSet<>(
          Arrays.asList(
              "org.jeecg.modules.system.mapper.SysUserMapper.selectCredentialById"));

  /**
   * L27-2 (root cause ②): MappedStatement ids permitted to read/write {@code sys_user_credentials} —
   * a table the stage-1 gate treats as sensitive because {@link #SENSITIVE_PREFIXES} holds the loose
   * prefix {@code sys_user} and {@link #matchesPrefix} uses {@code startsWith}. Admission requires
   * BOTH the pinned statement id AND a bound {@code userId} equal to
   * {@code StpUtil.getLoginIdAsString()} (see {@link #isSelfScopedCredentialStatement}). Without this
   * the interactive payment-password flows (setPaymentPassword / changePaymentPassword /
   * checkPaymentPassword) fail for every non-admin user. The MyBatis-Plus statements
   * (…UserCredentialsMapper.selectOne / insert / updateById) carry an {@code ew} / {@code et} wrapper
   * parameter the gate cannot attribute to an owner, so they remain blocked.
   */
  private static final Set<String> SELF_SCOPED_CREDENTIAL_MAPPER_IDS =
      new HashSet<>(
          Arrays.asList(
              // "org.jeecg.modules.system.mapper.UserCredentialsMapper.selectByUserIdAndType", // NOTE: cleaned
              "org.jeecg.modules.system.mapper.UserCredentialsMapper.insertSelfCredential",
              "org.jeecg.modules.system.mapper.UserCredentialsMapper.updateSelfCredential"));

  /** The only table {@link #SELF_SCOPED_CREDENTIAL_MAPPER_IDS} may touch. */
  private static final String SELF_CREDENTIAL_TABLE = "sys_user_credentials";

  /**
   * C6-4: MappedStatement ids permitted to project {@code sys_user.kyc_attachment_1/2/3}.
   *
   * <p>Those three columns are DELIBERATELY EXCLUDED from {@link #SYS_USER_ALLOWED_COLUMNS} (they
   * hold the OSS objectIds of identity documents) and MUST NOT be added there. The L27 design
   * records the sanctioned alternative explicitly: "kyc_attachment_1/2/3 — excluded; MFA/kyc pages
   * must route through dedicated Mappers"
   * ({@code 第二轮/L-剩余收尾散项杂项/01-L类子项-L27-登录后C6_COLUMN_BLOCKED阻断修复方案-v1.0.md}).
   *
   * <p>Admission requires ALL of:
   * <ol>
   *   <li>this exact, code-reviewed statement id — MyBatis-Plus generated statements
   *       ({@code selectById} / {@code selectList} / {@code selectOne}) and every dict / low-code
   *       statement can never produce it, so the column-fishing paths C6 exists to stop are
   *       unreachable;</li>
   *   <li>a projection pinned to {@link #SYS_USER_KYC_SLOT_COLUMNS} — if anyone later widens the
   *       {@code @Select} list (e.g. adds {@code password}), the pin fails and the statement falls
   *       back to the ordinary column whitelist, i.e. it is blocked;</li>
   *   <li>the caller either owns the row ({@code bound id == StpUtil.getLoginIdAsString()}) or
   *       holds {@link #KYC_ADMIN_PERMISSION} — the same permission
   *       {@code SysUserKycAttachmentController.adminSave/adminClear} already enforce, so the
   *       carve-out grants no principal anything it could not already obtain through the KYC
   *       admin API.</li>
   * </ol>
   * The global whitelist is untouched, so {@code SELECT *} and any other {@code sys_user}
   * projection remain blocked exactly as before.
   */
  private static final Set<String> SYS_USER_KYC_SLOT_MAPPER_IDS =
      new HashSet<>(
          Arrays.asList("org.jeecg.modules.system.mapper.SysUserMapper.selectKycSlotsById"));

  /**
   * The exact projection {@link #SYS_USER_KYC_SLOT_MAPPER_IDS} may select, and nothing else.
   * {@code id} is required to bind the owner check; the three slots are the payload.
   */
  private static final Set<String> SYS_USER_KYC_SLOT_COLUMNS =
      new HashSet<>(
          Arrays.asList("id", "kyc_attachment_1", "kyc_attachment_2", "kyc_attachment_3"));

  /**
   * Permission required to reach the KYC slot projection for a row the caller does not own. Kept in
   * lock-step with {@code SysUserKycAttachmentController.adminSave/adminClear}
   * ({@code @SaCheckPermission("system:user:edit")}) and seeded in
   * {@code docs/db/mpay_system.sql} (id {@code 1214376304951664642}).
   */
  private static final String KYC_ADMIN_PERMISSION = "system:user:edit";

  private static final String S2S_LOGIN_ID_PREFIX = "s2s:";

  private static final String C6_REFUSE_START_SIGN_DISABLED =
      "C6 REFUSE TO START signEnabled=false bypass removed; set jeecg.signEnabled=true via yml/nacos or re-enable signature validation.";

  private static final String C6_COLUMN_BLOCKED = "C6_COLUMN_BLOCKED";

  private static final LoadingCache<String, List<String>> SQL_PARSE_CACHE;

  static {
    SQL_PARSE_CACHE =
        CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(60, java.util.concurrent.TimeUnit.SECONDS)
            .build(
                new CacheLoader<String, List<String>>() {
                  @Override
                  public List<String> load(String sql) throws Exception {
                    return extractAllTablesUncached(sql);
                  }
                });
  }

  public static void bustSqlParseCache() {
    SQL_PARSE_CACHE.invalidateAll();
  }

  private static final List<String> ALLOWED_MAPPER_PREFIXES =
      Arrays.asList(
          "org.jeecg.modules.system.mapper.SysUser",
          "org.jeecg.modules.system.mapper.SysUserMfa",
          "org.jeecg.modules.system.mapper.SysRole",
          "org.jeecg.modules.system.mapper.SysUserRole",
          "org.jeecg.modules.system.mapper.SysPermission",
          // Actual Mapper: BlockchainApiKeyMapper (+ AuditLogMapper); not DcsBlockchainApiKey*
          "org.jeecg.modules.dcs.mapper.BlockchainApiKey",
          "org.jeecg.modules.system.mapper.SysDepart",
          // A5-2: allow the approval engine modules to drive their own tables; interactive dict
          // reporter roles still get intercepted because they don't route through these Mappers.
          "org.jeecg.modules.processengine.mapper.",
          "org.jeecg.modules.dcs.mapper.DepositRecordMapper",
          // Password-reset module: tables match (?i).*password.* regex (sys_password_reset_*),
          // but scheduled cleanup + link lifecycle run without login (anonymous). Dict/low-code
          // still blocked — they never use these Mapper ids.
          "org.jeecg.modules.system.passwordreset.mapper.",
          // Audit log write/cleanup: AutoLog / login / XXL deleteSysLog_job. sys_log is blacklisted
          // so dict/low-code cannot read it; these Mapper ids are the only allowed writers.
          // Login happens as anonymous — must NOT require admin role.
          "org.jeecg.modules.base.mapper.BaseCommonMapper",
          "org.jeecg.modules.system.mapper.SysLogMapper"
      );

  /** Dict Mapper: allowed only when the table is also in {@code sys_table_white_list}. */
  private static final String DICT_MAPPER_PREFIX =
      "org.jeecg.modules.system.mapper.SysDictMapper";

  /** Tables present in {@code sys_table_white_list} (status=1) — refreshed periodically. */
  private static final long WHITE_LIST_TTL_MS = 60_000L;
  private volatile java.util.Set<String> dbWhiteTables = java.util.Collections.emptySet();
  private volatile long dbWhiteTablesAt = 0L;

  private final List<Predicate<String>> matchers = new ArrayList<>();
  private final List<Pattern> regexPatterns = new ArrayList<>();
  private final List<String> prefixLower = new ArrayList<>();

  public SensitiveTableBlacklistInterceptor() {
    for (String s : SENSITIVE_PREFIXES) {
      prefixLower.add(s.trim().toLowerCase());
    }
    for (String regex : SENSITIVE_REGEXES) {
      regexPatterns.add(Pattern.compile(regex));
    }
    matchers.add(this::matchesPrefix);
    matchers.add(this::matchesRegex);
  }

  @Override
  public void afterPropertiesSet() {
    try {
      JeecgBaseConfig cfg = SpringContextUtils.getBean(JeecgBaseConfig.class);
      if (cfg == null || !cfg.isSignEnabled()) {
        log.error(C6_REFUSE_START_SIGN_DISABLED);
        throw new BeanInitializationException(C6_REFUSE_START_SIGN_DISABLED);
      }
    } catch (BeanInitializationException be) {
      throw be;
    } catch (Exception e) {
      log.error(C6_REFUSE_START_SIGN_DISABLED + " | resolve error: {}", e.getMessage());
      throw new BeanInitializationException(C6_REFUSE_START_SIGN_DISABLED, e);
    }
    log.info(
        "SensitiveTableBlacklistInterceptor loaded: prefixes={}, regexes={}",
        prefixLower.size(),
        regexPatterns.size());
  }

  @Override
  public void beforeQuery(
      Executor executor,
      MappedStatement ms,
      Object parameter,
      RowBounds rowBounds,
      ResultHandler resultHandler,
      BoundSql boundSql)
      throws SQLException {
    validate(ms, boundSql);
    validateSysUserColumns(ms, boundSql);
  }

  @Override
  public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
      throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    validate(ms, boundSql);
  }

  private void validate(MappedStatement ms, BoundSql boundSql) throws SQLException {
    String sql = boundSql == null ? null : boundSql.getSql();
    if (sql == null || sql.isEmpty()) {
      return;
    }
    String low = sql.trim().toLowerCase();
    if (!looksLikeTableDriven(low)) {
      return;
    }
    List<String> tables;
    try {
      tables = extractAllTables(sql);
    } catch (Exception e) {
      log.warn("SensitiveTableBlacklistInterceptor parse skip: {} | sql={}", e.getMessage(), sql);
      return;
    }
    for (String t : tables) {
      if (isSensitive(t)) {
        if (isAdminOrInternal(ms, t)) {
          return;
        }
        // L27-2 (root cause ②): self-scoped payment-credential access. Pinned statement AND bound
        // userId == caller's own login id, and only for sys_user_credentials. A foreign userId falls
        // through and is still blocked below.
        if (SELF_CREDENTIAL_TABLE.equalsIgnoreCase(t == null ? "" : t.trim())
            && isSelfScopedCredentialStatement(ms, boundSql)) {
          log.info(
              "[SEC-A34-SELF-CREDENTIAL-ALLOW] uid={} ms={} table={}",
              currentUsername(),
              ms == null ? null : ms.getId(),
              t);
          return;
        }
        String blocked =
            AUDIT_LOG_PREFIX
                + " table="
                + t
                + " user="
                + currentUsername()
                + " ms="
                + ms.getId()
                + " sql="
                + truncate(sql, 500);
        log.error(blocked);
        alert(blocked);
        throw new SQLException("sensitive table query blocked: " + t);
      }
    }
  }

  private boolean looksLikeTableDriven(String low) {
    return low.startsWith("select")
        || low.startsWith("insert")
        || low.startsWith("update")
        || low.startsWith("delete")
        || low.startsWith("with ");
  }

  @SuppressWarnings("unchecked")
  private void alert(String blocked) {
    try {
      RedisTemplate<String, String> rt =
          (RedisTemplate<String, String>)
              SpringContextUtils.getBean("redisTemplate", RedisTemplate.class);
      if (rt != null) {
        String user = currentUsername();
        String key = String.format(ALERT_REDIS_KEY_TMPL, user == null ? "_anon_" : user);
        ValueOperations<String, String> ops = rt.opsForValue();
        ops.increment(key, 1L);
        rt.expireAt(key, new java.util.Date(System.currentTimeMillis() + 86400_000L));
      }
    } catch (Exception ignore) {
      // never throw on alert failure
    }
  }

  private boolean isSensitive(String t) {
    if (t == null) {
      return false;
    }
    String tl = t.toLowerCase();
    for (Predicate<String> p : matchers) {
      if (p.test(tl)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesPrefix(String tl) {
    for (String p : prefixLower) {
      if (tl.equals(p) || tl.startsWith(p + "_") || tl.startsWith(p)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesRegex(String tl) {
    for (Pattern p : regexPatterns) {
      if (p.matcher(tl).matches()) {
        return true;
      }
    }
    return false;
  }

  private boolean isAdminOrInternal(MappedStatement ms, String table) {
    try {
      if (ms != null && ms.getId() != null) {
        String mid = ms.getId();
        // Dict path: SysDictMapper + row in sys_table_white_list (seed via V1.0.85 etc.)
        if (mid.startsWith(DICT_MAPPER_PREFIX) && isDbWhitelisted(table)) {
          return true;
        }
        for (String prefix : ALLOWED_MAPPER_PREFIXES) {
          if (mid.startsWith(prefix)) {
            return true;
          }
        }
      }
      if (StpUtil.isLogin()) {
        List<String> roles = getCurrentRoleCodes();
        if (roles != null) {
          for (String r : roles) {
            if (ADMIN_ROLE_CODES.contains(r.toLowerCase())) {
              return true;
            }
          }
        }
        Object principal = StpUtil.getSession().get("loginUser");
        if (principal instanceof org.jeecg.common.system.vo.LoginUser) {
          String username = ((org.jeecg.common.system.vo.LoginUser) principal).getUsername();
          if ("internal_call".equalsIgnoreCase(username)) {
            return true;
          }
        }
      }
    } catch (Exception e) {
      // default deny
    }
    return false;
  }

  /**
   * Honor {@code sys_table_white_list} rows (status=1). Seed via migrations such as
   * {@code V1.0.85__sys_process_definition_table_whitelist.sql}. Cache TTL 60s.
   */
  private boolean isDbWhitelisted(String table) {
    if (table == null || table.isEmpty()) {
      return false;
    }
    refreshDbWhiteTablesIfNeeded();
    return dbWhiteTables.contains(table.toLowerCase());
  }

  @SuppressWarnings("unchecked")
  private void refreshDbWhiteTablesIfNeeded() {
    long now = System.currentTimeMillis();
    if (now - dbWhiteTablesAt < WHITE_LIST_TTL_MS && !dbWhiteTables.isEmpty()) {
      return;
    }
    synchronized (this) {
      if (now - dbWhiteTablesAt < WHITE_LIST_TTL_MS && !dbWhiteTables.isEmpty()) {
        return;
      }
      try {
        org.springframework.jdbc.core.JdbcTemplate jdbc =
            SpringContextUtils.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        if (jdbc == null) {
          return;
        }
        List<String> names =
            jdbc.query(
                "SELECT table_name FROM sys_table_white_list WHERE status = '1'",
                (rs, rowNum) -> {
                  String n = rs.getString(1);
                  return n == null ? null : n.toLowerCase();
                });
        java.util.Set<String> next = new java.util.HashSet<>();
        if (names != null) {
          for (String n : names) {
            if (n != null && !n.isEmpty()) {
              next.add(n);
            }
          }
        }
        dbWhiteTables = next;
        dbWhiteTablesAt = System.currentTimeMillis();
        bustSqlParseCache();
      } catch (Exception e) {
        log.warn("SensitiveTableBlacklistInterceptor whitelist refresh skip: {}", e.getMessage());
        dbWhiteTablesAt = System.currentTimeMillis();
      }
    }
  }

  private List<String> getCurrentRoleCodes() {
    try {
      Object raw = StpUtil.getRoleList();
      if (raw instanceof List) {
        List<Object> ls = (List<Object>) raw;
        List<String> out = new ArrayList<>(ls.size());
        for (Object o : ls) {
          if (o != null) {
            out.add(String.valueOf(o).toLowerCase());
          }
        }
        return out;
      }
    } catch (Exception ignore) {
    }
    return Collections.emptyList();
  }

  private String currentUsername() {
    try {
      if (StpUtil.isLogin()) {
        return String.valueOf(StpUtil.getLoginIdDefaultNull());
      }
    } catch (Exception ignore) {
    }
    return "anonymous";
  }

  /**
   * Whether the current principal is an ephemeral trusted S2S session ({@code s2s:<module>})
   * created by {@code InternalTrustedS2sFilter} after CIDR allow-listing and internal shared
   * secret verification. Interactive browser/APP logins never carry this principal, so it is a
   * safe gate for the pinned credential-verification statement carve-out.
   */
  private static boolean isTrustedS2sSession() {
    try {
      if (!StpUtil.isLogin()) {
        return false;
      }
      Object loginId = StpUtil.getLoginIdDefaultNull();
      return loginId != null
          && String.valueOf(loginId).startsWith(S2S_LOGIN_ID_PREFIX);
    } catch (Exception ignore) {
      return false;
    }
  }

  /**
   * Extract all table references from a SQL statement using JSqlParser {@link TablesNamesFinder}.
   * Works for SELECT / INSERT / UPDATE / DELETE / WITH clauses.
   *
   * <p>Results are cached for 60 seconds per identical SQL text (max 10 000 entries). Use {@link
   * #bustSqlParseCache()} after whitelist refreshes or parser upgrades.
   */
  static List<String> extractAllTables(String sql) throws JSQLParserException {
    if (sql == null) {
      return Collections.emptyList();
    }
    try {
      return SQL_PARSE_CACHE.getUnchecked(sql);
    } catch (com.google.common.util.concurrent.UncheckedExecutionException ue) {
      Throwable cause = ue.getCause();
      if (cause instanceof JSQLParserException) {
        throw (JSQLParserException) cause;
      }
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new JSQLParserException(cause == null ? ue : cause);
    }
  }

  static List<String> extractAllTablesUncached(String sql) throws JSQLParserException {
    if (sql == null) {
      return Collections.emptyList();
    }
    Statement stmt = CCJSqlParserUtil.parse(sql);
    TablesNamesFinder finder = new TablesNamesFinder();
    List<String> names = finder.getTableList(stmt);
    return names == null ? Collections.emptyList() : names;
  }

  /**
   * L27-2: true only when the pinned self-credential statement is bound to the caller's own Sa-Token
   * login id. Fails closed on any Sa-Token error or unresolvable parameter.
   */
  private static boolean isSelfLoginId(BoundSql boundSql) {
    return isBoundParamSelf(boundSql, "id");
  }

  /**
   * L27-2 (root cause ②): true only for a pinned {@code sys_user_credentials} statement whose bound
   * {@code userId} is the caller's own login id. The statement-id check runs first so a foreign or
   * MyBatis-Plus-generated statement can never reach the self comparison.
   */
  private static boolean isSelfScopedCredentialStatement(MappedStatement ms, BoundSql boundSql) {
    if (ms == null
        || ms.getId() == null
        || !SELF_SCOPED_CREDENTIAL_MAPPER_IDS.contains(ms.getId())) {
      return false;
    }
    return isBoundParamSelf(boundSql, "userId");
  }

  /**
   * C6-4: true only for the pinned KYC slot statement, with a projection limited to
   * {@link #SYS_USER_KYC_SLOT_COLUMNS}, called by the row owner or by a holder of
   * {@link #KYC_ADMIN_PERMISSION}. The statement-id check runs first, so a foreign or
   * MyBatis-Plus-generated statement can never reach the rest of the test. Fails closed on any
   * Sa-Token error, unresolvable parameter, or projection that is not exactly the pinned set.
   */
  private static boolean isKycSlotStatement(
      MappedStatement ms, BoundSql boundSql, Set<String> selectedCols) {
    if (ms == null || ms.getId() == null || !SYS_USER_KYC_SLOT_MAPPER_IDS.contains(ms.getId())) {
      return false;
    }
    // Projection pin first: keeps the carve-out tied to the reviewed @Select column list even if
    // the statement id is ever reused for a wider projection.
    if (!isKycSlotProjectionPinned(selectedCols)) {
      return false;
    }
    // Owner-of-row read (profile center "mine") ...
    if (isBoundParamSelf(boundSql, "id")) {
      return true;
    }
    // ... or a user administrator, i.e. the exact permission the KYC admin endpoints require.
    try {
      return StpUtil.isLogin() && StpUtil.hasPermission(KYC_ADMIN_PERMISSION);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Pure projection pin for {@link #SYS_USER_KYC_SLOT_MAPPER_IDS}: every projected column must be
   * one of {@link #SYS_USER_KYC_SLOT_COLUMNS} and nothing else, so a later widening of the pinned
   * {@code @Select} (e.g. adding {@code password}) drops out of the carve-out and is blocked by the
   * ordinary whitelist. Package-private for the regression test; touches no Sa-Token state.
   */
  static boolean isKycSlotProjectionPinned(Set<String> selectedCols) {
    if (selectedCols == null || selectedCols.isEmpty()) {
      return false;
    }
    for (String col : selectedCols) {
      String clean = normalizeColumnName(col);
      if (clean.isEmpty() || !SYS_USER_KYC_SLOT_COLUMNS.contains(clean)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Lower-cases and strips a {@code table.} qualifier / backticks from a projected column name, the
   * same normalization {@link #validateSysUserColumns} applies to the whitelist comparison.
   */
  private static String normalizeColumnName(String col) {
    String clean = col == null ? "" : col.trim().toLowerCase();
    if (clean.isEmpty()) {
      return clean;
    }
    int dot = clean.lastIndexOf('.');
    if (dot >= 0) {
      clean = clean.substring(dot + 1);
    }
    return clean.replace("`", "");
  }

  /**
   * True only when the caller's own Sa-Token login id is bound to {@code key}. Fails closed on any
   * Sa-Token error or unresolvable parameter.
   */
  private static boolean isBoundParamSelf(BoundSql boundSql, String key) {
    String target = extractBoundParam(boundSql, key);
    if (target == null || target.isBlank()) {
      return false;
    }
    try {
      return target.equals(StpUtil.getLoginIdAsString());
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Extracts the value bound to {@code #{key}} from the statement parameter object. Every pinned
   * statement declares its arguments with {@code @Param}, so MyBatis passes a ParamMap keyed by the
   * annotation value; the raw-scalar branch is kept as a safety net.
   */
  private static String extractBoundParam(BoundSql boundSql, String key) {
    Object po = boundSql == null ? null : boundSql.getParameterObject();
    if (po == null) {
      return null;
    }
    if (po instanceof CharSequence) {
      return po.toString();
    }
    if (po instanceof java.util.Map) {
      Object v = ((java.util.Map<?, ?>) po).get(key);
      return v == null ? null : String.valueOf(v);
    }
    return null;
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    if (s.length() <= max) {
      return s;
    }
    return s.substring(0, max) + "...<truncated>";
  }

  private void validateSysUserColumns(MappedStatement ms, BoundSql boundSql) throws SQLException {
    if (boundSql == null) {
      return;
    }
    // P3: trusted S2S session (CIDR + internal secret already verified by
    // InternalTrustedS2sFilter) hitting the pinned credential-verification statement may
    // project password/salt — system verifies credentials on behalf of dcs so hashes never
    // cross Feign. Every other statement/session combination still goes through the whitelist.
    if (ms != null
        && S2S_CREDENTIAL_VERIFY_MAPPER_IDS.contains(ms.getId())
        && isTrustedS2sSession()) {
      log.info(
          "[SEC-C6-S2S-CREDENTIAL-ALLOW] uid={} ms={}",
          currentUsername(),
          ms.getId());
      return;
    }
    // L27-2: self credential verification (non-admin sessions). Allowed only for the pinned
    // statement AND when the bound id is the caller's own login id — a foreign id falls through
    // to the column whitelist below and still raises C6_COLUMN_BLOCKED.
    if (ms != null
        && SELF_CREDENTIAL_VERIFY_MAPPER_IDS.contains(ms.getId())
        && isSelfLoginId(boundSql)) {
      log.info("[SEC-C6-SELF-CREDENTIAL-ALLOW] uid={} ms={}", currentUsername(), ms.getId());
      return;
    }
    String sql = boundSql.getSql();
    if (sql == null || sql.isEmpty()) {
      return;
    }
    String low = sql.trim().toLowerCase();
    if (!low.startsWith("select")) {
      return;
    }
    // C6-2 targets authenticated non-owner callers (dict/low-code column fishing).
    // Anonymous SELECT is required for /sys/login password verify (full SysUser row).
    try {
      if (!StpUtil.isLogin()) {
        return;
      }
    } catch (Exception ignore) {
      return;
    }
    if (CommonRoleGuard.hasAnyOwnerAdminRole()) {
      return;
    }
    List<String> tables;
    try {
      tables = extractAllTables(sql);
    } catch (Exception e) {
      return;
    }
    boolean hasSysUser = false;
    for (String t : tables) {
      if ("sys_user".equalsIgnoreCase(t == null ? "" : t.trim())) {
        hasSysUser = true;
        break;
      }
    }
    if (!hasSysUser) {
      return;
    }
    Set<String> selectedCols;
    try {
      selectedCols = extractSelectColumns(sql);
    } catch (Exception e) {
      return;
    }
    if (selectedCols == null || selectedCols.isEmpty()) {
      return;
    }
    // C6-4: the single pinned KYC-slot statement may project sys_user.kyc_attachment_1/2/3 for the
    // row owner or for a holder of system:user:edit. Every other statement (including all
    // MyBatis-Plus generated ones and all dict/low-code SQL) still goes through the whitelist
    // below, and SYS_USER_ALLOWED_COLUMNS is NOT widened.
    if (isKycSlotStatement(ms, boundSql, selectedCols)) {
      log.info("[SEC-C6-KYC-SLOT-ALLOW] uid={} ms={}", currentUsername(), ms.getId());
      return;
    }
    for (String col : selectedCols) {
      String clean = col == null ? "" : col.trim().toLowerCase();
      if (clean.isEmpty()) {
        continue;
      }
      int dot = clean.lastIndexOf('.');
      if (dot >= 0) {
        clean = clean.substring(dot + 1);
      }
      clean = clean.replace("`", "");
      if (clean.isEmpty()) {
        continue;
      }
      if (!SYS_USER_ALLOWED_COLUMNS.contains(clean)) {
        String uid = currentUsername();
        String audit =
            "[SEC-C6-COLUMN-BLOCK]"
                + " uid="
                + uid
                + " table=sys_user"
                + " col="
                + clean
                + " sql="
                + truncate(sql, 500);
        log.error(audit);
        alert(audit);
        throw new SQLException(C6_COLUMN_BLOCKED + ": sys_user." + clean);
      }
    }
  }

  private Set<String> extractSelectColumns(String sql) throws JSQLParserException {
    Set<String> out = new HashSet<>();
    Statement stmt = CCJSqlParserUtil.parse(sql);
    if (!(stmt instanceof Select)) {
      return out;
    }
    PlainSelect ps;
    try {
      ps = (PlainSelect) ((Select) stmt).getSelectBody();
    } catch (Exception e) {
      return out;
    }
    if (ps == null) {
      return out;
    }
    List<SelectItem> items = ps.getSelectItems();
    if (items == null) {
      return out;
    }
    for (SelectItem item : items) {
      if (item instanceof AllColumns) {
        // SELECT * — must stay blocked (resolves to every physical column).
        out.add("*");
      } else if (item instanceof AllTableColumns) {
        // SELECT a.* — same risk once the alias resolves to sys_user.
        out.add(((AllTableColumns) item).getTable().getName() + ".*");
      } else if (item instanceof SelectExpressionItem) {
        Expression expr = ((SelectExpressionItem) item).getExpression();
        if (expr == null) {
          continue;
        }
        // Collect ONLY real physical columns referenced by the expression. Output aliases
        // (e.g. username AS "text", id AS "value" used by dict translation) are result-set
        // labels and can never expose a column the underlying expression doesn't already
        // project — checking them produced false C6_COLUMN_BLOCKED hits. Function/case
        // expressions are descended so nested columns (CONCAT(a,b), CASE WHEN ...) are
        // still validated.
        expr.accept(
            new ExpressionVisitorAdapter() {
              @Override
              public void visit(Column column) {
                String name = column.getFullyQualifiedName();
                if (name != null && !name.isBlank()) {
                  out.add(name);
                }
              }

              @Override
              public void visit(SubSelect subSelect) {
                // Do not descend into scalar sub-selects: their projected columns belong to
                // other tables/scopes and must not be checked against sys_user columns.
              }
            });
      }
    }
    return out;
  }
}
