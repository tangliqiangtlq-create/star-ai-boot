package org.jeecg.common.valid;

/**
 * Dictionary table validation scope for {@link org.jeecg.common.valid.annotation.ValidDictionary}.
 *
 * <p>In a micro-service deployment the validating service and the service owning the dictionary
 * table may differ. The scope decides which data source the lookup runs against.
 */
public enum DictScope {

  /** Validate inside the current service's own data source (default). */
  LOCAL,

  /**
   * Validate across services through {@code CommonAPI} (Feign to the system service). Only for
   * tables owned by the system service, e.g. {@code sys_user}.
   */
  SYSTEM
}