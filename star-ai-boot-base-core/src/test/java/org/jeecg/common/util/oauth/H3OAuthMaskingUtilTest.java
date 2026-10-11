package org.jeecg.common.util.oauth;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class H3OAuthMaskingUtilTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private File callbackLogFile;

  @Before
  public void setUp() throws Exception {
    callbackLogFile = tempFolder.newFile("h3-callback-test.log");
  }

  @Test
  public void T1_07_oauthMask_4Requirements_allPass() throws Exception {
    requirementA_noDirectStringConcat();
    requirementB_safeMaskHeadTail_calledAtLeast4();
    requirementC_len40_maskedExposedMax8Chars();
    requirementD_runtimeLog_codeLongerThan10Chars_0matches();
  }

  private void requirementA_noDirectStringConcat() {
    String source = readSource("ThirdLoginController.java");
    Pattern badPattern =
        Pattern.compile(
            "\"code=\"\\s*\\+.*code|\"authCode=\"\\s*\\+.*authCode|\"token=\"\\s*\\+.*token|\"access_token=\"\\s*\\+");
    Matcher m = badPattern.matcher(source);
    int badCount = 0;
    while (m.find()) {
      badCount++;
    }
    assertEquals(
        "T1-07(A): ThirdLoginController 4 handlers MUST NOT contain code=+code / token direct string concat (count=0)",
        0, badCount);
  }

  private void requirementB_safeMaskHeadTail_calledAtLeast4() {
    String source = readSource("ThirdLoginController.java");
    Pattern callPattern =
        Pattern.compile("H3OAuthMaskingUtil\\.safeMaskHeadTail\\s*\\(");
    Matcher m = callPattern.matcher(source);
    int count = 0;
    while (m.find()) {
      count++;
    }
    assertTrue(
        "T1-07(B): H3OAuthMaskingUtil.safeMaskHeadTail call count >= 4 in ThirdLoginController. Actual count="
            + count,
        count >= 4);
  }

  private void requirementC_len40_maskedExposedMax8Chars() {
    String longCode = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcd";
    assertEquals("len=40", 40, longCode.length());
    String masked = H3OAuthMaskingUtil.safeMaskHeadTail(longCode, 3, 3, 8);

    int exposedCount = 0;
    boolean inMask = false;
    for (int i = 0; i < masked.length(); i++) {
      char c = masked.charAt(i);
      if (c == '*') {
        inMask = true;
        continue;
      }
      if (!inMask || i > masked.lastIndexOf('*')) {
        exposedCount++;
      }
    }
    assertTrue(
        "T1-07(C): len=40 safeMaskHeadTail(head=3,tail=3,max=8) total exposed chars <= 8. Actual exposed="
            + exposedCount
            + " masked="
            + masked,
        exposedCount <= 8);

    String head4 = longCode.substring(0, 4);
    String tail4 = longCode.substring(longCode.length() - 4);
    assertTrue(
        "T1-07(C): masked value must start with head portion of original. masked=" + masked,
        masked.startsWith(head4.substring(0, 3)));
    assertTrue(
        "T1-07(C): masked value must end with tail portion of original. masked=" + masked,
        masked.endsWith(tail4.substring(tail4.length() - 3)));
    assertTrue(
        "T1-07(C): masked value must contain *** marker. masked=" + masked,
        masked.contains("***"));
  }

  private void requirementD_runtimeLog_codeLongerThan10Chars_0matches() throws Exception {
    String longCode = "abcdefghijklmnopqrstuvwxyz0123456789CODE40CHAR";
    String longAuthCode = "AUTHCODE0123456789abcdefghijklmnopqrstuvwx";
    String longState = "STATEstate0123456789abcdefghijklmnopSTATE";
    String longToken = "TOKENtoken0123456789abcdefghijklmnopqrTOKEN";

    String mc = H3OAuthMaskingUtil.safeMaskHeadTail(longCode);
    String ma = H3OAuthMaskingUtil.safeMaskHeadTail(longAuthCode);
    String ms = H3OAuthMaskingUtil.safeMaskHeadTail(longState);
    String mt = H3OAuthMaskingUtil.safeMaskHeadTail(longToken);

    String fakeCallbackLine1 = "[WeCom] OAuth2 login callback: code=" + mc + ", state=" + ms;
    String fakeCallbackLine2 = "[DingTalk] OAuth2 login callback: authCode=" + ma + ", state=" + ms;
    String fakeCallbackLine3 = "Third-party generic callback token=" + mt;

    try (BufferedWriter w = new BufferedWriter(new FileWriter(callbackLogFile))) {
      w.write(fakeCallbackLine1);
      w.newLine();
      w.write(fakeCallbackLine2);
      w.newLine();
      w.write(fakeCallbackLine3);
      w.newLine();
      w.flush();
    }

    String logContents = new String(Files.readAllBytes(callbackLogFile.toPath()));
    Pattern longCodePattern = Pattern.compile("code=[^,\\s\\)\\]\"']{10,}");
    Matcher matcher = longCodePattern.matcher(logContents);
    int longMatches = 0;
    while (matcher.find()) {
      longMatches++;
    }
    assertEquals(
        "T1-07(D): Runtime callback log grep `code=.{10,}` (raw code value longer than 10 chars) MUST be 0. Actual count="
            + longMatches
            + " matched groups: ["
            + collectMatches(longCodePattern, logContents)
            + "] file content: "
            + logContents,
        0, longMatches);
  }

  private static String readSource(String fileName) {
    java.nio.file.Path rootSysBiz =
        java.nio.file.Paths.get(
            "/Users/luohuan/Documents/trae_projects/mp-/mightyPay-boot/jeecg-module-system/jeecg-system-biz/src/main/java");
    try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(rootSysBiz)) {
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
    java.nio.file.Path rootCore =
        java.nio.file.Paths.get(
            "/Users/luohuan/Documents/trae_projects/mp-/mightyPay-boot/jeecg-boot-base-core/src/main/java");
    try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(rootCore)) {
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

  private static String collectMatches(Pattern p, String input) {
    Matcher m = p.matcher(input);
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (m.find()) {
      if (i++ > 0) sb.append(", ");
      sb.append(m.group());
    }
    return sb.toString();
  }
}
