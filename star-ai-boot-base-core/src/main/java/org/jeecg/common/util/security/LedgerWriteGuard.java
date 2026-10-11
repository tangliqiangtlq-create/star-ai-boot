package org.jeecg.common.util.security;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.google.common.base.CaseFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jeecg.common.constant.SecurityRoleConstants;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.oss.OssTenantOwnerUtil;

@Slf4j
public final class LedgerWriteGuard {

  public static final String ADMIN_ROLE = "admin";
  public static final String OPS_ADMIN_ROLE = SecurityRoleConstants.ROLE_OPS_ADMIN;
  /** Level-1 agent: may approve/reject remittances for downstream users (not own). */
  public static final String AGENT1_ROLE = "agent1";
  /**
   * Checker under an agent: may approve/reject remittances in the same {@code agent_id} scope as
   * list ({@code sys_user.dlygx}); A5-3 still blocks self-approve. Not a ledger admin.
   */
  public static final String CHECKER_ROLE = "CHECKER";
  public static final List<String> LEDGER_ADMIN_ROLES = List.of(OPS_ADMIN_ROLE, ADMIN_ROLE);

  private static final Map<String, Map<String, Set<String>>> WHITELIST = new HashMap<>();

  static {
    registerField("dcs_withdrawal_record", "remark",            "*");
    registerField("dcs_withdrawal_record", "status",            OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_withdrawal_record", "reduce_amount",     OPS_ADMIN_ROLE, ADMIN_ROLE);

    registerField("dcs_transfer_record",   "remark",            "*");
    registerField("dcs_transfer_record",   "status",            OPS_ADMIN_ROLE, ADMIN_ROLE);

    registerField("dcs_remittance_record", "remark",            "*");
    registerField("dcs_remittance_record", "sender_binding_id", OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_remittance_record", "status",            OPS_ADMIN_ROLE, ADMIN_ROLE);

    registerField("dcs_bank_account",      "account_nickname",  "*");
    registerField("dcs_bank_account",      "status",            OPS_ADMIN_ROLE, ADMIN_ROLE);

    registerField("dcs_agent_payment_bill",              "pay_status",   OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_agent_payment_bill",              "remark",       "*");
    registerField("dcs_agent_payment_bill",              "update_by",    "*");
    registerField("dcs_agent_payment_bill",              "update_time",  "*");
    registerField("dcs_agent_payment_bill",              "audit_tag",    "*");

    registerField("dcs_currency",                        "icon",         "*");
    registerField("dcs_currency",                        "name",         "*");
    registerField("dcs_currency",                        "status",       OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_currency",                        "type",         OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_currency",                        "amount_scale", OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_currency",                        "remark",       "*");
    registerField("dcs_currency",                        "update_by",    "*");
    registerField("dcs_currency",                        "update_time",  "*");

    registerField("dcs_deposit_account_application_record", "status",    OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_deposit_account_application_record", "remark",    "*");
    registerField("dcs_deposit_account_application_record", "update_by", "*");
    registerField("dcs_deposit_account_application_record", "update_time", "*");
    registerField("dcs_deposit_account_application_record", "audit_tag", "*");

    registerField("dcs_deposit_address_pool",            "blockchain_id",        OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_deposit_address_pool",            "deposit_wallet_address", OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_deposit_address_pool",            "memo",                 "*");
    registerField("dcs_deposit_address_pool",            "remark",               "*");
    registerField("dcs_deposit_address_pool",            "status",               OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_deposit_address_pool",            "update_by",            "*");
    registerField("dcs_deposit_address_pool",            "update_time",          "*");
    registerField("dcs_deposit_address_pool",            "audit_tag",            "*");

    registerField("dcs_deposit_currency",                "account_type",         OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_deposit_currency",                "deposit_currency",     OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_deposit_currency",                "blockchain_id",        OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_deposit_currency",                "contract_address",     OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_deposit_currency",                "min_amount",           OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_deposit_currency",                "max_amount",           OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_deposit_currency",                "status",               OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_deposit_currency",                "remark",               "*");
    registerField("dcs_deposit_currency",                "update_by",            "*");
    registerField("dcs_deposit_currency",                "update_time",          "*");
    registerField("dcs_deposit_currency",                "audit_tag",            "*");

    registerField("dcs_banner",                          "image",                "*");
    registerField("dcs_banner",                          "orderNum",             OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_banner",                          "order_num",            OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_banner",                          "updateBy",             "*");
    registerField("dcs_banner",                          "update_by",            "*");
    registerField("dcs_banner",                          "updateTime",           "*");
    registerField("dcs_banner",                          "update_time",          "*");

    registerField("dcs_blockchain",                      "name",                 "*");
    registerField("dcs_blockchain",                      "code",                 "*");
    registerField("dcs_blockchain",                      "icon",                 "*");
    registerField("dcs_blockchain",                      "remark",               "*");
    registerField("dcs_blockchain",                      "updateBy",             "*");
    registerField("dcs_blockchain",                      "update_by",            "*");
    registerField("dcs_blockchain",                      "updateTime",           "*");
    registerField("dcs_blockchain",                      "update_time",          "*");

    // ===================== [CF-2 FIX] add 2 missing tables + 3 main-table REQUIRED gaps =====================
    // --- new table 1: dcs_currency_transaction_record (10 fields, C REQUIRED_FIELDS) ---
    registerField("dcs_currency_transaction_record", "status",            OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_currency_transaction_record", "remark",            "*");
    registerField("dcs_currency_transaction_record", "update_by",         "*");
    registerField("dcs_currency_transaction_record", "update_time",       "*");
    registerField("dcs_currency_transaction_record", "transaction_type",  OPS_ADMIN_ROLE);
    registerField("dcs_currency_transaction_record", "transaction_no",    OPS_ADMIN_ROLE);
    registerField("dcs_currency_transaction_record", "amount",            OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_currency_transaction_record", "currency",          OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_currency_transaction_record", "fee",               OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_currency_transaction_record", "amount_type",       OPS_ADMIN_ROLE);

    // --- new table 2: dcs_int_acc_transfer_rec (10 fields, C REQUIRED_FIELDS) ---
    registerField("dcs_int_acc_transfer_rec", "status",             OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_int_acc_transfer_rec", "remark",             "*");
    registerField("dcs_int_acc_transfer_rec", "update_by",          "*");
    registerField("dcs_int_acc_transfer_rec", "update_time",        "*");
    registerField("dcs_int_acc_transfer_rec", "in_amount",          OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_int_acc_transfer_rec", "actual_amount",      OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_int_acc_transfer_rec", "total_fee",          OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_int_acc_transfer_rec", "src_currency",       OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_int_acc_transfer_rec", "dst_currency",       OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_int_acc_transfer_rec", "transaction_no",     OPS_ADMIN_ROLE);

    // --- dcs_withdrawal_record gap: 8 fields (C REQUIRED_FIELDS_FOR_THIS_TABLE 10 = 3 existing + 7 new) ---
    registerField("dcs_withdrawal_record", "amount",              OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_withdrawal_record", "actual_amount",       OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_withdrawal_record", "currency",            OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_withdrawal_record", "total_fee",           OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_withdrawal_record", "transaction_no",      OPS_ADMIN_ROLE);
    registerField("dcs_withdrawal_record", "process_instance_id", OPS_ADMIN_ROLE);
    ensureFieldIfAbsent("dcs_withdrawal_record", "update_by",   "*");
    ensureFieldIfAbsent("dcs_withdrawal_record", "update_time", "*");

    // --- dcs_transfer_record gap: 8 fields (C REQUIRED_FIELDS 10 = 2 existing + 8 new) ---
    registerField("dcs_transfer_record", "amount",                OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_transfer_record", "actual_amount",         OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_transfer_record", "currency",              OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_transfer_record", "total_fee",             OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_transfer_record", "transaction_no",        OPS_ADMIN_ROLE);
    registerField("dcs_transfer_record", "process_instance_id",   OPS_ADMIN_ROLE);
    ensureFieldIfAbsent("dcs_transfer_record", "update_by",   "*");
    ensureFieldIfAbsent("dcs_transfer_record", "update_time", "*");

    // --- dcs_remittance_record gap: 10 fields (C REQUIRED_FIELDS 10 = 3 existing + 10 new, incl. src/dst_currency) ---
    registerField("dcs_remittance_record", "amount",              OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_remittance_record", "actual_amount",       OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_remittance_record", "currency",            OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_remittance_record", "total_fee",           OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_remittance_record", "transaction_no",      OPS_ADMIN_ROLE);
    registerField("dcs_remittance_record", "process_instance_id", OPS_ADMIN_ROLE);
    registerField("dcs_remittance_record", "src_currency",        OPS_ADMIN_ROLE, ADMIN_ROLE);
    registerField("dcs_remittance_record", "dst_currency",        OPS_ADMIN_ROLE, ADMIN_ROLE);
    ensureFieldIfAbsent("dcs_remittance_record", "update_by",   "*");
    ensureFieldIfAbsent("dcs_remittance_record", "update_time", "*");
  }

  private LedgerWriteGuard() {}

  private static void registerField(String table, String field, String... roles) {
    WHITELIST
        .computeIfAbsent(table, k -> new HashMap<>())
        .put(field, roles == null || roles.length == 0 ? Set.of("*") : new HashSet<>(Arrays.asList(roles)));
  }

  private static void ensureFieldIfAbsent(String table, String field, String... roles) {
    Map<String, Set<String>> tableFields = WHITELIST.get(table);
    if (tableFields == null) {
      registerField(table, field, roles);
      return;
    }
    if (!tableFields.containsKey(field)) {
      registerField(table, field, roles);
    }
  }

  public static boolean isLedgerAdmin() {
    return hasAnyRole(LEDGER_ADMIN_ROLES);
  }

  public static void assertLedgerAdmin(String action) {
    if (!isLedgerAdmin()) {
      log.warn("[LEDGER-WRITE] caller={} not ledger admin, blocked action={}", StpUtil.getLoginIdDefaultNull(), action);
      // Anonymous -> 401, authenticated without role -> 403 (拒绝码统一标准).
      throw Rejections.unauthorizedOrForbidden(I18nUtil.getMessage("common.error.unauthorized"));
    }
  }

  /**
   * Ledger admins, or any of the extra roles (e.g. agent1 for remittance workflow approve/reject).
   * Callers must still enforce A5-3 self-approve / self-reject checks separately.
   */
  public static void assertLedgerAdminOrRoles(String action, String... extraRoles) {
    if (isLedgerAdmin()) {
      return;
    }
    if (extraRoles != null && extraRoles.length > 0 && hasAnyRole(Arrays.asList(extraRoles))) {
      return;
    }
    log.warn(
        "[LEDGER-WRITE] caller={} not ledger admin nor extraRoles={}, blocked action={}",
        StpUtil.getLoginIdDefaultNull(),
        extraRoles == null ? List.of() : Arrays.asList(extraRoles),
        action);
    throw Rejections.unauthorizedOrForbidden(I18nUtil.getMessage("common.error.unauthorized"));
  }

  public static <T> void assertOwnerOrLedgerAdmin(
      T entity, OwnerOrAdminGuard.BucketOwnerResolver<T> resolver, String bucket) {
    OwnerOrAdminGuard.assertOwnerOrAdmin(entity, resolver, bucket, OssTenantOwnerUtil.FILE_ADMIN_ROLES);
  }

  public static void assertFieldWhitelisted(String table, String field) {
    if (oConvertUtils.isEmpty(table) || oConvertUtils.isEmpty(field)) {
      throw Rejections.forbidden(I18nUtil.getMessage("common.error.unauthorized"));
    }
    Map<String, Set<String>> tableFields = WHITELIST.get(table.toLowerCase(Locale.ROOT));
    if (tableFields == null) {
      log.warn("[LEDGER-WRITE] table={} not whitelisted, blocked field={}", table, field);
      throw Rejections.forbidden(I18nUtil.getMessage("common.error.unauthorized"));
    }
    Set<String> allowedRoles = tableFields.get(field);
    if (allowedRoles == null) {
      log.warn("[LEDGER-WRITE] field={}.{} NOT on whitelist", table, field);
      throw Rejections.forbidden(I18nUtil.getMessage("common.error.unauthorized"));
    }
    if (allowedRoles.contains("*")) {
      return;
    }
    if (hasAnyRole(allowedRoles)) {
      return;
    }
    log.warn(
        "[LEDGER-WRITE] caller={} has none of allowed roles={} for field={}.{}",
        StpUtil.getLoginIdDefaultNull(), allowedRoles, table, field);
    throw Rejections.unauthorizedOrForbidden(I18nUtil.getMessage("common.error.unauthorized"));
  }

  private static final Set<String> FIELD_MPGENVAL_PREFIXES =
      Set.of("MPGENVAL_", "mpgenval_", "MP_");

  public static <T> void assertUpdateWrapperFields(
      String table, UpdateWrapper<T> updateWrapper, String... extraAllowedFields) {
    if (updateWrapper == null) {
      return;
    }
    Set<String> fieldsInSql = extractSqlSetColumns(updateWrapper);
    fieldsInSql.removeIf(f -> FIELD_MPGENVAL_PREFIXES.stream().anyMatch(f::startsWith));
    Set<String> extra =
        extraAllowedFields == null ? Collections.emptySet() : new HashSet<>(Arrays.asList(extraAllowedFields));
    for (String f : fieldsInSql) {
      if (extra.contains(f)) continue;
      assertFieldWhitelisted(table, f);
    }
  }

  public static <T> void assertLambdaUpdateWrapperFields(
      String table, LambdaUpdateWrapper<T> lambdaUw, String... extraAllowedFields) {
    if (lambdaUw == null) {
      return;
    }
    // C3 inspector returns an unmodifiable set (its contract); copy into a mutable set before
    // stripping MPGENVAL prefixes, otherwise removeIf throws UnsupportedOperationException.
    // The MPGENVAL strip + per-field whitelist assert below are unchanged (F6 gate intact).
    Set<String> fieldsInSql = new HashSet<>(C3FieldReflectionInspector.extractSetFieldNames(lambdaUw));
    fieldsInSql.removeIf(f -> f != null && FIELD_MPGENVAL_PREFIXES.stream().anyMatch(f::startsWith));
    // [CF-2 FIX] Normalize camelCase → snake_case for C3 mixed-set (camel + snake) compatibility
    Set<String> normalized = new HashSet<>(fieldsInSql.size());
    for (String raw : fieldsInSql) {
      if (raw == null) continue;
      if (raw.contains("_")) {
        normalized.add(raw);
      } else {
        try {
          normalized.add(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, raw));
        } catch (Exception e) {
          normalized.add(raw);
        }
      }
    }
    fieldsInSql = normalized;
    Set<String> extra =
        extraAllowedFields == null ? Collections.emptySet() : new HashSet<>(Arrays.asList(extraAllowedFields));
    for (String f : fieldsInSql) {
      if (extra.contains(f)) continue;
      assertFieldWhitelisted(table, f);
    }
  }

  /** VisibleForTesting: expose prefix set size for NFR verifiers. */
  public static int fieldMpGenValPrefixCount() {
    return FIELD_MPGENVAL_PREFIXES.size();
  }

  private static <T> Set<String> extractSqlSetColumns(UpdateWrapper<T> uw) {
    Set<String> result = new HashSet<>();
    try {
      var paramNameValuePairs = uw.getParamNameValuePairs();
      if (paramNameValuePairs != null) {
        for (Object key : paramNameValuePairs.keySet()) {
          if (key == null) continue;
          result.add(String.valueOf(key));
        }
      }
      String sqlSet = uw.getSqlSet();
      if (oConvertUtils.isNotEmpty(sqlSet)) {
        for (String part : sqlSet.split(",")) {
          String trimmed = part.trim();
          int eq = trimmed.indexOf('=');
          if (eq > 0) {
            String col = trimmed.substring(0, eq).trim().replace("`", "");
            result.add(col);
          }
        }
      }
    } catch (Exception e) {
      log.warn("[LEDGER-WRITE] UpdateWrapper field extraction failed: {}", e.getMessage());
    }
    return result;
  }

  private static boolean hasAnyRole(Iterable<String> roles) {
    if (roles == null) {
      return false;
    }
    java.util.List<String> list = new java.util.ArrayList<>();
    for (String r : roles) {
      list.add(r);
    }
    return RoleCacheHelper.hasAnyRole(list);
  }
}
