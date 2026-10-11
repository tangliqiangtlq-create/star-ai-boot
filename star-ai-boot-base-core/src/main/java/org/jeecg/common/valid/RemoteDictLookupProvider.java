package org.jeecg.common.valid;

import java.util.Collection;
import java.util.Map;

/**
 * SPI for resolving a dictionary table that is owned by a <b>peer</b> service while it has to be
 * rendered by the current service.
 *
 * <p>Example: DCS renders {@code RemittanceRecord.status} through
 * {@code @Dict(dictTable = "sys_state_definition", ...)}, but {@code sys_state_definition} is owned
 * by the process-engine schema ({@code mpay_wallet}). The DCS implementation of this SPI answers for
 * that single table and proxies the lookup to the process-engine service, instead of letting the
 * request fall through to the system service (which used to hold an empty mirror table).
 *
 * <p>Implementations MUST treat {@link #supports(String)} as a strict allowlist — never answer for
 * tables they do not own, otherwise unrelated dictionary lookups would be hijacked.
 */
public interface RemoteDictLookupProvider {

  /** @return {@code true} only for the exact table names this provider owns */
  boolean supports(String table);

  /** Single key lookup; returns {@code null} when unknown. */
  String translate(String table, String text, String code, String key);

  /** Batch lookup; returns key → text for the keys that resolved. */
  Map<String, String> translateBatch(
      String table, String text, String code, Collection<String> keys);
}
