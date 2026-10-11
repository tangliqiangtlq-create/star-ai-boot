package org.jeecg.common.security;

import static org.junit.Assert.*;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;

public class H7ExceptionRedactorTest {

  @Test
  public void T3_02_redactSummary_masksJWT_resetUrl_TOTP_and_hasMarker() {
    String jwt = "eyJhbGcOiJIUzI1NiIsInR5cCI6IkpXVCJ9.header.payload.signature123";
    String resetUrl = "https://mpay-stg.xnow.xyz/reset/pwd?token=XYZ123ABCDEF4567890LEAK";
    RuntimeException ex =
        new RuntimeException(
            "Failed auth token=" + jwt + " reset_url=" + resetUrl + " TOTP=988776 password=S3cretPass");

    String redacted = H7ExceptionRedactor.redactSummary(ex);

    assertFalse("JWT eyJhbGc prefix must NOT appear", redacted.contains("eyJhbGc"));
    assertFalse("reset/pwd?token=XYZ... plaintext must NOT appear",
        redacted.contains("token=XYZ123ABCDEF4567890LEAK"));
    assertFalse("TOTP=988776 6-digit plaintext must NOT appear",
        redacted.contains("TOTP=988776"));
    assertFalse("password=S3cretPass value must NOT appear", redacted.contains("=S3cretPass"));
    assertTrue("H7_REDACTED marker MUST appear in output",
        redacted.contains(H7ExceptionRedactor.REDACTED_MARKER));
    assertTrue("REDACTED_VALUE *** mask char MUST appear (at least 1 sensitive hit)",
        redacted.contains(H7ExceptionRedactor.REDACTED_VALUE));
  }

  @Test
  public void T3_03_causeChainDepthAtLeast5_and_hasLocalPathFilter_scaleGE2() throws Exception {
    java.lang.reflect.Method m =
        H7ExceptionRedactor.class.getDeclaredMethod(
            "collectCauseChain", Throwable.class, int.class);
    m.setAccessible(true);

    RuntimeException c5 = new RuntimeException("L5: token=LEAKVIADEPTH5 secret=Z");
    RuntimeException c4 = new RuntimeException("L4", c5);
    RuntimeException c3 = new RuntimeException("L3", c4);
    RuntimeException c2 = new RuntimeException("L2", c3);
    RuntimeException c1 = new RuntimeException("L1", c2);

    @SuppressWarnings("unchecked")
    List<Throwable> chain = (List<Throwable>) m.invoke(null, c1, 5);
    assertEquals("collectCauseChain maxDepth=5 must return all 5 exceptions", 5, chain.size());

    String redacted = H7ExceptionRedactor.redactSummary(c1);
    assertTrue("marker still present for deep cause", redacted.contains(H7ExceptionRedactor.REDACTED_MARKER));
    assertFalse("token=LEAKVIADEPTH5 still present at depth 5 → forbidden",
        redacted.contains("LEAKVIADEPTH5"));

    java.lang.reflect.Method lp =
        H7ExceptionRedactor.class.getDeclaredMethod("localPathPatternCount");
    lp.setAccessible(true);
    int lpCount = (int) lp.invoke(null);
    assertTrue("LOCAL_PATH_PATTERNS count >= 2 for stack frame filtering. actual=" + lpCount,
        lpCount >= 2);

    java.lang.reflect.Method sp =
        H7ExceptionRedactor.class.getDeclaredMethod("sensitivePatternCount");
    sp.setAccessible(true);
    int spCount = (int) sp.invoke(null);
    assertTrue("SENSITIVE_PATTERNS >= 9 (T3-02 rubric). actual=" + spCount, spCount >= 9);

    StackTraceElement localSte =
        new StackTraceElement(
            "com.example.Leak", "foo", "/private/tmp/build/src/main/java/Leak.java", 123);
    String renderedLine = renderSteViaReflection(localSte);
    if (renderedLine != null) {
      assertFalse("Local /private/tmp path must be [filtered] in output",
          renderedLine.contains("/private/tmp/build"));
      assertTrue("filtered placeholder should replace local disk path",
          renderedLine.contains("[filtered]"));
    }
  }

  @Test
  public void T3_02_redactMessageBody_nullEmptyAndPlainBody() {
    assertEquals("null body → REDACTED_MARKER",
        H7ExceptionRedactor.REDACTED_MARKER, H7ExceptionRedactor.redactMessageBody(null));
    assertEquals("empty body → REDACTED_MARKER",
        H7ExceptionRedactor.REDACTED_MARKER, H7ExceptionRedactor.redactMessageBody(""));

    String body = "{\"password\":\"S3cret!\",\"access_token\":\"abcdefghijklmnopqrstuvwxyz123456\",\"TOTP\":\"112233\"}";
    String r = H7ExceptionRedactor.redactMessageBody(body);
    assertFalse("password value masked", r.contains("S3cret"));
    assertFalse("access_token long value masked", r.contains("abcdefghijklmnopqrstuvwxyz123456"));
    assertFalse("6-digit TOTP value masked", r.contains("112233"));
    assertTrue("[H7_REDACTED] marker always appended", r.contains(H7ExceptionRedactor.REDACTED_MARKER));
  }

  @Test
  public void T3_03_stackLine_fileUsersPath_alsoFiltered() throws Exception {
    StackTraceElement ste =
        new StackTraceElement(
            "org.jeecg.MyController", "handle", "/Users/luohuan/projects/mp-/src/MyController.java", 45);
    String line = renderSteViaReflection(ste);
    assertNotNull("render method accessible", line);
    assertFalse("/Users path must not leak into stack lines", line.contains("/Users/luohuan"));
    assertTrue("[filtered] or redacted placeholder present", line.contains("[filtered]"));
  }

  private static String renderSteViaReflection(StackTraceElement e) {
    try {
      java.lang.reflect.Method m =
          H7ExceptionRedactor.class.getDeclaredMethod(
              "renderStackTraceElement", StackTraceElement.class);
      m.setAccessible(true);
      return (String) m.invoke(null, e);
    } catch (Exception ex) {
      return null;
    }
  }
}
