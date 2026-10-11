package org.jeecg.common.util.security;

import cn.hutool.core.net.NetUtil;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.jeecg.common.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IpAccessGuard {

  private static final Logger log = LoggerFactory.getLogger(IpAccessGuard.class);

  private static final String IPV4_LOOPBACK = "127.0.0.0/8";
  private static final String IPV4_RFC_10_PRIVATE = "10.0.0.0/8";
  private static final String IPV4_RFC_172_PRIVATE = "172.16.0.0/12";
  private static final String IPV4_RFC_192_PRIVATE = "192.168.0.0/16";
  private static final String IPV4_LINK_LOCAL = "169.254.0.0/16";
  private static final String IPV4_CARRIER_GRADE_NAT = "100.64.0.0/10";
  private static final String IPV4_MULTICAST = "224.0.0.0/4";
  private static final String IPV4_RESERVED = "240.0.0.0/4";
  private static final String IPV4_BROADCAST = "255.255.255.255/32";
  private static final String IPV4_UNSPEC = "0.0.0.0/8";
  private static final String IPV6_LOOPBACK = "::1/128";
  private static final String IPV6_UNSPEC = "::/128";
  private static final String IPV6_UNIQUE_LOCAL = "fc00::/7";
  private static final String IPV6_LINK_LOCAL = "fe80::/10";
  private static final String IPV6_ULA_OLD = "fec0::/10";
  private static final String IPV6_MULTICAST = "ff00::/8";
  private static final String IPV4_METADATA_ALI = "100.100.100.200/32";
  private static final String IPV4_METADATA_GCP = "169.254.169.254/32";
  private static final String IPV4_METADATA_AWS = IPV4_METADATA_GCP;
  private static final String IPV4_METADATA_HW = "169.254.169.254/32";
  private static final Pattern GCP_METADATA_DNS = Pattern.compile("^metadata\\.google\\.internal$", Pattern.CASE_INSENSITIVE);
  private static final Pattern AWS_METADATA_DNS = Pattern.compile("^.*compute\\.internal$", Pattern.CASE_INSENSITIVE);
  private static final Pattern ALI_METADATA_DNS = Pattern.compile("^metadata\\.alibabacloud\\.com$", Pattern.CASE_INSENSITIVE);

  private static final List<CidrBlock> BLOCKS;

  static {
    List<CidrBlock> cidrs = new ArrayList<>();
    cidrs.add(new CidrBlock(IPV4_LOOPBACK));
    cidrs.add(new CidrBlock(IPV4_RFC_10_PRIVATE));
    cidrs.add(new CidrBlock(IPV4_RFC_172_PRIVATE));
    cidrs.add(new CidrBlock(IPV4_RFC_192_PRIVATE));
    cidrs.add(new CidrBlock(IPV4_LINK_LOCAL));
    cidrs.add(new CidrBlock(IPV4_CARRIER_GRADE_NAT));
    cidrs.add(new CidrBlock(IPV4_MULTICAST));
    cidrs.add(new CidrBlock(IPV4_RESERVED));
    cidrs.add(new CidrBlock(IPV4_BROADCAST));
    cidrs.add(new CidrBlock(IPV4_UNSPEC));
    cidrs.add(new CidrBlock(IPV4_METADATA_ALI));
    cidrs.add(new CidrBlock(IPV4_METADATA_GCP));
    cidrs.add(new CidrBlock(IPV4_METADATA_AWS));
    cidrs.add(new CidrBlock(IPV4_METADATA_HW));
    cidrs.add(new CidrBlock(IPV6_LOOPBACK));
    cidrs.add(new CidrBlock(IPV6_UNSPEC));
    cidrs.add(new CidrBlock(IPV6_UNIQUE_LOCAL));
    cidrs.add(new CidrBlock(IPV6_LINK_LOCAL));
    cidrs.add(new CidrBlock(IPV6_ULA_OLD));
    cidrs.add(new CidrBlock(IPV6_MULTICAST));
    BLOCKS = Collections.unmodifiableList(cidrs);
  }

  private IpAccessGuard() {}

  public static boolean isPrivateIpOrLoopback(String ip) {
    if (ip == null) {
      return false;
    }
    try {
      if (!IpUtils.isValidIpAddress(ip)) {
        InetAddress ia = InetAddress.getByName(ip);
        if (ia instanceof Inet6Address) {
          return ia.isLoopbackAddress()
              || ia.isLinkLocalAddress()
              || ia.isSiteLocalAddress()
              || ia.isMulticastAddress()
              || ia.isAnyLocalAddress();
        }
      }
      return matchesAnyBlock(ip);
    } catch (Exception e) {
      log.warn("IpAccessGuard: invalid IP {}", ip);
      return false;
    }
  }

  public static void assertNoSsrfTarget(String rawUriOrHost) {
    if (rawUriOrHost == null || rawUriOrHost.isEmpty()) {
      throw new SsrfBlockedException("empty target blocked");
    }
    try {
      String host = extractHost(rawUriOrHost);
      if (host == null || host.isEmpty()) {
        throw new SsrfBlockedException("empty host blocked");
      }
      if (isMetadataHostname(host)) {
        throw new SsrfBlockedException("metadata hostname blocked: " + host);
      }
      InetAddress[] addrs = InetAddress.getAllByName(host);
      for (InetAddress ia : addrs) {
        String ip = ia.getHostAddress();
        if (matchesAnyBlock(ip)) {
          throw new SsrfBlockedException("SSRF target IP blocked: " + ip + " (" + host + ")");
        }
      }
    } catch (SsrfBlockedException sbe) {
      log.error("SSRF blocked: {}", rawUriOrHost);
      throw sbe;
    } catch (UnknownHostException uhe) {
      log.warn("SSRF host resolution failed: {}", rawUriOrHost);
      throw new SsrfBlockedException("host resolution failed");
    }
  }

  /**
   * B8-1 XXL Admin allowlist (opposite of SSRF egress deny).
   *
   * <p>Scheduler must live on loopback (non-prod) or RFC1918 — never public Internet or cloud
   * metadata. Local IDE / SIT: {@code http://127.0.0.1:9000/xxl-job-admin} must
   * <strong>pass</strong> when {@code allowLoopback=true}. Public host (e.g. evil.com) must
   * fail-fast.
   *
   * @param allowLoopback when false (production), only RFC1918 is accepted
   */
  public static void assertPrivateSchedulerTarget(String rawUriOrHost, boolean allowLoopback) {
    if (rawUriOrHost == null || rawUriOrHost.isEmpty()) {
      throw new SsrfBlockedException("empty xxl admin address blocked");
    }
    try {
      String host = extractHost(rawUriOrHost);
      if (host == null || host.isEmpty()) {
        throw new SsrfBlockedException("empty xxl admin host blocked");
      }
      if (isMetadataHostname(host)) {
        throw new SsrfBlockedException("metadata hostname blocked for xxl admin: " + host);
      }
      InetAddress[] addrs = InetAddress.getAllByName(host);
      if (addrs == null || addrs.length == 0) {
        throw new SsrfBlockedException("xxl admin host resolution returned empty");
      }
      for (InetAddress ia : addrs) {
        String ip = ia.getHostAddress();
        if (ia instanceof Inet6Address) {
          if (!allowLoopback || !ia.isLoopbackAddress()) {
            throw new SsrfBlockedException(
                "xxl admin IPv6 rejected: " + ip + " (" + host + "), allowLoopback=" + allowLoopback);
          }
          continue;
        }
        boolean loopback = LOOPBACK_V4.contains(ip);
        boolean rfc1918 = RFC10.contains(ip) || RFC172.contains(ip) || RFC192.contains(ip);
        if (loopback && !allowLoopback) {
          throw new SsrfBlockedException(
              "xxl admin loopback forbidden in production: " + ip + " (" + host + ")");
        }
        if (!(loopback || rfc1918)) {
          throw new SsrfBlockedException(
              "xxl admin must be RFC1918"
                  + (allowLoopback ? " or loopback" : "")
                  + ", got: "
                  + ip
                  + " ("
                  + host
                  + ")");
        }
      }
    } catch (SsrfBlockedException sbe) {
      log.error("XXL admin address rejected: {}", rawUriOrHost);
      throw sbe;
    } catch (UnknownHostException uhe) {
      log.warn("XXL admin host resolution failed: {}", rawUriOrHost);
      throw new SsrfBlockedException("xxl admin host resolution failed: " + rawUriOrHost);
    }
  }

  /** Convenience: allow loopback (local/SIT). */
  public static void assertPrivateSchedulerTarget(String rawUriOrHost) {
    assertPrivateSchedulerTarget(rawUriOrHost, true);
  }

  private static final CidrBlock LOOPBACK_V4 = new CidrBlock(IPV4_LOOPBACK);
  private static final CidrBlock RFC10 = new CidrBlock(IPV4_RFC_10_PRIVATE);
  private static final CidrBlock RFC172 = new CidrBlock(IPV4_RFC_172_PRIVATE);
  private static final CidrBlock RFC192 = new CidrBlock(IPV4_RFC_192_PRIVATE);

  /** IPv4 loopback or RFC1918 only (scheduler / XXL Admin allowlist). */
  public static boolean isRfc1918OrLoopbackIpv4(String ip) {
    if (ip == null || ip.isEmpty()) {
      return false;
    }
    return LOOPBACK_V4.contains(ip)
        || RFC10.contains(ip)
        || RFC172.contains(ip)
        || RFC192.contains(ip);
  }

  public static boolean matchesAnyBlock(String ip) {
    for (CidrBlock block : BLOCKS) {
      if (block.contains(ip)) {
        return true;
      }
    }
    return false;
  }

  public static String extractHost(String raw) {
    if (raw == null) {
      return null;
    }
    raw = raw.trim();
    if (raw.contains("://")) {
      try {
        URI u = URI.create(raw);
        String h = u.getHost();
        return (h != null) ? h : raw;
      } catch (Exception e) {
        return raw;
      }
    }
    if (raw.contains("/")) {
      return raw.split("/", 2)[0];
    }
    if (raw.contains(":")) {
      int colon = raw.lastIndexOf(':');
      int bracket = raw.lastIndexOf(']');
      if (bracket > colon) {
        return raw;
      }
      return raw.substring(0, colon);
    }
    return raw;
  }

  public static boolean isMetadataHostname(String host) {
    if (host == null) {
      return false;
    }
    String h = host.trim().toLowerCase();
    return GCP_METADATA_DNS.matcher(h).matches()
        || AWS_METADATA_DNS.matcher(h).matches()
        || ALI_METADATA_DNS.matcher(h).matches()
        || h.endsWith(".metadata.google.internal")
        || h.endsWith(".compute.internal")
        || h.equals("metadata");
  }

  public static class SsrfBlockedException extends RuntimeException {
    public SsrfBlockedException(String msg) {
      super(msg);
    }
  }

  static final class CidrBlock {
    final InetAddress network;
    final int prefix;
    final boolean ipv6;

    CidrBlock(String cidr) {
      String[] parts = cidr.split("/", 2);
      String addr = parts[0];
      this.prefix = Integer.parseInt(parts[1]);
      try {
        this.network = InetAddress.getByName(addr);
        this.ipv6 = network instanceof Inet6Address;
      } catch (UnknownHostException uhe) {
        throw new IllegalArgumentException("invalid cidr: " + cidr, uhe);
      }
    }

    boolean contains(String ip) {
      try {
        InetAddress ia = InetAddress.getByName(ip);
        if (ia instanceof Inet4Address != network instanceof Inet4Address) {
          return false;
        }
        byte[] netBytes = network.getAddress();
        byte[] ipBytes = ia.getAddress();
        int fullBytes = prefix / 8;
        int remainBits = prefix % 8;
        for (int i = 0; i < fullBytes; i++) {
          if (netBytes[i] != ipBytes[i]) {
            return false;
          }
        }
        if (remainBits > 0 && fullBytes < netBytes.length) {
          int mask = 0xFF << (8 - remainBits);
          return ((netBytes[fullBytes] & mask) == (ipBytes[fullBytes] & mask));
        }
        return true;
      } catch (UnknownHostException uhe) {
        return false;
      }
    }
  }
}
