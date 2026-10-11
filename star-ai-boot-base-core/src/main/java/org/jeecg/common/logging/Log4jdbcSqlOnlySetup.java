package org.jeecg.common.logging;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import net.sf.log4jdbc.SpyLogDelegator;
import net.sf.log4jdbc.SpyLogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Replace SpyLogDelegator before log4jdbc Driver loads, keeping only SQL logs. */
public final class Log4jdbcSqlOnlySetup {

  private static final Logger log = LoggerFactory.getLogger(Log4jdbcSqlOnlySetup.class);

  private Log4jdbcSqlOnlySetup() {}

  public static void install() {
    try {
      replaceDelegator(new SqlOnlySpyLogDelegator());
      log.debug("Installed SqlOnlySpyLogDelegator");
    } catch (Exception ex) {
      // JDK 17+ prohibits reflection overwriting static final; logback has been configured with SQL-only, and is only optimized here.
      log.debug(
          "SqlOnlySpyLogDelegator not installed, relying on logback SQL-only config: {}",
          ex.toString());
    }
  }

  private static void replaceDelegator(SpyLogDelegator delegator) throws ReflectiveOperationException {
    Class<?> factoryClass = Class.forName(SpyLogFactory.class.getName());
    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(factoryClass, MethodHandles.lookup());
    VarHandle varHandle =
        lookup.findStaticVarHandle(factoryClass, "logger", SpyLogDelegator.class);
    varHandle.set(delegator);
  }

  public static SpyLogDelegator currentDelegator() {
    return SpyLogFactory.getSpyLogDelegator();
  }
}
