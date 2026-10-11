package org.jeecg;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import org.apache.commons.lang3.StringUtils;
import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.jeecg.common.base.BaseMap;
import org.jeecg.common.constant.GlobalConstants;
import org.jeecg.common.logging.Log4jdbcSqlOnlySetup;
import org.jeecg.common.util.JdbcLoggingLevels;
import org.jeecg.config.NewFeignConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Microservice bootstrap class (use this class to start the project in microservice mode). Note: initialize Nacos DB scripts first, db/tables_nacos.sql
 *
 * @author zyf
 * @date: 2022/4/21 10:55
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {DruidDataSourceAutoConfigure.class})
@ComponentScan(
    basePackages = "org.jeecg",
    excludeFilters = {
      @Filter(
          type = FilterType.REGEX,
          pattern = "org\\.jeecg\\.modules\\.monitor\\.actuator\\.CustomActuatorConfig"),
      @Filter(type = FilterType.REGEX, pattern = "org\\.jeecg\\.modules\\.drag\\..*"),
      @Filter(type = FilterType.REGEX, pattern = "org\\.jeecg\\.config\\.FeignConfig"),
      @Filter(
          type = FilterType.REGEX,
          pattern = "org\\.jeecg\\.starter\\.cloud\\.handler\\.CustomSentinelExceptionHandler"),
      @Filter(
          type = FilterType.REGEX,
          pattern = "org\\.jeecg\\.starter\\.cloud\\.interceptor\\.DefaultRequestOriginParser"),
      @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class)
    })
@Import(NewFeignConfig.class)
@EnableFeignClients(basePackages = {"org.jeecg"})
@EnableScheduling
@EnableFileStorage
public class JeecgSystemCloudApplication extends SpringBootServletInitializer
    implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(JeecgSystemCloudApplication.class);

  @Autowired private RedisTemplate<String, Object> redisTemplate;

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.sources(JeecgSystemCloudApplication.class);
  }

  public static void main(String[] args) {
    System.setProperty("com.mysql.cj.protocol.ExportControlled.enabled", "false");
    org.jeecg.common.env.RepoRootEnvLoader.load();
    try (java.io.InputStream is = JeecgSystemCloudApplication.class.getClassLoader()
        .getResourceAsStream("jeecg_database.properties")) {
      if (is != null) {
        throw new org.springframework.beans.factory.BeanInitializationException(
            "D8 REFUSE TO START: BOOT-INF/classes/jeecg_database.properties bundled in jar "
          + "(DB creds leak via 'jar xf'). Fix maven-jar-plugin excludes in parent pom.");
      }
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
    Log4jdbcSqlOnlySetup.install();
    forceInfoLogLevel();
    ConfigurableApplicationContext application =
        SpringApplication.run(JeecgSystemCloudApplication.class, args);
    JdbcLoggingLevels.applySqlOnly();
    Environment env = application.getEnvironment();
    String ip = firstNonLoopbackIpv4ForBanner();
    String port = env.getProperty("server.port");
    String path = StringUtils.defaultString(env.getProperty("server.servlet.context-path"));
    log.info(
        "\n----------------------------------------------------------\n\t"
            + "Application Jeecg-Boot is running! Access URLs:\n\t"
            + "Local: \t\thttp://localhost:"
            + port
            + path
            + "/doc.html\n"
            + "External: \thttp://"
            + ip
            + ":"
            + port
            + path
            + "/doc.html\n"
            + "Swagger docs: \thttp://"
            + ip
            + ":"
            + port
            + path
            + "/doc.html\n"
            + "----------------------------------------------------------");
  }

  @Override
  public void run(String... args) {
    BaseMap params = new BaseMap();
    params.put(GlobalConstants.HANDLER_NAME, GlobalConstants.LODER_ROUDER_HANDLER);
    redisTemplate.convertAndSend(GlobalConstants.REDIS_TOPIC_NAME, params);
  }

  @Bean("applicationTaskExecutor")
  public TaskExecutor applicationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(8);
    executor.setMaxPoolSize(32);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("app-task-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }

  @Bean
  public DdlApplicationRunner ddlApplicationRunner() {
    return new DdlApplicationRunner(Collections.emptyList());
  }

  private static String firstNonLoopbackIpv4ForBanner() {
    InetAddress addr = findFirstNonLoopbackInet4();
    return addr != null ? addr.getHostAddress() : "";
  }

  private static InetAddress findFirstNonLoopbackInet4() {
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface ni = interfaces.nextElement();
        if (!ni.isUp() || ni.isLoopback()) {
          continue;
        }
        Enumeration<InetAddress> addresses = ni.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress addr = addresses.nextElement();
          if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
            return addr;
          }
        }
      }
    } catch (SocketException e) {
      log.warn("Failed to enumerate network interfaces and obtain local IPv4: {}");
    }
    return null;
  }

  private static void forceInfoLogLevel() {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
      return;
    }
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(Level.INFO);
    context.getLogger("org.springframework").setLevel(Level.INFO);
    context.getLogger("org.jeecg").setLevel(Level.INFO);
    context.getLogger("io.grpc").setLevel(Level.INFO);
    JdbcLoggingLevels.applySqlOnly();
  }
}
