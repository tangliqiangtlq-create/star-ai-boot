package org.jeecg.config.firewall;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression: the C6 column gate must judge the columns an expression actually projects, not the
 * result-set alias.
 *
 * <p>The dict translation issued by {@code AutoLogAspect} /
 * {@code commonAPI.translateDictFromTable("sys_user", "username", "id", loginId)} compiles to
 *
 * <pre>SELECT username AS "text", id AS "value" FROM sys_user WHERE id IN (?) LIMIT 1024</pre>
 *
 * <p>An earlier version of {@link SensitiveTableBlacklistInterceptor#extractSelectColumns} added
 * the output aliases {@code text} / {@code value} to the column set. They are not
 * {@code SYS_USER_ALLOWED_COLUMNS}, so every non-admin audit write was rejected with
 * {@code [SEC-C6-COLUMN-BLOCK] col="text"} → {@code C6_COLUMN_BLOCKED: sys_user."text"}. The
 * system service answered with a Result error envelope, {@code sys_log.username} received that
 * envelope, and the audit row either silently held error JSON or failed outright with
 * {@code MysqlDataTruncation: Data too long for column 'username'}.
 *
 * <p>These tests pin the extraction contract so the alias handling cannot regress: real columns in,
 * aliases ignored, and {@code SELECT *} still refused.
 */
class SensitiveTableBlacklistColumnExtractionTest {

  private static final String DICT_TRANSLATION_SQL =
      "SELECT username AS \"text\", id AS \"value\" FROM sys_user WHERE id IN (?) LIMIT 1024";

  /**
   * {@code extractSelectColumns} is private; the interceptor is not reachable without a Spring
   * context and a Sa-Token session, so the pure SQL-analysis step is invoked directly.
   */
  private static Set<String> extractColumns(String sql) throws Exception {
    SensitiveTableBlacklistInterceptor interceptor =
        new SensitiveTableBlacklistInterceptor();
    Method m = SensitiveTableBlacklistInterceptor.class.getDeclaredMethod(
        "extractSelectColumns", String.class);
    m.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<String> cols = (Set<String>) m.invoke(interceptor, sql);
    return cols;
  }

  @Test
  @DisplayName("Dict translation: aliases are not treated as columns (was col=\"text\")")
  void dictTranslationAliasesAreNotColumns() throws Exception {
    Set<String> cols = extractColumns(DICT_TRANSLATION_SQL);

    assertTrue(cols.contains("username"), "real column username must be collected: " + cols);
    assertTrue(cols.contains("id"), "real column id must be collected: " + cols);
    assertFalse(cols.contains("text"), "alias must not be collected: " + cols);
    assertFalse(cols.contains("\"text\""), "quoted alias must not be collected: " + cols);
    assertFalse(cols.contains("value"), "alias must not be collected: " + cols);
    assertFalse(cols.contains("\"value\""), "quoted alias must not be collected: " + cols);
  }

  @Test
  @DisplayName("Explicit sensitive column is still collected (gate not weakened)")
  void sensitiveColumnStillCollected() throws Exception {
    Set<String> cols =
        extractColumns("SELECT id, username, reason FROM sys_user WHERE id = ?");
    assertTrue(cols.contains("reason"), "reason must still be gated: " + cols);
  }

  @Test
  @DisplayName("SELECT * is still refused")
  void selectStarStillRefused() throws Exception {
    Set<String> cols = extractColumns("SELECT * FROM sys_user WHERE username = ? AND del_flag = 0");
    assertTrue(cols.contains("*"), "SELECT * must stay blocked: " + cols);
  }

  @Test
  @DisplayName("Table-qualified star is still refused")
  void selectTableStarStillRefused() throws Exception {
    Set<String> cols = extractColumns("SELECT u.* FROM sys_user u WHERE u.id = ?");
    assertTrue(cols.contains("u.*"), "SELECT u.* must stay blocked: " + cols);
  }

  @Test
  @DisplayName("Aliased real column is resolved to the underlying column")
  void aliasedRealColumnResolvedToUnderlying() throws Exception {
    // create_time AS created must be judged as create_time (allowed), not "created".
    Set<String> cols = extractColumns("SELECT create_time AS created FROM sys_user WHERE id = ?");
    assertTrue(cols.contains("create_time"), "underlying column expected: " + cols);
    assertFalse(cols.contains("created"), "alias must not be collected: " + cols);
  }

  @Test
  @DisplayName("Expressions descend into their nested columns")
  void expressionsDescendIntoColumns() throws Exception {
    Set<String> cols =
        extractColumns("SELECT CONCAT(username, realname) AS display FROM sys_user WHERE id = ?");
    assertTrue(cols.contains("username"), "nested username expected: " + cols);
    assertTrue(cols.contains("realname"), "nested realname expected: " + cols);
    assertFalse(cols.contains("display"), "alias must not be collected: " + cols);
  }
}
