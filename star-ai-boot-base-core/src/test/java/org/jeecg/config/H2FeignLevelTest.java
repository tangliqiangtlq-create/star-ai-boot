package org.jeecg.config;

import static org.junit.Assert.*;

import feign.Logger;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class H2FeignLevelTest {

  private H2FeignHeaderStrippingInterceptor stripper;
  private RequestTemplate template;

  @Before
  public void setUp() {
    stripper = new H2FeignHeaderStrippingInterceptor();
    template = new RequestTemplate();
    template.method("GET");
    template.uri("/api/test");
    template.body("{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
  }

  @Test
  public void T1_05_newFeignConfig_NO_FULL_LITERAL_and_verifierThrowsOnFull() {
    String newFeignConfigSource = readSourceFile("NewFeignConfig.java");
    int fullCount = countOccurrences(newFeignConfigSource, "Logger.Level.FULL");
    assertEquals(
        "T1-05: NewFeignConfig.java Logger.Level.FULL literal rg count must be 0", 0, fullCount);
    int basicCount = countOccurrences(newFeignConfigSource, "Logger.Level.BASIC");
    assertTrue(
        "T1-05: NewFeignConfig must contain Logger.Level.BASIC (actual count=" + basicCount + ")",
        basicCount >= 1);

    ListableBeanFactory bfFull = new DefaultListableBeanFactory();
    ((DefaultListableBeanFactory) bfFull)
        .registerSingleton("fullLevel", Logger.Level.FULL);
    H2FeignLevelStartupVerifier verifier = new H2FeignLevelStartupVerifier();
    try {
      java.lang.reflect.Field f = H2FeignLevelStartupVerifier.class.getDeclaredField("beanFactory");
      f.setAccessible(true);
      f.set(verifier, bfFull);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    boolean threw = false;
    try {
      verifier.verifyLevels();
    } catch (org.springframework.beans.factory.BeanCreationException ex) {
      threw = true;
    }
    assertTrue(
        "T1-05: H2FeignLevelStartupVerifier prod profile MUST throw BeanCreationException when FULL bean present",
        threw);
  }

  @Test
  public void T1_06_headerStrippingInterceptor_strips6Headers_0BearerInLog() {
    template.header("Authorization", "Bearer xxx-secret-token-zz90qwertyabcdef");
    template.header("Cookie", "sessionId=xyz-session-value-abcdefzz90qwerty");
    template.header("Set-Cookie", "newSession=yyy-cookie-new-val-0987654321");
    String otpSentinel = "otp-" + java.util.UUID.randomUUID();
    template.header("X-OTP", otpSentinel);
    template.header("X-MFA", "mfa-secret-totp-val-abc123");
    template.header("X-Gateway-Secret", "gateway-sig-secret-key-xyz789");
    template.header("X-Plain-Business", "keepThisValue");
    template.header("Content-Type", "application/json");

    stripper.apply(template);

    Map<String, Collection<String>> headersAfter = template.headers();
    for (String stripName : H2FeignHeaderStrippingInterceptor.getStripHeaders()) {
      boolean found = containsHeaderCaseInsensitive(headersAfter, stripName);
      assertFalse(
          "T1-06: After stripping, header=" + stripName + " MUST be absent from Feign request",
          found);
    }

    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Collection<String>> e : headersAfter.entrySet()) {
      sb.append(e.getKey()).append(": ").append(String.join(",", e.getValue())).append("\n");
    }
    String serialized = sb.toString();
    assertFalse(
        "T1-06: Bearer xxx-secret-token raw value must NOT appear in serialized header dump",
        serialized.contains("Bearer xxx-secret-token"));
    assertFalse(
        "T1-06: sessionId=xyz-session raw cookie value must NOT appear in serialized header dump",
        serialized.contains("xyz-session-value"));
    assertFalse(
        "T1-06: X-OTP raw value must NOT appear in serialized header dump",
        serialized.contains(otpSentinel));
    assertTrue(
        "T1-06: non-sensitive header X-Plain-Business MUST be preserved",
        serialized.contains("keepThisValue"));
    assertTrue(
        "T1-06: Content-Type header MUST be preserved",
        serialized.contains("application/json"));

    List<String> stripList = H2FeignHeaderStrippingInterceptor.getStripHeaders();
    assertEquals("T1-06: 6 classes of headers must be stripped (Authorization/Cookie/Set-Cookie/X-OTP/X-MFA/X-Gateway-Secret)", 6, stripList.size());
  }

  private static String readSourceFile(String fileName) {
    java.nio.file.Path root =
        java.nio.file.Paths.get(
            "/Users/luohuan/Documents/trae_projects/mp-/mightyPay-boot/jeecg-boot-base-core/src/main/java");
    try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(root)) {
      java.nio.file.Path found =
          paths
              .filter(p -> p.getFileName().toString().equals(fileName))
              .findFirst()
              .orElse(null);
      if (found != null) {
        return new String(java.nio.file.Files.readAllBytes(found));
      }
    } catch (Exception ignore) {
    }
    return "";
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

  private static boolean containsHeaderCaseInsensitive(
      Map<String, Collection<String>> headers, String name) {
    if (headers == null || name == null) {
      return false;
    }
    for (String key : headers.keySet()) {
      if (key != null && key.equalsIgnoreCase(name)) {
        Collection<String> vals = headers.get(key);
        if (vals != null && !vals.isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }
}
