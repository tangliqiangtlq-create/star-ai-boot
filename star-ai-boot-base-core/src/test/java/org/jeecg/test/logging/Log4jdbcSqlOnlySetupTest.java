package org.jeecg.test.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.sf.log4jdbc.SpyLogFactory;
import org.jeecg.common.logging.Log4jdbcSqlOnlySetup;
import org.jeecg.common.logging.SqlOnlySpyLogDelegator;
import org.junit.jupiter.api.Test;

class Log4jdbcSqlOnlySetupTest {

  @Test
  void installReplacesSpyLogDelegator() {
    Log4jdbcSqlOnlySetup.install();
    assertEquals(SqlOnlySpyLogDelegator.class, SpyLogFactory.getSpyLogDelegator().getClass());
  }
}
