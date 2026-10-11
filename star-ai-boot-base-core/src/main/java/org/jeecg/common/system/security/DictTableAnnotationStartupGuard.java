package org.jeecg.common.system.security;

import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.valid.DictScope;
import org.jeecg.common.valid.annotation.ValidDictionary;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Fail-fast guard for {@link ValidDictionary} table annotations.
 *
 * <p>For every annotated entity field with a non-blank {@code table}:
 *
 * <ul>
 *   <li>{@link DictScope#LOCAL}: the table must exist in the current service's own schema;
 *   <li>{@link DictScope#SYSTEM}: the table must be declared in the system-owned allowlist.
 * </ul>
 *
 * <p>Any violation refuses startup, so a mis-classified annotation can never silently read the
 * wrong database again.
 */
@Slf4j
@Component
public class DictTableAnnotationStartupGuard implements SmartInitializingSingleton {

  private static final String GUARD_ENABLED = "jeecg.dict-table.guard-enabled";
  private static final String SCAN_PACKAGES = "jeecg.dict-table.scan-packages";
  private static final String SYSTEM_ALLOWLIST = "jeecg.dict-table.system-allowlist";
  private static final String DEFAULT_SCAN_PACKAGES = "org.jeecg";
  private static final String DEFAULT_SYSTEM_ALLOWLIST = "sys_user,sys_dict,sys_role,sys_depart,sys_permission,sys_user_role";

  private final Environment env;
  private final JdbcTemplate jdbcTemplate;

  public DictTableAnnotationStartupGuard(Environment env, JdbcTemplate jdbcTemplate) {
    this.env = env;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (!Boolean.parseBoolean(env.getProperty(GUARD_ENABLED, "true"))) {
      log.warn("[DictTableGuard] disabled via {}=false; dictionary table scope is NOT verified.", GUARD_ENABLED);
      return;
    }

    Set<String> allowlist = parseAllowlist(env.getProperty(SYSTEM_ALLOWLIST, DEFAULT_SYSTEM_ALLOWLIST));
    String[] packages = env.getProperty(SCAN_PACKAGES, DEFAULT_SCAN_PACKAGES).split(",");

    List<Annotation> annotations = scanAnnotations(packages);
    Set<String> violations = new TreeSet<>();
    Map<String, Boolean> tableExistsCache = new HashMap<>();

    for (Annotation a : annotations) {
      String table = a.table;
      if (a.scope == DictScope.SYSTEM) {
        if (!allowlist.contains(table.toLowerCase())) {
          violations.add(
              " - " + a.owner + "  table=" + table + "  scope=SYSTEM  -> not in " + SYSTEM_ALLOWLIST);
        }
      } else {
        boolean exists =
            tableExistsCache.computeIfAbsent(table, this::tableExistsInCurrentSchema);
        if (!exists) {
          violations.add(
              " - " + a.owner + "  table=" + table + "  scope=LOCAL  -> table not found in current schema");
        }
      }
    }

    if (!violations.isEmpty()) {
      StringBuilder sb = new StringBuilder(512);
      sb.append("[DictTableGuard] ").append(violations.size()).append(" violation(s):\n");
      sb.append(String.join("\n", violations)).append("\n");
      sb.append("  fix: table in this service's own schema -> keep default scope=LOCAL; ")
          .append("table owned by the system service -> declare scope=DictScope.SYSTEM and add it to ")
          .append(SYSTEM_ALLOWLIST).append(".");
      log.error("\n========== DICT TABLE GUARD BLOCK STARTUP ==========\n{}\n===================================================", sb);
      throw new BeanInitializationException(sb.toString());
    }

    log.info("[DictTableGuard] {} dictionary table annotation(s) verified (LOCAL/SYSTEM scopes OK).", annotations.size());
  }

  private Set<String> parseAllowlist(String raw) {
    Set<String> set = new LinkedHashSet<>();
    for (String s : raw.split(",")) {
      String v = s.trim().toLowerCase();
      if (!v.isEmpty()) {
        set.add(v);
      }
    }
    return set;
  }

  private List<Annotation> scanAnnotations(String[] packages) {
    ClassPathScanningCandidateComponentProvider provider =
        new ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(new AnnotationTypeFilter(TableName.class));

    List<Annotation> result = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String pkg : packages) {
      String trimmed = pkg.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      for (BeanDefinition bd : provider.findCandidateComponents(trimmed)) {
        String className = bd.getBeanClassName();
        if (className == null || !seen.add(className)) {
          continue;
        }
        Class<?> clazz;
        try {
          clazz = Class.forName(className, false, getClass().getClassLoader());
        } catch (Throwable t) {
          log.warn("[DictTableGuard] cannot load candidate class {}: {}", className, t.getMessage());
          continue;
        }
        ReflectionUtils.doWithFields(
            clazz,
            (Field field) -> {
              ValidDictionary vd = AnnotatedElementUtils.findMergedAnnotation(field, ValidDictionary.class);
              if (vd == null || !StringUtils.hasText(vd.table())) {
                return;
              }
              result.add(new Annotation(clazz.getSimpleName() + "#" + field.getName(), vd.table(), vd.scope()));
            },
            (Field field) -> AnnotatedElementUtils.hasAnnotation(field, ValidDictionary.class));
      }
    }
    return result;
  }

  private boolean tableExistsInCurrentSchema(String table) {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.TABLES where TABLE_SCHEMA = DATABASE() and TABLE_NAME = ?",
            Long.class,
            table);
    return count != null && count > 0;
  }

  private static final class Annotation {
    private final String owner;
    private final String table;
    private final DictScope scope;

    private Annotation(String owner, String table, DictScope scope) {
      this.owner = owner;
      this.table = table;
      this.scope = scope;
    }
  }
}