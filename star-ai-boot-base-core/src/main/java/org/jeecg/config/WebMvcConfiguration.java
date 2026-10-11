package org.jeecg.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.annotation.Resource;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.filter.ApiInputXssFilter;
import org.jeecg.config.filter.ContentTypeOptionsFilter;
import org.jeecg.config.filter.GatewaySignatureFilter;
import org.jeecg.config.filter.InternalSecretHeaderStripFilter;
import org.jeecg.config.filter.InternalTrustedS2sFilter;
import org.jeecg.config.filter.SecurityResponseHeadersFilter;
import org.jeecg.config.filter.UserTokenContextClearFilter;
import org.jeecg.config.firewall.filter.PageSizeCapRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.jackson.JacksonProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Spring Boot 2.0 CORS workaround @Author qinfeng */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer, SmartInitializingSingleton {
  private static final String STATIC_PREVIEW_PATH_PATTERN = "/sys/common/static/**";
  private static final Logger log = LoggerFactory.getLogger(WebMvcConfiguration.class);

  @Resource JeecgBaseConfig jeecgBaseConfig;

  @Value("${spring.web.resources.static-locations:${spring.resource.static-locations:}}")
  private String staticLocations;

  @Autowired private CorsProperties corsProperties;

  @Autowired private ApplicationContext appCtx;

  @Autowired(required = false)
  private PrometheusMeterRegistry prometheusMeterRegistry;

  @Autowired private ObjectProvider<Jackson2ObjectMapperBuilder> builderProvider;
  @Autowired private JacksonProperties jacksonProperties;

  @Value("${spring.profiles.active}")
  private String activeProfile;

  /** Static resource configuration — enables serving HTML, images, video, audio, etc. from disk */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    List<String> resourceLocations = new ArrayList<>();
    if (jeecgBaseConfig.getPath() != null && jeecgBaseConfig.getPath().getUpload() != null) {
      resourceLocations.add("file:" + jeecgBaseConfig.getPath().getUpload() + "//");
      resourceLocations.add("file:" + jeecgBaseConfig.getPath().getWebapp() + "//");
    }
    if (StringUtils.hasText(staticLocations)) {
      for (String location : staticLocations.split(",")) {
        if (StringUtils.hasText(location)) {
          resourceLocations.add(location.trim());
        }
      }
    }
    if (!resourceLocations.isEmpty()) {
      ResourceHandlerRegistration resourceHandlerRegistration =
          registry.addResourceHandler(STATIC_PREVIEW_PATH_PATTERN);
      resourceHandlerRegistration
          .addResourceLocations(resourceLocations.toArray(new String[0]))
          .resourceChain(false)
          .addResolver(new PathResourceResolver());
    }
    registry
        .addResourceHandler("/doc.html")
        .addResourceLocations("classpath:/META-INF/resources/")
        .resourceChain(false)
        .addResolver(new PathResourceResolver());
    registry
        .addResourceHandler("/webjars/**")
        .addResourceLocations("classpath:/META-INF/resources/webjars/")
        .resourceChain(false)
        .addResolver(new PathResourceResolver());
  }

  private static final HandlerInterceptor E7_SVG_ATTACHMENT_INTERCEPTOR =
      new HandlerInterceptor() {
        @Override
        public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
          String uri = request.getRequestURI();
          if (uri != null && uri.toLowerCase().endsWith(".svg")) {
            String safeName = "sanitized.svg";
            response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"" + safeName + "\"; filename*=UTF-8''" + safeName);
          }
          return true;
        }
      };

  /**
   * Option 1: default root path redirects to doc.html (Swagger docs page)
   * Option 2: change root path to index.html (simplified deployment: frontend bundle can be placed in project webapp per config above)
   */
  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/").setViewName("doc.html");
  }

  /**
   * Mount custom ObjectMapper while keeping Spring Boot default HttpMessageConverters (including UTF-8 String etc.).
   * Using {@link WebMvcConfigurer#configureMessageConverters} with only Jackson add replaces default converters,
   * which can cause garbled text on Windows/GBK default encoding.
   */
  @Override
  public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    ObjectMapper mapper = objectMapper();
    for (HttpMessageConverter<?> converter : converters) {
      if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
        jacksonConverter.setObjectMapper(mapper);
        jacksonConverter.setDefaultCharset(StandardCharsets.UTF_8);
        break;
      }
    }
  }

  /**
   * V2.15-4: mirrors {@code GatewayCorsConfiguration.isProdProfile()} so both layers classify the
   * active profile identically. Anything that is not clearly a development/test profile — including
   * an unknown or blank value — is treated as production (fail closed).
   */
  private boolean isProdLike() {
    if (!StringUtils.hasText(activeProfile)) {
      return true;
    }
    String lower = activeProfile.toLowerCase();
    return lower.contains("prod") || lower.contains("prd") || lower.contains("production");
  }

  @Bean
  @Conditional(CorsFilterCondition.class)
  public FilterRegistrationBean<CorsFilter> corsFilter() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    CorsConfiguration config = new CorsConfiguration();

    // V2.15-4: production detection must match the gateway's (GatewayCorsConfiguration
    // .isProdProfile). An exact "prod" match let profile "production"/"prd" fall into the
    // permissive branch and expose allowCredentials(true) + allowedOriginPattern("*").
    if (!isProdLike()) {
      // Non-production environment, allow all CORS
      config.setAllowCredentials(true); // Allow credentials (e.g. Cookie)
      config.addAllowedOriginPattern("*"); // Allow all origins
      config.addAllowedMethod("*"); // Allow all HTTP methods (GET, POST, PUT, DELETE, etc.)
      config.addAllowedHeader("*"); // Allow all request headers
    } else {
      // Production: use CORS settings from config
      config.setAllowCredentials(corsProperties.getAllowCredentials());
      config.setAllowedOrigins(corsProperties.getAllowedOrigins());
      config.setAllowedMethods(corsProperties.getAllowedMethods());
      config.addAllowedHeader(corsProperties.getAllowedHeaders());
    }

    source.registerCorsConfiguration("/**", config);

    FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE); // Set highest priority
    return bean;
  }

  @Bean
  public FilterRegistrationBean<PageSizeCapRequestFilter> pageSizeCapRequestFilter() {
    FilterRegistrationBean<PageSizeCapRequestFilter> bean =
        new FilterRegistrationBean<>(new PageSizeCapRequestFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
    bean.addUrlPatterns("/*");
    bean.setName("pageSizeCapRequestFilter");
    return bean;
  }

  @Bean
  public FilterRegistrationBean<UserTokenContextClearFilter> userTokenContextClearFilter() {
    FilterRegistrationBean<UserTokenContextClearFilter> bean =
        new FilterRegistrationBean<>(new UserTokenContextClearFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    bean.addUrlPatterns("/*");
    bean.setName("userTokenContextClearFilter");
    return bean;
  }

  @Bean
  public FilterRegistrationBean<org.jeecg.config.filter.TenantContextInboundFilter>
      tenantContextInboundFilter() {
    FilterRegistrationBean<org.jeecg.config.filter.TenantContextInboundFilter> bean =
        new FilterRegistrationBean<>(new org.jeecg.config.filter.TenantContextInboundFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    bean.addUrlPatterns("/*");
    bean.setName("tenantContextInboundFilter");
    return bean;
  }

  @Bean
  public FilterRegistrationBean<org.jeecg.config.filter.QueryTokenStripFilter> queryTokenStripFilter() {
    FilterRegistrationBean<org.jeecg.config.filter.QueryTokenStripFilter> bean =
        new FilterRegistrationBean<>(new org.jeecg.config.filter.QueryTokenStripFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    bean.addUrlPatterns("/*");
    bean.setName("queryTokenStripFilter");
    bean.setAsyncSupported(true);
    return bean;
  }

  /**
   * Site-wide response header: disable browser MIME sniffing on response body; with application/json etc. mitigates JavaScript hijacking (SECZ-SSCSP-CODE-021).
   */
  @Bean
  public FilterRegistrationBean<ApiInputXssFilter> apiInputXssFilter() {
    FilterRegistrationBean<ApiInputXssFilter> bean =
        new FilterRegistrationBean<>(new ApiInputXssFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
    bean.addUrlPatterns("/*");
    bean.setName("apiInputXssFilter");
    return bean;
  }

  @Bean
  public FilterRegistrationBean<ContentTypeOptionsFilter> contentTypeOptionsFilter() {
    FilterRegistrationBean<ContentTypeOptionsFilter> bean =
        new FilterRegistrationBean<>(new ContentTypeOptionsFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    bean.addUrlPatterns("/*");
    bean.setName("contentTypeOptionsFilter");
    return bean;
  }

  /**
   * B13-1: full browser security response headers (CSP, framing, referrer, permissions, HSTS,
   * COOP/CORP). Registered at order HIGHEST_PRECEDENCE+20 so it runs after CORS/XSS/secret filters
   * but before any business dispatch — and since we use OncePerRequestFilter + finally block,
   * headers are guaranteed to be present on error dispatches too.
   */
  @Bean
  public FilterRegistrationBean<SecurityResponseHeadersFilter> securityResponseHeadersFilter() {
    FilterRegistrationBean<SecurityResponseHeadersFilter> bean =
        new FilterRegistrationBean<>(new SecurityResponseHeadersFilter());
    bean.setOrder(SecurityResponseHeadersFilter.ORDER);
    bean.addUrlPatterns("/*");
    bean.setName("securityResponseHeadersFilter");
    bean.setAsyncSupported(true);
    return bean;
  }

  @Bean
  public FilterRegistrationBean<org.jeecg.config.filter.PrivacyCacheControlFilter>
      privacyCacheControlFilter() {
    FilterRegistrationBean<org.jeecg.config.filter.PrivacyCacheControlFilter> bean =
        new FilterRegistrationBean<>(new org.jeecg.config.filter.PrivacyCacheControlFilter());
    bean.setOrder(org.jeecg.config.filter.PrivacyCacheControlFilter.ORDER);
    bean.addUrlPatterns("/sys/common/*", "/oss/*", "/*");
    bean.setName("privacyCacheControlFilter");
    bean.setAsyncSupported(true);
    return bean;
  }

  /**
   * PR-CL-32-HS-1.2 / G-32-07: Strip and fail-close forged X-Internal-Call-Secret headers
   * arriving from public / non-private CIDR remote addresses. Order MUST be HIGHEST_PRECEDENCE
   * (before CORS / GatewaySignatureFilter) so an attacker cannot slip a forged secret header past
   * downstream secretMatches() checks (StpInterfaceImpl / InternalTrustedS2sFilter).
   */
  @Bean
  public FilterRegistrationBean<InternalSecretHeaderStripFilter> internalSecretHeaderStripFilter() {
    FilterRegistrationBean<InternalSecretHeaderStripFilter> bean =
        new FilterRegistrationBean<>(new InternalSecretHeaderStripFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    bean.addUrlPatterns("/*");
    bean.setName("internalSecretHeaderStripFilter");
    bean.setAsyncSupported(true);
    return bean;
  }

  /**
   * CP-S2S-MIGR-33 Phase 1-4: Generic module-matrix based S2S trusted-call filter.
   *
   * <p>Order HIGHEST_PRECEDENCE + 1 — strictly AFTER InternalSecretHeaderStripFilter (HP, which
   * strips forged X-Internal-Call-Secret from public ingress) and strictly BEFORE
   * GatewaySignatureFilter (HP+3) so signature validation sees the clean request AND the ATTR
   * marks set here. Bucket HP+1 is free (no other filter uses it) so no tie-breaker ambiguity.
   *
   * <p>URL pattern is "/*" — the filter itself matches URI prefixes inside the matrix to decide
   * whether to mark; this avoids needing to keep filter pattern strings in sync with matrix
   * prefixes.
   */
  @Bean
  public FilterRegistrationBean<org.jeecg.config.filter.InternalTrustedS2sFilter>
      internalTrustedS2sFilter() {
    FilterRegistrationBean<org.jeecg.config.filter.InternalTrustedS2sFilter> bean =
        new FilterRegistrationBean<>(new org.jeecg.config.filter.InternalTrustedS2sFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    bean.addUrlPatterns("/*");
    bean.setName("internalTrustedS2sFilter");
    bean.setAsyncSupported(true);
    return bean;
  }

  /**
   * PR-CL-B1-3: inject the Spring-managed {@link GatewaySignatureFilter} (bean
   * {@code gatewaySignatureFilterDelegate}) instead of {@code new ...}. Only a container-managed
   * instance gets its {@code @Value} fields populated and its {@code @PostConstruct} fail-fast
   * executed; a raw instance silently no-ops.
   */
  @Bean
  public FilterRegistrationBean<GatewaySignatureFilter> gatewaySignatureFilter(
      GatewaySignatureFilter gatewaySignatureFilterDelegate) {
    FilterRegistrationBean<GatewaySignatureFilter> bean =
        new FilterRegistrationBean<>(gatewaySignatureFilterDelegate);
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
    bean.addUrlPatterns("/*");
    bean.setName("gatewaySignatureFilter");
    bean.setAsyncSupported(true);
    return bean;
  }

  // =========================================================================================
  // PR-CL-32-HS-2.2 / PR-CL-32-CG-3.2 / PR-CL-32-RD-3.2
  // Boot-time fail-fast validators — any violation throws IllegalStateException at startup
  // so the container REFUSES to come up rather than silently running with broken guards.
  //
  // MUST run via SmartInitializingSingleton (not @PostConstruct): these validators call
  // appCtx.getBean(...) for FilterRegistrationBean instances defined as @Bean methods on
  // THIS same @Configuration class. @PostConstruct runs while the config bean is still being
  // created → Spring Boot 3 self-cycle on webMvcConfiguration and refuses to start.
  // afterSingletonsInstantiated() still fail-closes before the app accepts traffic.
  // =========================================================================================

  @Override
  public void afterSingletonsInstantiated() {
    validateSaExemptionAlignedWithSecretFilter();
    validateInternalSecretStripPresence();
    validateCidrGuardOnAllSecretMatches();
    validateRoleCodeBoundary();
    validateS2sFilterOrderBeforeGatewaySig();
  }

  /**
   * CP-S2S-MIGR-33 ModeA-v2 + R-06: {@code /sys/innerLogin} / {@code innerTestLogin} endpoints are
   * permanently deleted and must NOT be Sa-exempt (anonymous minting closed). They MUST appear on
   * {@link InternalTrustedS2sFilter} SYSTEM_S2S exclude list so a forged secret cannot inject
   * SYSTEM_S2S perms on those paths. Also keep recoverPassword / sendVerificationCode alignment
   * with SaInterceptor suffix ignores (still anonymous).
   *
   * <p>Intentionally destructive tests:
   *   [break-1] Remove "/sys/innerLogin" from SYSTEM_S2S_EXCLUDE_URIS → ISE
   *   [break-2] Remove endsWith("/sys/recoverPassword") from addInterceptors suffix list → ISE
   *             (manual / static review; this method asserts exclude-list + filter registration)
   */
  public void validateSaExemptionAlignedWithSecretFilter() {
    log.info("[32-FLT-2.2] Boot-time: ModeA-v2 innerLogin deny + SYSTEM_S2S exclude (R-06) check");

    Set<String> systemExcludes = InternalTrustedS2sFilter.getSystemS2sExcludeUris();
    for (String required :
        Arrays.asList("/sys/innerLogin", "/sys/innerTestLogin", "/sys/common/file")) {
      boolean covered =
          systemExcludes.stream()
              .anyMatch(excl -> required.equals(excl) || required.startsWith(excl));
      if (!covered) {
        throw new IllegalStateException(String.format(
            "[32-FLT-2.2-BROKEN][R-06] SYSTEM_S2S excludeUris MISSING '%s' — "
                + "forged secret on this path would inject SYSTEM_S2S perms.",
            required));
      }
    }

    @SuppressWarnings("unchecked")
    FilterRegistrationBean<InternalTrustedS2sFilter> s2sFilterReg =
        (FilterRegistrationBean<InternalTrustedS2sFilter>)
            appCtx.getBean("internalTrustedS2sFilter");
    Set<String> s2sPatterns = new HashSet<>(s2sFilterReg.getUrlPatterns());
    if (s2sPatterns.stream().noneMatch(p -> p.equals("/*") || p.equals("/**"))) {
      throw new IllegalStateException(
          "[32-FLT-2.2-BROKEN] InternalTrustedS2sFilter URL patterns must cover /* (got "
              + s2sPatterns
              + ")");
    }

    // Anonymous paths that remain Sa-exempt must still be on SYSTEM_S2S exclude (no S2S inject).
    for (String anon : Arrays.asList("/sys/recoverPassword", "/sys/sendVerificationCode")) {
      boolean covered =
          systemExcludes.stream()
              .anyMatch(excl -> anon.equals(excl) || anon.startsWith(excl));
      if (!covered) {
        throw new IllegalStateException(String.format(
            "[32-FLT-2.2-BROKEN] SYSTEM_S2S excludeUris MISSING anonymous path '%s'.", anon));
      }
    }

    log.info(
        "[32-FLT-2.2] PASS: ModeA-v2 innerLogin denied + R-06 SYSTEM_S2S excludes OK (size={})",
        systemExcludes.size());
  }

  /**
   * PR-CL-32-HS-2.2: Enforce that X-Internal-Call-Secret strip guard is present and ordered
   * BEFORE every downstream consumer of the secret (GatewaySignatureFilter HP+3 at minimum).
   *
   * Intentionally destructive tests:
   *   [break-1] Change internalSecretHeaderStripFilter Order from HP to HP+10 → ISE
   */
  public void validateInternalSecretStripPresence() {
    log.info("[32-HS-2.2] Boot-time: InternalSecretHeaderStripFilter presence & order check");

    if (!appCtx.containsBean("internalSecretHeaderStripFilter")) {
      throw new IllegalStateException(
          "[32-HS-2.2-BROKEN] Bean 'internalSecretHeaderStripFilter' MISSING — public ingress cannot strip forged X-Internal-Call-Secret.");
    }

    @SuppressWarnings("unchecked")
    FilterRegistrationBean<InternalSecretHeaderStripFilter> stripReg =
        (FilterRegistrationBean<InternalSecretHeaderStripFilter>)
            appCtx.getBean("internalSecretHeaderStripFilter");
    int stripOrder = stripReg.getOrder();

    @SuppressWarnings("unchecked")
    FilterRegistrationBean<GatewaySignatureFilter> sigReg =
        (FilterRegistrationBean<GatewaySignatureFilter>)
            appCtx.getBean("gatewaySignatureFilter");
    int sigOrder = sigReg.getOrder();

    if (stripOrder != Ordered.HIGHEST_PRECEDENCE) {
      throw new IllegalStateException(String.format(
          "[32-HS-2.2-BROKEN] internalSecretHeaderStripFilter order=%d, must be exactly HIGHEST_PRECEDENCE (%d).",
          stripOrder, Ordered.HIGHEST_PRECEDENCE));
    }
    if (stripOrder >= sigOrder) {
      throw new IllegalStateException(String.format(
          "[32-HS-2.2-BROKEN] strip order=%d is NOT BEFORE gatewaySignatureFilter order=%d — forged secret header would leak downstream.",
          stripOrder, sigOrder));
    }

    log.info("[32-HS-2.2] PASS: strip filter present HP ({}) < gatewaySig HP+3 ({})", stripOrder, sigOrder);
  }

  /**
   * CP-S2S-MIGR-33 ModeA: Boot-time assertion that every secretMatches() / constantTimeEquals()
   * call-site that compares X-Internal-Call-Secret is GUARDED by a CIDR check BEFORE the
   * comparison. Accepted guard tokens (aligned with 32-CG-B1):
   * {@code IpAccessGuard} <em>or</em> {@code isTrustedInboundRemoteAddr} (unified helper that
   * wraps {@code IpAccessGuard.isPrivateIpOrLoopback}).
   *
   * <p>Probed classes (ModeA final 2/2; InternalCallSecretFilter / InternalFeignProcessPermFilter
   * have been PERMANENTLY DELETED per final_v4_new12gates_v3.sh 12/12 GREEN — NOT restored).
   *
   * Intentionally destructive tests:
   *   [break-6] In InternalCallRequestSupport, remove IpAccessGuard/isTrustedInboundRemoteAddr → ISE
   */
  public void validateCidrGuardOnAllSecretMatches() {
    log.info("[32-CG-3.2] Boot-time: CIDR guard precedes every secretMatches() comparison");

    // { fullyQualifiedClassName, secret-compare-token }
    // Note: InternalCallSecretFilter / InternalFeignProcessPermFilter have been DELETED in
    // CP-S2S-MIGR-33 ModeA. Only InternalCallRequestSupport + StpInterfaceImpl remain as consumers.
    String[][] probes = new String[][] {
        {"org.jeecg.common.util.security.InternalCallRequestSupport", "constantTimeEquals"},
        {"org.jeecg.config.shiro.StpInterfaceImpl", "constantTimeEquals"},
    };

    for (String[] probe : probes) {
      String fqcn = probe[0];
      String secretToken = probe[1];
      Class<?> kls;
      try {
        kls = Class.forName(fqcn);
      } catch (ClassNotFoundException ex) {
        throw new IllegalStateException(String.format(
            "[32-CG-3.2-BROKEN] CIDR probe cannot load class %s (renamed? check probe list)", fqcn));
      }
      java.security.CodeSource cs = kls.getProtectionDomain().getCodeSource();
      if (cs == null || cs.getLocation() == null) {
        continue;
      }
      String classFilePath;
      try {
        classFilePath = java.nio.file.Paths.get(cs.getLocation().toURI())
            .resolve(fqcn.replace('.', '/') + ".class").toString();
      } catch (Exception ex) {
        continue;
      }
      byte[] bytecode;
      try {
        bytecode = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(classFilePath));
      } catch (Exception ex) {
        continue;
      }
      String asciiBlob = new String(bytecode, StandardCharsets.ISO_8859_1);
      boolean hasSecret = asciiBlob.contains(secretToken);
      // 32-CG-B1: either direct IpAccessGuard or unified isTrustedInboundRemoteAddr wrapper.
      boolean hasCidr =
          asciiBlob.contains("IpAccessGuard")
              || asciiBlob.contains("isTrustedInboundRemoteAddr");
      if (hasSecret && !hasCidr) {
        throw new IllegalStateException(String.format(
            "[32-CG-3.2-BROKEN] Class %s contains %s comparison but LACKS CIDR guard token "
                + "'IpAccessGuard' / 'isTrustedInboundRemoteAddr'. "
                + "Prepend IpAccessGuard.isPrivateIpOrLoopback / isTrustedInboundRemoteAddr BEFORE constantTimeEquals.",
            fqcn, secretToken));
      }
    }

    log.info("[32-CG-3.2] PASS: {}/{} secretMatches call-sites are CIDR-guarded (ModeA).", probes.length, probes.length);
  }

  /**
   * PR-CL-32-RD-3.2: Enforce the role-name decoupling contract defined in G-32-09:
   * (a) ProcessInstanceController must NEVER short-circuit via hasRole("operations_admin"),
   *     because operations_admin in DB (V1.0.86) is view-only and hasRole() bypasses
   *     the button-level @SaCheckPermission("processengine:sys_process_instance:add") gate.
   * (b) StpInterfaceImpl S2S elevation must NEVER inject the literal role "operations_admin",
   *     it MUST inject the dedicated internal_processengine_s2s role so the two namespaces
   *     (human RBAC in DB vs internal S2S service account) NEVER collide.
   *
   * <p>ProcessInstanceController lives only in jeecg-module-processengine. This
   * {@code WebMvcConfiguration} is shared via base-core across all microservices (DCS,
   * system, gateway, …). When the controller class is absent from the classpath, probe (a)
   * is skipped — coverage remains on processengine-start where the class is present and
   * break-7 still fail-closes. StpInterfaceImpl (b) is always required.
   *
   * Uses the same bytecode-string probe as validateCidrGuardOnAllSecretMatches.
   *
   * Intentionally destructive tests:
   *   [break-7] Re-add `|| StpUtil.hasRole("operations_admin")` to ProcessInstanceController
   *             enforceAddPermission line → ISE at startup
   *   [break-8] Change S2S role literal in StpInterfaceImpl.grantProcessEngineServicePerms
   *             back to "operations_admin" → ISE at startup
   */
  public void validateRoleCodeBoundary() {
    log.info("[32-RD-3.2] Boot-time: operations_admin DB-role vs S2S-role boundary check");

    final String controllerFqcn =
        "org.jeecg.modules.processengine.controller.ProcessInstanceController";
    final String stpImplFqcn = "org.jeecg.config.shiro.StpInterfaceImpl";
    // Class-file UTF-8 constants do NOT include Java source quotes. Probe the raw role
    // literals as they appear in the constant pool (32-RD-B1 / 32-RD-B2 / break-7/8).
    final String forbiddenControllerToken = "operations_admin";
    final String forbiddenStpRoleLiteral = "operations_admin";
    final String requiredS2sRole = "internal_processengine_s2s";

    for (String[] entry : new String[][] {
        { controllerFqcn, "STOP", forbiddenControllerToken },
        { stpImplFqcn,      "REQUIRE", requiredS2sRole },
        { stpImplFqcn,      "STOP", forbiddenStpRoleLiteral },
    }) {
      String fqcn = entry[0];
      String mode = entry[1];
      String token = entry[2];
      Class<?> kls;
      try {
        kls = Class.forName(fqcn);
      } catch (ClassNotFoundException ex) {
        // Optional module class: skip without weakening break-7 on processengine JVM.
        if (controllerFqcn.equals(fqcn)) {
          log.info(
              "[32-RD-3.2] Skip ProcessInstanceController probe — class not on classpath "
                  + "(service without processengine module); StpInterfaceImpl probe still runs.");
          continue;
        }
        throw new IllegalStateException(String.format(
            "[32-RD-3.2-BROKEN] Role-boundary probe cannot load class %s", fqcn));
      }
      java.security.CodeSource cs = kls.getProtectionDomain().getCodeSource();
      if (cs == null || cs.getLocation() == null) {
        continue;
      }
      String classFilePath;
      try {
        classFilePath = java.nio.file.Paths.get(cs.getLocation().toURI())
            .resolve(fqcn.replace('.', '/') + ".class").toString();
      } catch (Exception ex) {
        continue;
      }
      byte[] bytecode;
      try {
        bytecode = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(classFilePath));
      } catch (Exception ex) {
        continue;
      }
      String asciiBlob = new String(bytecode, StandardCharsets.ISO_8859_1);
      boolean found = asciiBlob.contains(token);
      if ("STOP".equals(mode) && found) {
        throw new IllegalStateException(String.format(
            "[32-RD-3.2-BROKEN] Class %s contains forbidden token '%s'. "
                + "(a) Controller must NOT bypass @SaCheckPermission with hasRole(operations_admin). "
                + "(b) StpInterfaceImpl must NOT inject DB operations_admin role for S2S.",
            fqcn, token));
      }
      if ("REQUIRE".equals(mode) && !found) {
        throw new IllegalStateException(String.format(
            "[32-RD-3.2-BROKEN] Class %s does NOT contain required S2S role token '%s' — "
                + "rename S2S role injection to dedicated internal_processengine_s2s.",
            fqcn, token));
      }
    }

    log.info("[32-RD-3.2] PASS: operations_admin DB-role is NOT coupled to S2S elevation path.");
  }

  /**
   * CP-S2S-MIGR-33 Phase 1-4: Enforce InternalTrustedS2sFilter is ordered STRICTLY BEFORE
   * GatewaySignatureFilter. InternalTrustedS2sFilter sets ATTR_TRUSTED_S2S_MODULE;
   * GatewaySignatureFilter validates request integrity. If the signature filter ran first, any
   * cached Sa permission list (built on first annotation check) would miss the S2S inject because
   * the ATTR wasn't set yet.
   *
   * <p>Intentionally destructive tests:
   *   [break-9] Change internalTrustedS2sFilter Order from HP+1 to HP+10 → ISE
   *   [break-10] Rename bean "internalTrustedS2sFilter" to a typo → ISE
   */
  public void validateS2sFilterOrderBeforeGatewaySig() {
    log.info("[S2S-MIGR-33-FLT] Boot-time: InternalTrustedS2sFilter present & order before GatewaySig");

    if (!appCtx.containsBean("internalTrustedS2sFilter")) {
      throw new IllegalStateException(
          "[S2S-MIGR-33-FLT-BROKEN] Bean 'internalTrustedS2sFilter' MISSING — cannot mark S2S requests.");
    }
    @SuppressWarnings("unchecked")
    FilterRegistrationBean<org.jeecg.config.filter.InternalTrustedS2sFilter> s2sReg =
        (FilterRegistrationBean<org.jeecg.config.filter.InternalTrustedS2sFilter>)
            appCtx.getBean("internalTrustedS2sFilter");
    int s2sOrder = s2sReg.getOrder();

    @SuppressWarnings("unchecked")
    FilterRegistrationBean<GatewaySignatureFilter> sigReg =
        (FilterRegistrationBean<GatewaySignatureFilter>)
            appCtx.getBean("gatewaySignatureFilter");
    int sigOrder = sigReg.getOrder();

    if (s2sOrder >= sigOrder) {
      throw new IllegalStateException(String.format(
          "[S2S-MIGR-33-FLT-BROKEN] internalTrustedS2sFilter order=%d is NOT BEFORE gatewaySignatureFilter order=%d — "
              + "ATTR_TRUSTED_S2S_MODULE would be set AFTER Sa permission cache built.",
          s2sOrder, sigOrder));
    }
    @SuppressWarnings("unchecked")
    FilterRegistrationBean<InternalSecretHeaderStripFilter> stripReg =
        (FilterRegistrationBean<InternalSecretHeaderStripFilter>)
            appCtx.getBean("internalSecretHeaderStripFilter");
    int stripOrder = stripReg.getOrder();
    if (stripOrder > s2sOrder) {
      throw new IllegalStateException(String.format(
          "[S2S-MIGR-33-FLT-BROKEN] internalSecretHeaderStripFilter order=%d is NOT BEFORE internalTrustedS2sFilter order=%d — "
              + "forged X-Internal-Call-Secret would reach S2S marking logic.",
          stripOrder, s2sOrder));
    }

    log.info(
        "[S2S-MIGR-33-FLT] PASS: strip HP({}) < S2S HP+1({}) < GatewaySig HP+3({})",
        stripOrder, s2sOrder, sigOrder);
  }

  /** CustomObjectMapper */
  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    // Inherit Spring Jackson default mechanism
    if (Objects.nonNull(builderProvider.getIfAvailable())) {
      objectMapper = builderProvider.getIfAvailable().createXmlMapper(false).build();
    }
    // ProcessbigDecimal
    objectMapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    // ProcessFailed
    objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false);
    // Align with container/JVM: output JSON dates in UTC
    TimeZone utc = TimeZone.getTimeZone("UTC");
    objectMapper.setTimeZone(utc);
    // Default date-time format; accepts pattern via spring.jackson.date-format
    if (Objects.isNull(jacksonProperties.getDateFormat())) {
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      dateFormat.setTimeZone(utc);
      objectMapper.setDateFormat(dateFormat);
    }
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(
        LocalDateTime.class,
        new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    javaTimeModule.addSerializer(
        LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    javaTimeModule.addSerializer(
        LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm:ss")));
    javaTimeModule.addDeserializer(
        LocalDateTime.class,
        new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    javaTimeModule.addDeserializer(
        LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    javaTimeModule.addDeserializer(
        LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern("HH:mm:ss")));
    objectMapper.registerModule(javaTimeModule);
    return objectMapper;
  }

  // update-begin---author:chenrui ---date:20240514  for: [QQYUN-9247] System monitoring feature optimization ------------
  //    /**
  //     * SpringBootAdmin HttpTrace is missing
  //     * https://blog.csdn.net/u013810234/article/details/110097201
  //     */
  //    @Bean
  //    public InMemoryHttpTraceRepository getInMemoryHttpTrace(){
  //        return new InMemoryHttpTraceRepository();
  //    }
  // update-end---author:chenrui ---date:20240514  for: [QQYUN-9247] System monitoring feature optimization ------------

  /** Fix metrics endpoint not showing JVM info (zyf) */
  @Bean
  InitializingBean forcePrometheusPostProcessor(BeanPostProcessor meterRegistryPostProcessor) {
    return () ->
        meterRegistryPostProcessor.postProcessAfterInitialization(prometheusMeterRegistry, "");
  }

  /**
   * Register interceptors [intercept params and auto-switch datasource — multi-tenant datasource switching later]
   *
   * @param registry
   */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(E7_SVG_ATTACHMENT_INTERCEPTOR)
        .addPathPatterns(STATIC_PREVIEW_PATH_PATTERN, "/**/*.svg");

    // Register Sa-Token interceptor. Annotation checks MUST run AFTER Mode-B ephemeral login:
    // SaInterceptor default isAnnotation=true runs @SaCheckLogin BEFORE the auth lambda, so
    // ATTR skip in the lambda never helps — Feign secret-only calls then 401.
    registry
        .addInterceptor(
            new SaInterceptor(
                    handle -> {
                      String path = "";
                      HttpServletRequest req = null;
                      try {
                        ServletRequestAttributes attrs =
                            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                        if (attrs != null) {
                          req = attrs.getRequest();
                          if (req != null) {
                            path = req.getRequestURI();
                          }
                        }
                      } catch (Throwable ex) {
                        log.debug("Resolve request path failed in SaInterceptor: {}");
                      }
                      // ModeA-v2 G-01: endpoint permanently deleted — hard 404 (not static-resource
                      // fallthrough / NotLoginException spam). Minting remains impossible.
                      if (path.endsWith("/sys/innerLogin") || path.endsWith("/sys/innerTestLogin")) {
                        String remote = req != null ? req.getRemoteAddr() : "-";
                        log.warn(
                            "ModeA-v2: deleted path hit uri={} remote={} — 404 (rebuild/restart upstream still calling innerLogin)",
                            path,
                            remote);
                        throw new JeecgBootException(
                            "No endpoint", CommonConstant.SC_INTERNAL_NOT_FOUND_404);
                      }
                      log.info("Request path: {}", path);
                      // Under gateway discovery prefix, excludePathPatterns may not match; allow by suffix
                      if (path.endsWith("/sys/recoverPassword")
                          || path.endsWith("/sys/sendVerificationCode")) {
                        return;
                      }

                      Object s2sModule =
                          req != null
                              ? req.getAttribute(
                                  org.jeecg.config.filter.InternalTrustedS2sFilter
                                      .ATTR_TRUSTED_S2S_MODULE)
                              : null;
                      // CP-S2S-MIGR-33 Mode B: CIDR+secret+matrix already marked — bind principal
                      // then run method annotations so @SaCheckLogin/@SaCheckPermission see S2S perms.
                      if (s2sModule != null) {
                        org.jeecg.config.filter.InternalTrustedS2sFilter.ensureEphemeralS2sSession(
                            String.valueOf(s2sModule));
                        if (handle instanceof org.springframework.web.method.HandlerMethod) {
                          java.lang.reflect.Method method =
                              ((org.springframework.web.method.HandlerMethod) handle).getMethod();
                          cn.dev33.satoken.strategy.SaAnnotationStrategy.instance.checkMethodAnnotation
                              .accept(method);
                        }
                        return;
                      }

                      if (handle instanceof org.springframework.web.method.HandlerMethod) {
                        java.lang.reflect.Method method =
                            ((org.springframework.web.method.HandlerMethod) handle).getMethod();
                        cn.dev33.satoken.strategy.SaAnnotationStrategy.instance.checkMethodAnnotation
                            .accept(method);
                      }
                      // Other requests validate Token normally
                      StpUtil.checkLogin();
                    })
                .isAnnotation(false))
        .addPathPatterns("/**")
        .excludePathPatterns(
            "/sys/cas/client/validateLogin", // CAS authentication login
            "/sys/randomImage/**", // Exclude login verification-code endpoint
            "/sys/checkCaptcha", // Exclude login verification-code endpoint
            "/sys/smsCheckCaptcha", // Exclude endpoint when too many SMS verification codes sent
            "/sys/login", // Exclude login endpoint
            "/sys/emailLogin", // Exclude login endpoint
            "/sys/verifyTotp", // Login TOTP verification
            "/**/sys/verifyTotp", // Login TOTP verification under gateway prefix
            "/sys/logout", // Exclude logout endpoint
            // B2-2: thirdLogin only OAuth render/callback remain anonymous; create/bind require login
            "/sys/thirdLogin/*/callback",
            "/sys/thirdLogin/render/**",
            "/sys/thirdLogin/oauth2/**/callback",
            "/sys/thirdLogin/oauth2/**/render",
            // A4-1 Endpoint removed: "/sys/getEncryptedString" previously exposed static AES key/iv.
            // Existing response encryption still uses EncryptedString internally; callers no longer leak them.
            "/sys/sms", // SMS verification code
            // Ember R1 / B2-2: public self-registration disabled — removed from whitelist
            "/sys/rampPublicConfig", // Ramp public brand config (brandName/requestAccessUrl/publicDomain, no sensitive data)
            "/sys/sendVerificationCode", // Send verification code
            "/**/sys/sendVerificationCode", // Send verification code under gateway prefix
            "/sys/phoneLogin", // Mobile login
            // B2-2: checkOnlyUser removed from exclude — requires login + @SaCheckPermission
            "/sys/recoverPassword", // Password recovery
            "/**/sys/recoverPassword", // Password reset under gateway prefix
            // B2-2: /sys/user/register removed from exclude; endpoint rejects (B2-3)
            "/sys/user/phoneVerification", // User forgot password: verify phone number
            "/sys/user/passwordChange", // User change password
            "/auth/2step-code", // Login verification code
            "/sys/common/static/**", // Image preview and file download without token restriction
            "/sys/common/pdf/**",
            "/sys/common/file", // PDF preview
            // V2.15-11: removed the dead uqpay webhook path — no controller exists for it anywhere
            // in the repo, so it was a permanently dead anonymous entry in the exclude list.
            "/v2/api-docs", // swagger api
            "/swagger-resources/**", // swagger resources
            "/webjars/**", // webjars
            "/doc.html", // doc html
            "/actuator/health", // actuator monitoring endpoints
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/info/**",
            "/actuator/prometheus",
            "/actuator/prometheus/**",
            "/error" // Exception forward path to avoid context errors from re-authentication
        )
        .excludePathPatterns(
            (jeecgBaseConfig.getShiro() != null
                && oConvertUtils.isNotEmpty(jeecgBaseConfig.getShiro().getExcludeUrls()))
                ? jeecgBaseConfig.getShiro().getExcludeUrls().split(",")
                : new String[0]);
  }
}
