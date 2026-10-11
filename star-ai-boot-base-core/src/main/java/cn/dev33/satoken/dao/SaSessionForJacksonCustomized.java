/*
 * Sa-Token 1.40 -> 1.44 upgrade compatibility: legacy Redis session JSON uses this class as the @class type id.
 * Officially removed in 1.41+; this shim avoids InvalidTypeIdException and migrates tokenSignList on deserialization.
 */
package cn.dev33.satoken.dao;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.session.TokenSign;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

@JsonIgnoreProperties({"timeout"})
public class SaSessionForJacksonCustomized extends SaSession {

  private static final long serialVersionUID = -7600983549653130681L;

  public SaSessionForJacksonCustomized() {
    super();
  }

  public SaSessionForJacksonCustomized(String id) {
    super(id);
  }

  /**
   * 1.40 uses tokenSignList to store login tokens; 1.41+ changed to terminalList. Memory migration on deserialization; does not trigger update() write back to Redis.
   */
  @JsonProperty("tokenSignList")
  public void migrateTokenSignList(List<TokenSign> tokenSignList) {
    if (tokenSignList == null || tokenSignList.isEmpty()) {
      return;
    }
    List<SaTerminalInfo> terminals = new Vector<>();
    int index = 0;
    for (TokenSign sign : tokenSignList) {
      if (sign == null || sign.getValue() == null) {
        continue;
      }
      index++;
      terminals.add(
          new SaTerminalInfo(
              index, sign.getValue(), sign.getDevice(), toExtraData(sign.getTag())));
    }
    if (!terminals.isEmpty()) {
      setTerminalList(terminals);
      setHistoryTerminalCount(index);
    }
  }

  private static Map<String, Object> toExtraData(Object tag) {
    if (tag == null) {
      return null;
    }
    if (tag instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) tag;
      return map;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("tag", tag);
    return map;
  }
}
