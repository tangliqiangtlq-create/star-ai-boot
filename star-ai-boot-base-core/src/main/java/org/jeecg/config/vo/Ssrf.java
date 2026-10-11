package org.jeecg.config.vo;

import lombok.Data;

/**
 * Outbound HTTP SSRF protection configuration (used with {@link org.jeecg.common.util.RestUtil}).
 */
@Data
public class Ssrf {

  /** Whether to enable SSRF verification*/
  private boolean enabled = true;

  /**
   * Additional allowed hostnames or IPs (comma-separated) for internal ES, in-house services, etc.; hosts in Elasticsearch
   * {@code jeecg.elasticsearch.cluster-nodes} are merged into the allow list automatically; no duplicate config needed.
   */
  private String allowedHosts = "localhost";

  /** Whether to block RFC1918 private network addresses*/
  private boolean blockPrivateNetworks = true;

  /** Whether to intercept the loopback address*/
  private boolean blockLoopback = true;

  /** Whether to intercept link local addresses (commonly used in cloud metadata)*/
  private boolean blockLinkLocal = true;
}
