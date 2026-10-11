package org.jeecg.config;

import lombok.Data;
import org.jeecg.common.util.LogoLoader;
import org.jeecg.config.vo.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Load project configuration
 *
 * @author: jeecg-boot
 */
@Component("jeecgBaseConfig")
@ConfigurationProperties(prefix = "jeecg")
@Data
public class JeecgBaseConfig {

  /** Signature secret (sensitive APIs such as dictionaries); @TODO default added to reduce setup cost—actual value comes from yml config */
  private String signatureSecret = "";

  /** Whether signature validation is enabled */
  private boolean signEnabled = false;

  /** List of APIs requiring enhanced validation */
  private String signUrls;

  /** Upload mode: local\Minio: minio\Aliyun: alioss */
  private String uploadType;

  /** Platform security mode configuration */
  private Firewall firewall;

  /** Shiro interceptor exclusion */
  private Shiro shiro;

  /** Upload file configuration */
  private Path path;

  /** Front-end page URLs: pc: http://localhost:3100 app: http://localhost:8051 */
  private DomainUrl domainUrl;

  /** File preview */
  private String fileViewDomain;

  /** ESConfig */
  private Elasticsearch elasticsearch;

  /**
   * WeChat Pay
   *
   * @return
   */
  private WeiXinPay weiXinPay;

  private String signVerifyDomain;

  private String newsApiKey;

  private String validationWhitelist;

  private String ossDomain;

  private String apiSubdomainAddress;

  /** Global Logo URL for FTL templates / PDF / email; override via Nacos jeecg.logo-url */
  private String logoUrl = LogoLoader.DEFAULT_LOGO_URL;

  /** Outbound HTTP SSRF protection (see {@link org.jeecg.common.util.SsrfUrlValidator}) */
  private Ssrf ssrf;

  /** Customer support email shown publicly */
  private String contactSupportEmail = "";

  /** Privileged admin login username */
  private String adminUsername = "";

  /** Brand name (Ember: Mighty Ramp) */
  private String brandName = "Mighty Ramp";

  /** Request access external link URL; Q2: if set, frontend redirects; if blank, show example text in a dialog */
  private String requestAccessUrl = "";

  /** Public-facing domain (Ember: ramp.mighty.africa) */
  private String publicDomain = "ramp.mighty.africa";

  /** Password-reset link (portal set-password / forgot-password) */
  private PasswordReset passwordReset = new PasswordReset();
}
