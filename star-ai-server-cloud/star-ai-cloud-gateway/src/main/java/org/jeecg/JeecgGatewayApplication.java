package org.jeecg;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.Resource;
import java.net.InetSocketAddress;
import java.net.Socket;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.loader.DynamicRouteLoader;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author jeecg
 */
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(
    excludeFilters = {
      @Filter(
          type = FilterType.REGEX,
          pattern = "org\\.jeecg\\.starter\\.cloud\\.handler\\.CustomSentinelExceptionHandler"),
      @Filter(
          type = FilterType.REGEX,
          pattern = "org\\.jeecg\\.starter\\.cloud\\.interceptor\\.DefaultRequestOriginParser")
    })
@Slf4j
public class JeecgGatewayApplication implements CommandLineRunner {

  @Resource private DynamicRouteLoader dynamicRouteLoader;

  public static void main(String[] args) {
    System.setProperty("com.mysql.cj.protocol.ExportControlled.enabled", "false");
    org.jeecg.common.env.RepoRootEnvLoader.load();
    try (java.io.InputStream is = JeecgGatewayApplication.class.getClassLoader()
        .getResourceAsStream("jeecg_database.properties")) {
      if (is != null) {
        throw new org.springframework.beans.factory.BeanInitializationException(
            "D8 REFUSE TO START: BOOT-INF/classes/jeecg_database.properties bundled in jar "
          + "(DB creds leak via 'jar xf'). Fix maven-jar-plugin excludes in parent pom.");
      }
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
    forceInfoLogLevel();
    SpringApplication.run(JeecgGatewayApplication.class, args);
  }

  /**
   * Load routes after container initialization
   *
   * @param strings
   */
  @Override
  public void run(String... strings) {
    dynamicRouteLoader.refresh(null);
  }

  /**
   * V-X-06 / P1 Fail-Fast: Kill JVM on startup if (a) gateway listens on a
   * public-exposure port (80/443/8080/8088) directly, or (b) localhost:8080 is
   * already listening via another process (indicates a mis-deploy that leaks
   * backend containers onto the internet).
   */
  @Bean
  CommandLineRunner gatewayPortExposureFailFast(
      @Value("${server.port:9999}") int serverPort, Environment env,
      ApplicationContext ignoredCtx) {
    return args -> {
      String profile = String.join(",", env.getActiveProfiles());
      boolean strict =
          profile.contains("prod")
              || profile.contains("gray")
              || profile.contains("pre")
              || profile.contains("uat");
      if (strict) {
        if (serverPort == 80
            || serverPort == 443
            || serverPort == 8080
            || serverPort == 8088) {
          throw new IllegalStateException(
              "GATEWAY-PORT-FAILFAST: server.port="
                  + serverPort
                  + " must not be 80/443/8080/8088 on prod/gray/pre/uat."
                  + " Terminate JVM now. profiles="
                  + profile);
        }
        try (Socket s = new Socket()) {
          s.connect(new InetSocketAddress("127.0.0.1", 8080), 500);
          throw new IllegalStateException(
              "GATEWAY-PORT-FAILFAST: 127.0.0.1:8080 TCP OPEN."
                  + " A local process is listening on the admin-bypass port 8080."
                  + " Close the listener before starting gateway. profiles="
                  + profile);
        } catch (java.net.ConnectException
            | java.net.SocketTimeoutException
            | java.net.NoRouteToHostException ok) {
          // 8080 not listening = PASS
        }
      }
      log.info(
          "GATEWAY-PORT-CHECK OK: server.port={} profiles={} strict={}",
          serverPort,
          profile,
          strict);
    };
  }

  private static void forceInfoLogLevel() {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
      return;
    }
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(Level.INFO);
    context.getLogger("org.springframework").setLevel(Level.INFO);
    context.getLogger("org.jeecg").setLevel(Level.INFO);
    context.getLogger("reactor.netty").setLevel(Level.INFO);
    context.getLogger("io.grpc").setLevel(Level.INFO);
  }

  /**
   * Avoid WebFlux.fn functional routing for the index page so the gateway does not
   * expose a RouterFunction-based entrypoint while staying on the Spring 5.3.x line.
   */
  @Controller
  static class GatewayDocController {
    private final org.springframework.core.io.Resource indexHtml;

    GatewayDocController(
        @Value("classpath:/META-INF/resources/doc.html")
            org.springframework.core.io.Resource indexHtml) {
      this.indexHtml = indexHtml;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public org.springframework.core.io.Resource index() {
      return indexHtml;
    }
  }
}
