package org.jeecg.common.util.log;

import static org.junit.Assert.*;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

public class H1TurboFilterTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private File logFile;

  @Before
  public void setUp() throws IOException {
    logFile = tempFolder.newFile("h1-turbo-test.log");
    H1LogbackTurboConfigurator.resetForTest();
    H1LogbackTurboConfigurator.installToCurrentContext();
  }

  @org.junit.After
  public void tearDown() {
    try {
      Object lc = org.slf4j.LoggerFactory.getILoggerFactory();
      if (lc instanceof ch.qos.logback.classic.LoggerContext) {
        ((ch.qos.logback.classic.LoggerContext) lc).reset();
      }
    } catch (Throwable ignore) {
    }
    H1LogbackTurboConfigurator.resetForTest();
  }

  @Test
  public void T1_01_noInstanceOfString_onlyBranch_and_hasThrowableProxyUtil() {
    String source = readClassSource(H1SensitiveTurboFilter.class);
    int instanceofStringCount = countOccurrences(source, "instanceof String");
    assertEquals(
        "T1-01: H1SensitiveTurboFilter must NOT contain instanceof String exclusive branch (count must be 0 or both String+Throwable covered)",
        0, instanceofStringCount);

    int throwableProxyUtilCount = countOccurrences(source, "ThrowableProxyUtil");
    assertTrue(
        "T1-01: H1SensitiveTurboFilter must use ThrowableProxyUtil or equivalent stack access (count>=1)",
        throwableProxyUtilCount >= 1);

    int reflectStackTraceCount = countOccurrences(source, "getStackTrace");
    assertTrue(
        "T1-01: H1SensitiveTurboFilter must access stack trace via ThrowableProxyUtil or reflect (either >=1)",
        throwableProxyUtilCount >= 1 || reflectStackTraceCount >= 1);
  }

  @Test
  public void T1_02_turboConfiguratorIsListener_and_verifierIsInitializingBean() {
    Class<?> configuratorClass = H1LogbackTurboConfigurator.class;
    boolean isApplicationListener =
        org.springframework.context.ApplicationListener.class.isAssignableFrom(configuratorClass);
    assertTrue(
        "T1-02: H1LogbackTurboConfigurator must implement ApplicationListener<EnvironmentPreparedEvent>",
        isApplicationListener);

    java.lang.reflect.Type[] ifaces = configuratorClass.getGenericInterfaces();
    boolean hasEnvPreparedEvent = false;
    for (java.lang.reflect.Type t : ifaces) {
      if (t.getTypeName().contains("ApplicationEnvironmentPreparedEvent")) {
        hasEnvPreparedEvent = true;
        break;
      }
    }
    assertTrue(
        "T1-02: H1LogbackTurboConfigurator ApplicationListener generic param must be EnvironmentPreparedEvent",
        hasEnvPreparedEvent);

    Class<?> verifierClass = H1TurboCoverageVerifier.class;
    boolean isInitializingBean =
        org.springframework.beans.factory.InitializingBean.class.isAssignableFrom(verifierClass);
    assertTrue(
        "T1-02: H1TurboCoverageVerifier must be InitializingBean for fail-fast at startup",
        isInitializingBean);

    assertTrue(
        "T1-02: H1TurboCoverageVerifier must have @Profile prod/staging/uat for fail-fast",
        verifierClass.isAnnotationPresent(org.springframework.context.annotation.Profile.class));
  }

  @Test
  public void T1_03_runtimeException_withJwtToken_resetUrl_totp_0OriginalInLogs()
      throws IOException {
    String jwt = "eyJhbGcOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
    String resetUrl = "https://mpay-stg.xnow.xyz/reset/pwd?token=YYYzzzAAAbbbCCCddd123456";
    String totp = "123456";
    RuntimeException ex =
        new RuntimeException(
            "token=" + jwt + " reset_url=" + resetUrl + " TOTP=" + totp);

    String stack = ThrowableProxyUtil.asString(new ThrowableProxy(ex));
    String redactedStack = H1SensitiveTurboFilter.redactAll(stack);

    writeLogFile(logFile, redactedStack);
    String logContents = new String(Files.readAllBytes(logFile.toPath()));

    assertFalse(
        "T1-03: JWT eyJhbGc prefix must NOT appear in persisted log file",
        logContents.contains("eyJhbGc"));
    assertFalse(
        "T1-03: reset password URL token=YYY must NOT appear in persisted log file",
        logContents.contains("token=YYYzzzAAAbbbCCCddd123456"));
    assertFalse(
        "T1-03: TOTP=123456 plaintext must NOT appear in persisted log file",
        logContents.contains("123456"));
    assertTrue(
        "T1-03: *** mask marker MUST appear at least once in redacted output",
        logContents.contains("***"));
  }

  @Test
  public void T1_04_patternDictionaryCount_ge31_scale3() {
    int count = H1SensitiveTurboFilter.patternCount();
    assertTrue(
        "T1-04: Regex sensitive dictionary entries >= 31 for scale >= 3. Actual count=" + count,
        count >= 31);
    int namesSize = H1SensitiveTurboFilter.getPatternNames().size();
    assertEquals(
        "T1-04: Pattern compile count must match patternNames list size", count, namesSize);
    assertTrue("T1-04: Scale >= 3 (>=31) reached. patterns=" + count, count >= 31);
  }

  private static String readClassSource(Class<?> clazz) {
    StringBuilder sb = new StringBuilder();
    try {
      java.io.InputStream is =
          clazz.getClassLoader()
              .getResourceAsStream(clazz.getName().replace('.', '/') + ".class");
      if (is != null) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
          baos.write(buf, 0, n);
        }
        sb.append(baos);
      }
    } catch (Exception ignore) {
    }
    String clazzName = clazz.getSimpleName() + ".java";
    java.nio.file.Path root =
        java.nio.file.Paths.get(
            "/Users/luohuan/Documents/trae_projects/mp-/mightyPay-boot/jeecg-boot-base-core/src/main/java");
    try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(root)) {
      java.nio.file.Path found =
          paths
              .filter(p -> p.getFileName().toString().equals(clazzName))
              .findFirst()
              .orElse(null);
      if (found != null) {
        return new String(Files.readAllBytes(found));
      }
    } catch (Exception ignore) {
    }
    return sb.toString();
  }

  private static int countOccurrences(String haystack, String needle) {
    if (haystack == null || needle == null || needle.isEmpty()) {
      return 0;
    }
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }

  private static void writeLogFile(File f, String content) throws IOException {
    try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
      w.write(content);
      w.flush();
    }
  }
}
