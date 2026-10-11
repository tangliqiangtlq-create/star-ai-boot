package org.jeecg.common.logging;

import net.sf.log4jdbc.Spy;
import net.sf.log4jdbc.Slf4jSpyLogDelegator;

/** log4jdbc delegate: only output SQL, shielding audit/connection/resultset/sqltiming and other noise. */
public class SqlOnlySpyLogDelegator extends Slf4jSpyLogDelegator {

  @Override
  public void methodReturned(Spy spy, String methodCall, String returnMsg) {
    // no-op: jdbc.audit / jdbc.resultset
  }

  @Override
  public void connectionOpened(Spy spy) {
    // no-op: jdbc.connection
  }

  @Override
  public void connectionClosed(Spy spy) {
    // no-op: jdbc.connection
  }

  @Override
  public void sqlTimingOccured(Spy spy, long timeElapsed, String methodCall, String sql) {
    // no-op: jdbc.sqltiming
  }

  @Override
  public void debug(String msg) {
    // no-op: log4jdbc.debug
  }
}
