package org.jeecg.common.gates;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class LGateG23RolePermissionCrossTest {

  private static final Path V1_0_92_SQL =
      Paths.get("docs/db/migrations/mysql/V1.0.92__seed_security_GHCJ_new_permissions.sql");

  @Test
  void v1_0_92_button_permissions_ExactlyEight() throws IOException {
    String sql = new String(Files.readAllBytes(V1_0_92_SQL), StandardCharsets.UTF_8);
    Pattern buttonPerm = Pattern.compile("menu_type.,\\s*2");
    Matcher m = buttonPerm.matcher(sql);
    int buttons = 0;
    while (m.find()) buttons++;
    assertEquals(8, buttons,
        "V1.0.92 sys_permission INSERT must define exactly 8 menu_type=2 button perms "
            + "(G 2FA×2 / H audit×2 / C funds×3 / J ops×1); AUT-03 rg≥8, JUnit static SQL recheck");
  }

  @Test
  void admin_role_grant_clause_EightPerms() throws IOException {
    String sql = new String(Files.readAllBytes(V1_0_92_SQL), StandardCharsets.UTF_8);
    Pattern adminGrant = Pattern.compile("WHERE\\s+r\\.role_code\\s*=\\s*'admin'[\\s\\S]*?NOT\\s+EXISTS");
    Matcher m = adminGrant.matcher(sql);
    String block = "";
    if (m.find()) block = sql.substring(m.start(), Math.min(sql.length(), m.end() + 400));
    Pattern permItem = Pattern.compile("'[A-Za-z0-9_:*]+'");
    Matcher pm = permItem.matcher(block);
    int perms = 0;
    while (pm.find()) {
      String v = pm.group();
      if (v.contains(":") && (v.startsWith("'*") || v.startsWith("'dcs:") || v.startsWith("'ops:"))) perms++;
    }
    assertEquals(8, perms,
        "V1.0.92 admin sys_role_permission INSERT subquery perms IN list must be 8 entries "
            + "matching button permissions (100% cross-seed); AUT-03 recheck");
  }
}
