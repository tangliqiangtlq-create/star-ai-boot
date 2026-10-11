package org.jeecg.common.valid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Per-service allowlist of dictionary tables that live in <b>this</b> service's own schema and may
 * therefore be resolved locally by {@code DictAspect} (instead of the cross-service
 * {@code CommonAPI} path).
 *
 * <p>Why an explicit allowlist instead of "table exists locally":
 *
 * <ul>
 *   <li>the system service physically holds {@code sys_user} / {@code sys_role}, but those lookups
 *       must keep going through the system dictionary endpoints so the {@code sys_table_white_list}
 *       / sensitive-table guards are enforced — a "local first" rule based purely on existence would
 *       silently bypass them;
 *   <li>it keeps the blast radius auditable: a service only ever resolves the tables it declares.
 * </ul>
 *
 * <p>Configuration (per service, e.g. {@code application-*.yml}):
 *
 * <pre>
 * jeecg:
 *   dict:
 *     local-tables: dcs_currency,dcs_blockchain
 * </pre>
 *
 * <p>Empty (the default) preserves the legacy behaviour: every {@code @Dict(dictTable = ...)}
 * lookup goes through {@code CommonAPI}.
 */
@Component
@ConfigurationProperties(prefix = "jeecg.dict")
public class DictDisplayProperties {

  /** Dictionary tables owned by the current service's schema (case-insensitive). */
  private List<String> localTables = new ArrayList<>();

  public List<String> getLocalTables() {
    return localTables;
  }

  public void setLocalTables(List<String> localTables) {
    this.localTables = localTables == null ? new ArrayList<>() : localTables;
  }

  /** @return {@code true} when {@code table} is declared as locally owned by this service */
  public boolean isLocal(String table) {
    if (table == null || localTables.isEmpty()) {
      return false;
    }
    String normalized = table.trim().toLowerCase(Locale.ROOT);
    for (String candidate : localTables) {
      if (candidate != null && candidate.trim().toLowerCase(Locale.ROOT).equals(normalized)) {
        return true;
      }
    }
    return false;
  }
}
