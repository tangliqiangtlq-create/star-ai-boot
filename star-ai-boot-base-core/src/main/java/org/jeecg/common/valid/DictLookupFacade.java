package org.jeecg.common.valid;

import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.system.vo.DictModel;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Single entry point for reading a dictionary table inside a service.
 *
 * <p>Resolution order (identical for {@code DictAspect} and hand-written lookups):
 *
 * <ol>
 *   <li>{@code ds} is non-blank → always the cross-service {@code CommonAPI} path (an explicit
 *       datasource hint means "do not assume the local schema");
 *   <li>table is declared local ({@link DictDisplayProperties}) and physically exists →
 *       {@link LocalDictTableLookup} on this service's datasource;
 *   <li>a registered {@link RemoteDictLookupProvider} owns the table → peer-service read-only call;
 *   <li>otherwise → {@code CommonAPI} (Feign to the system service) — unchanged legacy behaviour,
 *       still subject to the system-side sensitive-table / whitelist guards.
 * </ol>
 *
 * <p>This is what removes the {@code mpay_system} dictionary-mirror dependency: a table owned by a
 * service is always resolved from its owner, and it is impossible to accidentally hit the stale
 * copy in the system schema.
 */
@Slf4j
@Component
public class DictLookupFacade implements SmartInitializingSingleton {

  private static final String GUARD_ENABLED = "jeecg.dict.guard-enabled";

  private final LocalDictTableLookup localLookup;
  private final DictDisplayProperties properties;
  private final ObjectProvider<RemoteDictLookupProvider> remoteProviders;
  private final ObjectProvider<CommonAPI> commonAPIProvider;
  private final Environment env;

  public DictLookupFacade(
      LocalDictTableLookup localLookup,
      DictDisplayProperties properties,
      ObjectProvider<RemoteDictLookupProvider> remoteProviders,
      ObjectProvider<CommonAPI> commonAPIProvider,
      Environment env) {
    this.localLookup = localLookup;
    this.properties = properties;
    this.remoteProviders = remoteProviders;
    this.commonAPIProvider = commonAPIProvider;
    this.env = env;
  }

  /**
   * Fail-fast guard: every table declared in {@code jeecg.dict.local-tables} must physically exist
   * in this service's own schema. A typo would otherwise silently fall back to the cross-service
   * path and re-introduce the stale-mirror read this mechanism exists to remove.
   */
  @Override
  public void afterSingletonsInstantiated() {
    if (properties.getLocalTables().isEmpty()) {
      return;
    }
    if (!Boolean.parseBoolean(env.getProperty(GUARD_ENABLED, "true"))) {
      log.warn("[DictDisplayGuard] disabled via {}=false; local-tables is NOT verified.", GUARD_ENABLED);
      return;
    }
    List<String> missing = new ArrayList<>();
    for (String table : properties.getLocalTables()) {
      if (table == null || table.trim().isEmpty()) {
        continue;
      }
      if (!localLookup.supports(table.trim())) {
        missing.add(table.trim());
      }
    }
    if (!missing.isEmpty()) {
      String msg =
          "[DictDisplayGuard] jeecg.dict.local-tables declares table(s) not present in the current "
              + "schema: "
              + missing
              + ". Fix the allowlist (or the migration) before starting up.";
      log.error("\n========== DICT DISPLAY GUARD BLOCK STARTUP ==========\n{}\n======================================================", msg);
      throw new BeanInitializationException(msg);
    }
    log.info(
        "[DictDisplayGuard] {} locally-owned dictionary table(s) verified: {}",
        properties.getLocalTables().size(),
        properties.getLocalTables());
  }

  /** @return {@code true} when {@code table} is resolved from this service's own schema */
  public boolean isLocal(String table) {
    return properties.isLocal(table) && localLookup.supports(table);
  }

  private RemoteDictLookupProvider remoteProvider(String table) {
    for (RemoteDictLookupProvider provider : remoteProviders) {
      try {
        if (provider.supports(table)) {
          return provider;
        }
      } catch (RuntimeException e) {
        log.warn(
            "DictLookupFacade provider {} failed on supports({}): {}",
            provider.getClass().getSimpleName(),
            table,
            e.getMessage());
      }
    }
    return null;
  }

  public String translate(String table, String text, String code, String key) {
    return translate(table, text, code, key, null);
  }

  public String translate(String table, String text, String code, String key, String ds) {
    if (StrUtil.isBlank(ds)) {
      if (isLocal(table)) {
        return localLookup.translate(table, text, code, key);
      }
      RemoteDictLookupProvider provider = remoteProvider(table);
      if (provider != null) {
        return provider.translate(table, text, code, key);
      }
    }
    CommonAPI commonAPI = commonAPIProvider.getIfAvailable();
    if (commonAPI == null) {
      log.warn("DictLookupFacade: CommonAPI unavailable; cannot translate table={}", table);
      return null;
    }
    return commonAPI.translateDictFromTable(table, text, code, key);
  }

  public Map<String, String> translateBatch(
      String table, String text, String code, Collection<String> keys, String ds) {
    Map<String, String> out = new LinkedHashMap<>();
    if (keys == null || keys.isEmpty()) {
      return out;
    }
    if (StrUtil.isBlank(ds)) {
      if (isLocal(table)) {
        return localLookup.translateBatch(table, text, code, keys);
      }
      RemoteDictLookupProvider provider = remoteProvider(table);
      if (provider != null) {
        Map<String, String> resolved = provider.translateBatch(table, text, code, keys);
        return resolved == null ? out : resolved;
      }
    }
    CommonAPI commonAPI = commonAPIProvider.getIfAvailable();
    if (commonAPI == null) {
      log.warn("DictLookupFacade: CommonAPI unavailable; cannot translate table={}", table);
      return out;
    }
    List<DictModel> models =
        commonAPI.translateDictFromTableByKeys(table, text, code, String.join(",", keys), ds);
    if (models != null) {
      for (DictModel model : models) {
        if (model != null && StrUtil.isNotBlank(model.getValue()) && StrUtil.isNotBlank(model.getText())) {
          out.put(model.getValue(), model.getText());
        }
      }
    }
    return out;
  }
}
