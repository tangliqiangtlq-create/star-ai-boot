package org.jeecg.config.firewall;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * B4-3: bare {@code ;} in free text (AutoLog / exception messages) must not be treated as SQLi;
 * multi-statement {@code ; drop}/{@code ; select} must still match.
 */
class DangerousSqlParamSemicolonTest {

  /** Same pattern as {@link DangerousSqlStringInspector#MULTI_STMT_SEMICOLON}. */
  private static final Pattern MULTI_STMT_SEMICOLON =
      Pattern.compile(";\\s*[a-zA-Z_\\u4e00-\\u9fa5\"'`]");

  @Test
  void englishProseSemicolonIsNotMultiStatement() {
    assertFalse(MULTI_STMT_SEMICOLON.matcher("empty; confirm xxljob service".toLowerCase()).find());
  }

  @Test
  void stackedStatementSemicolonIsMultiStatement() {
    assertTrue(MULTI_STMT_SEMICOLON.matcher("1; drop table sys_user".toLowerCase()).find());
    assertTrue(MULTI_STMT_SEMICOLON.matcher("x';select 1".toLowerCase()).find());
  }
}
