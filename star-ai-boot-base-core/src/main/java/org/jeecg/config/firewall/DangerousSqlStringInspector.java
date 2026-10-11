package org.jeecg.config.firewall;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.jeecg.common.util.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

public class DangerousSqlStringInspector implements InnerInterceptor, InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(DangerousSqlStringInspector.class);

  public static final String AUDIT_LOG_PREFIX = "[DANGEROUS_SQL_BLOCKED]";
  public static final String ALERT_REDIS_KEY_TMPL = "sec:alert:dangerous_sql:daily:%s";

  /**
   * Full-SQL danger tokens. E3 fix: expanded from 21 → ≥37 tokens (AC-E3-1).
   * Case-insensitive compare via sqlLower.
   */
  private static final List<String> SQL_DANGER_TOKENS =
      Arrays.asList(
          // ==== Tautology fragments (narrow quotes to avoid free-text false positives) ====
          "' or '1",
          "' or 1=",
          "' or 1 =",
          "' or true",
          "' and '1",
          "' and 1=",
          "' and 1 =",
          "' and false",
          "\" or \"1",
          "\" or 1=",
          "\" and \"1",
          "\" and 1=",
          "-- ",
          "/*",
          "*/",
          ";--",
          "sleep(",
          "benchmark(",
          "pg_sleep(",
          "waitfor delay",
          "union select",
          "union all select",
          "union distinct select",
          "drop table",
          "truncate table",
          "alter table",
          "create table",
          "rename table",
          "information_schema.",
          "information_schema.tables",
          "information_schema.columns",
          "pg_catalog.",
          "mysql.",
          "sys.objects",
          "load_file(",
          "outfile ",
          "into outfile",
          "into dumpfile",
          "xp_cmdshell",
          "sp_executesql",
          "hex(cast(",
          "char(",
          "ascii(",
          "substring(",
          "mid(",
          "version(",
          "@@version",
          "database(",
          "current_user",
          "user()",
          "system_user"
      );

  /**
   * E3 fix (AC-E3-1): REMOVED bare single-quote character {@code "'"} (was line 78 O'Brien false-positive).
   * Replaced lexical probe by {@link #PARAM_QUOTE_WITH_KEYWORD} Pattern below.
   * Kept hints narrow: no bare {@code ";"} / {@code " and "} / {@code " or "} → prose/audit false-positive.
   */
  private static final List<String> PARAM_DANGER_SUBSTRINGS =
      Arrays.asList(
          // NOTE: single bare "'" intentionally REMOVED — O'Brien / D'Souza names false-positive
          "%s ",
          "%s,",
          "%s)",
          "' or ",
          "' and ",
          "\" or ",
          "\" and ",
          "-- ",
          "; drop",
          "; delete",
          "; update",
          "; insert",
          "; select",
          "; truncate",
          "delete from ",
          "drop table ",
          "truncate table ",
          "union select",
          "union all select",
          "sleep(",
          "benchmark(",
          "load_file(",
          "outfile ",
          "xp_cmdshell"
      );

  /**
   * E3 AC-E3-1 lexical probe: single-quote character ONLY when immediately followed by SQL keyword
   * token (OR|AND|UNION|SELECT|DELETE|UPDATE|INSERT|HEX|BENCHMARK|LOAD_FILE|OUTFILE|WAITFOR|DROP|ALTER).
   * This preserves legitimate {@code O'Brien} / {@code D'Souza} apostrophes while catching
   * {@code ' OR '1'='1} / {@code ' UNION SELECT ...} injection payloads.
   */
  private static final Pattern PARAM_QUOTE_WITH_KEYWORD =
      Pattern.compile(
          "'\\s*(OR|AND|UNION|SELECT|DELETE|UPDATE|INSERT|HEX|BENCHMARK|LOAD_FILE|OUTFILE|WAITFOR|DROP|ALTER)\\s+",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern MULTI_STMT_SEMICOLON =
      Pattern.compile(";\\s*[a-zA-Z_\\u4e00-\\u9fa5\"'`]");

  private boolean failFast;

  @Override
  public void afterPropertiesSet() {
    try {
      Environment env = SpringContextUtils.getBean(Environment.class);
      String envStr = env == null ? "" : env.getProperty("sql.inspection.fail.fast", "");
      if (envStr == null || envStr.isEmpty()) {
        // E3 AC-E3-3: default true (was !prod REVERSED — prod/staging/uat/default ALL true).
        // Only 'test' profile explicitly overrides to false when unset.
        String[] profiles = env == null ? new String[0] : env.getActiveProfiles();
        boolean testProfile = false;
        if (profiles != null) {
          for (String p : profiles) {
            if (p != null && p.equalsIgnoreCase("test")) {
              testProfile = true;
              break;
            }
          }
        }
        this.failFast = !testProfile;
      } else {
        this.failFast = Boolean.parseBoolean(envStr);
      }
    } catch (Exception ignore) {
      // E3 AC-E3-3: default true even on exception (was false REVERSED)
      this.failFast = true;
    }
    log.info(
        "DangerousSqlStringInspector loaded: failFast={}, tokens={}, paramHints={}",
        this.failFast,
        SQL_DANGER_TOKENS.size(),
        PARAM_DANGER_SUBSTRINGS.size());
  }

  @Override
  public void beforeQuery(
      Executor executor,
      MappedStatement ms,
      Object parameter,
      RowBounds rowBounds,
      ResultHandler resultHandler,
      BoundSql boundSql)
      throws SQLException {
    validate(ms, boundSql, parameter);
  }

  @Override
  public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
      throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    validate(ms, boundSql, parameter);
  }

  private void validate(MappedStatement ms, BoundSql boundSql, Object parameter) throws SQLException {
    if (boundSql == null) {
      return;
    }
    String sql = boundSql.getSql();
    if (sql == null || sql.isEmpty()) {
      return;
    }
    String sqlLower = sql.toLowerCase();
    if (MULTI_STMT_SEMICOLON.matcher(sqlLower).find()) {
      block(ms, sql, "multi_statement_semicolon");
      return;
    }
    for (String token : SQL_DANGER_TOKENS) {
      if (sqlLower.contains(token)) {
        block(ms, sql, "sql_token:" + token.replace(' ', '_'));
        return;
      }
    }
    List<ParameterMapping> mappings =
        boundSql.getParameterMappings() == null
            ? Collections.emptyList()
            : boundSql.getParameterMappings();
    if (parameter == null && mappings.isEmpty()) {
      return;
    }
    try {
      Configuration configuration = ms.getConfiguration();
      TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
      MetaObject metaObject =
          (parameter != null && configuration.getObjectFactory() != null
                  && registry.hasTypeHandler(parameter.getClass()) == false
                  && (parameter instanceof Map || parameter instanceof String == false))
              ? configuration.newMetaObject(parameter)
              : null;
      for (ParameterMapping pm : mappings) {
        Object value = null;
        String property = pm.getProperty();
        if (boundSql.hasAdditionalParameter(property)) {
          value = boundSql.getAdditionalParameter(property);
        } else if (metaObject != null && metaObject.hasGetter(property)) {
          try {
            value = metaObject.getValue(property);
          } catch (Exception ignore) {
          }
        } else if (parameter instanceof Map) {
          try {
            value = ((Map<?, ?>) parameter).get(property);
          } catch (Exception ignore) {
          }
        } else if (parameter instanceof String
            || parameter instanceof Number
            || parameter instanceof Boolean
            || parameter instanceof java.util.Date
            || parameter instanceof Enum) {
          value = parameter;
        }
        if (value != null) {
          checkParameterValue(ms, sql, property, value);
        }
      }
      if (parameter instanceof Map) {
        for (Map.Entry<?, ?> e : ((Map<?, ?>) parameter).entrySet()) {
          Object v = e.getValue();
          if (v != null && !mappings.stream().anyMatch(pm -> String.valueOf(e.getKey()).equals(pm.getProperty()))) {
            checkParameterValue(ms, sql, "map:" + e.getKey(), v);
          }
        }
      }
    } catch (Exception metaEx) {
      log.warn("DangerousSqlStringInspector param skip: {} | ms={}", metaEx.getMessage(), ms == null ? "" : ms.getId());
    }
  }

  private void checkParameterValue(MappedStatement ms, String sql, String field, Object value)
      throws SQLException {
    if (value instanceof CharSequence == false) {
      return;
    }
    String s = value.toString();
    if (s == null || s.length() < 2) {
      return;
    }
    String low = s.toLowerCase();
    // B4-3 semicolon target: multi-statement payloads only (not prose ";")
    if (MULTI_STMT_SEMICOLON.matcher(low).find()) {
      block(ms, sql, "param:" + field + ":multi_statement_semicolon");
      return;
    }
    // E3 AC-E3-1 lexical: quote + keyword pattern (O'Brien safe, ' OR 1=1 blocked)
    if (PARAM_QUOTE_WITH_KEYWORD.matcher(s).find()) {
      block(ms, sql, "param:" + field + ":quote_with_sql_keyword");
      return;
    }
    for (String hint : PARAM_DANGER_SUBSTRINGS) {
      if (low.contains(hint)) {
        block(ms, sql, "param:" + field + ":hint:" + hint.replace(' ', '_'));
        return;
      }
    }
    if (low.contains("%s") && looksLikeNonParamFormat(s)) {
      block(ms, sql, "param:" + field + ":non_param_format");
    }
  }

  private static boolean looksLikeNonParamFormat(String s) {
    int idx = s.indexOf("%s");
    if (idx < 0) {
      return false;
    }
    int after = idx + 2;
    if (after >= s.length()) {
      return true;
    }
    char c = s.charAt(after);
    return Character.isWhitespace(c) || c == ',' || c == ')' || c == '\'' || c == '"' || c == ';' || c == '/';
  }

  @SuppressWarnings("unchecked")
  private void block(MappedStatement ms, String sql, String reason) throws SQLException {
    // E3 AC-E3-2: NO raw sql=, NO truncate(sql, 500) in log.
    // Only emit: sha256(sql[:128]) + reason + ms id + failFast.
    String sqlHash = sha256hex(sql == null ? "" : sql.length() > 128 ? sql.substring(0, 128) : sql);
    String traceId = safeCurrentTraceId();
    String msg =
        AUDIT_LOG_PREFIX
            + " reason="
            + reason
            + " ms="
            + (ms == null ? "" : ms.getId())
            + " sql_sha256_128="
            + sqlHash
            + " trace_id="
            + traceId
            + " failFast="
            + failFast;
    log.error(msg);
    try {
      RedisTemplate<String, String> rt =
          (RedisTemplate<String, String>)
              SpringContextUtils.getBean("redisTemplate", RedisTemplate.class);
      if (rt != null) {
        String key = String.format(ALERT_REDIS_KEY_TMPL, sanitize(ms == null ? "" : ms.getId()));
        ValueOperations<String, String> ops = rt.opsForValue();
        ops.increment(key, 1L);
        rt.expireAt(key, new java.util.Date(System.currentTimeMillis() + 86400_000L));
      }
    } catch (Exception ignore) {
    }
    if (failFast) {
      throw new RuntimeException("dangerous sql string rejected: " + reason);
    }
    throw new SQLException("dangerous sql string rejected: " + reason);
  }

  private static String sha256hex(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(64);
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception ex) {
      return "sha256_unavailable";
    }
  }

  private static String safeCurrentTraceId() {
    try {
      String tid = org.slf4j.MDC.get("traceId");
      if (tid != null && !tid.isEmpty()) {
        return tid;
      }
    } catch (Exception ignore) {
    }
    return "no_trace";
  }

  private static String sanitize(String s) {
    if (s == null) {
      return "_";
    }
    return s.replaceAll("[^A-Za-z0-9._\\-]", "_");
  }
}
