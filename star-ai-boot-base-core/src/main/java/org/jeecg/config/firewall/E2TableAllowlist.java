package org.jeecg.config.firewall;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class E2TableAllowlist {

  public static final Set<String> ALLOWED_TABLES;

  static {
    Set<String> s = new HashSet<>(Arrays.asList(
        // ==== System dict / permission / role core (low sensitivity) ====
        "sys_dict",
        "sys_dict_item",
        "sys_permission",
        "sys_role",
        "sys_role_permission",
        "sys_depart",
        "sys_depart_role",
        "sys_depart_role_permission",
        "sys_depart_role_user",
        "sys_user_depart",
        "sys_user_role",
        "sys_user_position",
        "sys_position",
        "sys_category",
        "sys_announcement",
        "sys_announcement_send",
        "sys_sms",
        "sys_sms_template",
        "sys_data_log",
        "sys_log",
        "sys_file",
        "sys_form_file",
        "sys_check_rule",
        "sys_fill_rule",
        "sys_duplicate_check",
        "sys_user",
        "sys_user_agent",
        "sys_user_tenant",
        "sys_tenant",
        "sys_state_definition",
        "sys_process_definition",
        "sys_tenant_package",
        "sys_tenant_permission",
        "sys_depart_permission",
        "sys_gateway_route",
        "sys_data_source",
        "sys_quartz_job",
        "sys_quartz_log",
        "sys_ip_blacklist",
        "sys_pack_permission",
        "sys_low_app",
        "sys_low_app_depart",
        "sys_low_app_permission",
        "sys_message",
        "sys_message_template",
        "sys_message_depart",
        "sys_message_permission",
        "portal_team_role_def",
        "portal_team_member",
        "portal_team_role_member",
        "user_label",
        "user_invite_code",
        "sys_event_definition",
        "sys_event_subscribe",
        // ==== DCS non-sensitive lookup tables ====
        "dcs_currency",
        "dcs_country",
        "dcs_bank_code",
        "dcs_payment_method",
        "dcs_exchange_rate"
    ));
    ALLOWED_TABLES = Collections.unmodifiableSet(s);
  }

  private E2TableAllowlist() {
  }

  public static boolean isAllowed(String tableName) {
    if (tableName == null) {
      return false;
    }
    String normalized = tableName.trim().toLowerCase();
    return ALLOWED_TABLES.contains(normalized);
  }
}
