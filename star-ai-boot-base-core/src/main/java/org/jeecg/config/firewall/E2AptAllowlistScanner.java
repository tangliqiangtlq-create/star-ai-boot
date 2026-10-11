package org.jeecg.config.firewall;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class E2AptAllowlistScanner implements InitializingBean {

  private static final Pattern DOLLAR_VAR_PATTERN = Pattern.compile("\\$\\{\\s*([a-zA-Z0-9_.]+)\\s*\\}");

  private final List<SqlSessionFactory> sqlSessionFactories;

  @Autowired
  public E2AptAllowlistScanner(@Lazy List<SqlSessionFactory> sqlSessionFactories) {
    this.sqlSessionFactories = sqlSessionFactories == null ? new ArrayList<>() : sqlSessionFactories;
  }

  @Override
  public void afterPropertiesSet() {
    if (sqlSessionFactories == null || sqlSessionFactories.isEmpty()) {
      log.warn("E2AptAllowlistScanner: no SqlSessionFactory beans available, skip scan");
      return;
    }
    List<String> violations = new ArrayList<>();
    int scanned = 0;
    for (SqlSessionFactory sf : sqlSessionFactories) {
      Configuration cfg = sf.getConfiguration();
      Collection<MappedStatement> statements = cfg.getMappedStatements();
      for (Object msObj : statements) {
        if (!(msObj instanceof MappedStatement)) {
          continue;
        }
        MappedStatement ms = (MappedStatement) msObj;
        scanned++;
        String resource = ms.getResource();
        String msId = ms.getId();
        try {
          org.apache.ibatis.mapping.SqlSource src = ms.getSqlSource();
          if (src == null) {
            continue;
          }
          Object param = buildDefaultParameterObject(ms);
          org.apache.ibatis.mapping.BoundSql bound;
          try {
            bound = src.getBoundSql(param);
          } catch (Exception ignore) {
            continue;
          }
          if (bound == null) {
            continue;
          }
          String sql = bound.getSql();
          if (sql == null) {
            continue;
          }
          Matcher m = DOLLAR_VAR_PATTERN.matcher(sql);
          while (m.find()) {
            String varName = m.group(1);
            if (looksLikeTableVar(varName)) {
              violations.add("ms=" + msId + " resource=" + resource + " $" + varName + " TABLE_VAR_UNCHECKED");
            }
          }
        } catch (Exception ignore) {
        }
      }
    }
    if (!violations.isEmpty()) {
      StringBuilder sb = new StringBuilder(512);
      sb.append("E2AptAllowlistScanner detected ").append(violations.size()).append(" unallowlisted ${...} table refs:\n");
      for (String v : violations) {
        sb.append("  - ").append(v).append("\n");
      }
      String msg = sb.toString();
      log.error(msg);
      throw new RuntimeException(msg);
    }
    log.info(
        "E2AptAllowlistScanner: {} factories, {} MappedStatements scanned, 0 violations; E2TableAllowlist size={}",
        sqlSessionFactories.size(),
        scanned,
        E2TableAllowlist.ALLOWED_TABLES.size());
  }

  private static Object buildDefaultParameterObject(MappedStatement ms) {
    if (ms == null || ms.getParameterMap() == null) {
      return null;
    }
    Class<?> cls = ms.getParameterMap().getType();
    if (cls == null || cls == Object.class) {
      return null;
    }
    try {
      return cls.getDeclaredConstructor().newInstance();
    } catch (Exception ignore) {
      return null;
    }
  }

  private static boolean looksLikeTableVar(String varName) {
    if (varName == null) {
      return false;
    }
    String low = varName.toLowerCase();
    return low.equals("table")
        || low.equals("tablename")
        || low.endsWith(".table")
        || low.endsWith(".tablename")
        || low.contains("ew.customsqlsegment")
        || low.equals("query.table");
  }
}
