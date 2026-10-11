package org.jeecg.config.firewall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * C6-4 regression: the pinned KYC-slot statement carve-out must stay surgical.
 *
 * <p>Contract pinned here:
 *
 * <ul>
 *   <li>the pinned statement set contains exactly one statement id:
 *       {@code org.jeecg.modules.system.mapper.SysUserMapper.selectKycSlotsById};
 *   <li>the projection pin accepts ONLY the four reviewed columns
 *       ({@code id, kyc_attachment_1, kyc_attachment_2, kyc_attachment_3}) — any widening, any
 *       {@code SELECT *}, and any table-qualified star drop out of the carve-out;
 *   <li>{@code kyc_attachment_1/2/3} are NOT added to the global {@code SYS_USER_ALLOWED_COLUMNS}
 *       whitelist, i.e. the gate itself was not widened (L27 explicitly requires those columns to
 *       stay excluded and KYC pages to route through dedicated Mappers).
 * </ul>
 *
 * <p>Reads the private constants reflectively, mirroring
 * {@link SensitiveTableBlacklistColumnExtractionTest}'s approach of exercising the pure SQL-analysis
 * contract without a Spring context or a Sa-Token session.
 */
class SensitiveTableBlacklistKycSlotPinTest {

  private static final String EXPECTED_STATEMENT_ID =
      "org.jeecg.modules.system.mapper.SysUserMapper.selectKycSlotsById";

  private static final Set<String> REVIEWED_PROJECTION =
      new HashSet<>(
          Arrays.asList("id", "kyc_attachment_1", "kyc_attachment_2", "kyc_attachment_3"));

  @SuppressWarnings("unchecked")
  private static Set<String> pinnedStatementIds() throws Exception {
    Field f =
        SensitiveTableBlacklistInterceptor.class.getDeclaredField("SYS_USER_KYC_SLOT_MAPPER_IDS");
    f.setAccessible(true);
    return (Set<String>) f.get(null);
  }

  @SuppressWarnings("unchecked")
  private static Set<String> pinnedColumns() throws Exception {
    Field f =
        SensitiveTableBlacklistInterceptor.class.getDeclaredField("SYS_USER_KYC_SLOT_COLUMNS");
    f.setAccessible(true);
    return (Set<String>) f.get(null);
  }

  @SuppressWarnings("unchecked")
  private static Set<String> sysUserAllowedColumns() throws Exception {
    Field f =
        SensitiveTableBlacklistInterceptor.class.getDeclaredField("SYS_USER_ALLOWED_COLUMNS");
    f.setAccessible(true);
    return (Set<String>) f.get(null);
  }

  private static boolean projectionPinned(Set<String> cols) throws Exception {
    Method m =
        SensitiveTableBlacklistInterceptor.class.getDeclaredMethod(
            "isKycSlotProjectionPinned", Set.class);
    m.setAccessible(true);
    return (Boolean) m.invoke(null, cols);
  }

  private static Set<String> extractColumns(String sql) throws Exception {
    SensitiveTableBlacklistInterceptor interceptor = new SensitiveTableBlacklistInterceptor();
    Method m =
        SensitiveTableBlacklistInterceptor.class.getDeclaredMethod(
            "extractSelectColumns", String.class);
    m.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<String> cols = (Set<String>) m.invoke(interceptor, sql);
    return cols;
  }

  @Test
  @DisplayName("Exactly one pinned statement id, matching SysUserMapper.selectKycSlotsById")
  void pinnedStatementIdIsExact() throws Exception {
    Set<String> ids = pinnedStatementIds();
    assertEquals(1, ids.size(), "carve-out must stay pinned to a single statement: " + ids);
    assertTrue(ids.contains(EXPECTED_STATEMENT_ID), "unexpected pinned id: " + ids);
  }

  @Test
  @DisplayName("Pinned column set is exactly the four KYC slots")
  void pinnedColumnSetIsExact() throws Exception {
    assertEquals(REVIEWED_PROJECTION, pinnedColumns(), "reviewed projection changed");
  }

  @Test
  @DisplayName("Reviewed KYC projection passes the pin")
  void reviewedProjectionPasses() throws Exception {
    assertTrue(projectionPinned(REVIEWED_PROJECTION));
  }

  @Test
  @DisplayName("Selecting one extra sensitive column drops out of the carve-out")
  void widenedProjectionIsRefused() throws Exception {
    Set<String> withPassword = new HashSet<>(REVIEWED_PROJECTION);
    withPassword.add("password");
    assertFalse(projectionPinned(withPassword), "password must not ride along");

    Set<String> withSalt = new HashSet<>(REVIEWED_PROJECTION);
    withSalt.add("salt");
    assertFalse(projectionPinned(withSalt), "salt must not ride along");
  }

  @Test
  @DisplayName("SELECT * and table-qualified star are still refused by the pin")
  void starProjectionsRefused() throws Exception {
    assertFalse(projectionPinned(new HashSet<>(Arrays.asList("*"))));
    assertFalse(projectionPinned(new HashSet<>(Arrays.asList("u.*"))));
    assertFalse(projectionPinned(new HashSet<>(Arrays.asList("id", "*"))));
    assertFalse(projectionPinned(new HashSet<>()));
    assertFalse(projectionPinned(null));
  }

  @Test
  @DisplayName("The real pinned statement SQL extracts to exactly the pinned projection")
  void pinnedStatementSqlMatchesPin() throws Exception {
    // The same @Select text SysUserMapper declares, run through the gate's own extractor, must
    // yield exactly the reviewed set — tying the mapper SQL to the pin so a later widening fails.
    Set<String> cols =
        extractColumns(
            "SELECT id, kyc_attachment_1, kyc_attachment_2, kyc_attachment_3 FROM sys_user"
                + " WHERE id = ? AND del_flag = 0");

    assertEquals(REVIEWED_PROJECTION, cols, "extracted projection drifted from the pin");
    assertTrue(projectionPinned(cols));
  }

  @Test
  @DisplayName("Table-qualified form of the pinned projection is also accepted")
  void tableQualifiedProjectionAccepted() throws Exception {
    Set<String> cols =
        extractColumns(
            "SELECT u.id, u.kyc_attachment_1, u.kyc_attachment_2, u.kyc_attachment_3"
                + " FROM sys_user u WHERE u.id = ? AND u.del_flag = 0");
    assertTrue(projectionPinned(cols), "normalization must strip the qualifier: " + cols);
  }

  @Test
  @DisplayName("Global SYS_USER_ALLOWED_COLUMNS was NOT widened with KYC slots")
  void globalWhitelistNotWidened() throws Exception {
    Set<String> allowed = sysUserAllowedColumns();
    for (String kycCol : REVIEWED_PROJECTION) {
      if ("id".equals(kycCol)) {
        continue;
      }
      assertFalse(
          allowed.contains(kycCol),
          kycCol + " must stay out of the global whitelist (L27: excluded, use dedicated Mappers)");
    }
  }

  @Test
  @DisplayName("Extractor still reports a foreign sensitive projection (gate not weakened)")
  void foreignProjectionStillSensitive() throws Exception {
    Set<String> cols = extractColumns("SELECT password, salt FROM sys_user WHERE id = ?");
    assertTrue(cols.contains("password"), "password must still be extractable for gating: " + cols);
    assertFalse(projectionPinned(cols), "pin must not admit credential projections");
  }
}
