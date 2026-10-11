package org.jeecg.common.security.audit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"prod", "staging", "uat"})
public class H4EmailBodyNeverInfoVerifier implements InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(H4EmailBodyNeverInfoVerifier.class);

  private static final Pattern[] FORBIDDEN_LOG_PATTERNS =
      new Pattern[] {
        Pattern.compile("getContent\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\.body\\s*[+.]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("setPasswordUrl", Pattern.CASE_INSENSITIVE),
        Pattern.compile("TOTP.*\\d{4,8}", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Email\\s*content\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/reset/pwd\\?token=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("verificationCode\\s*=", Pattern.CASE_INSENSITIVE)
      };

  private static final String[] TARGET_FILE_SUFFIXES =
      new String[] {"EmailSendMsgHandle.java", "SendEmailTopic.java"};

  @Override
  public void afterPropertiesSet() throws Exception {
    List<String> violations = new ArrayList<>();
    String repoRoot = detectRepoRoot();
    if (repoRoot == null) {
      log.warn("H4EmailBodyNeverInfoVerifier: repo root not detected, skip source scan");
      return;
    }
    List<File> targets = new ArrayList<>();
    collectTargets(new File(repoRoot), targets);
    for (File f : targets) {
      scanFile(f, violations);
    }
    if (!violations.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append(
          "H4-BODY-INFO-GATE FAIL: EmailSendMsgHandle/SendEmailTopic log touching email body / setPasswordUrl / TOTP detected (prod/stage fail-fast):\n");
      for (String v : violations) {
        sb.append("  - ").append(v).append('\n');
      }
      log.error(sb.toString());
      throw new BeanInitializationSecurityException(sb.toString());
    }
    log.info(
        "H4EmailBodyNeverInfoVerifier PASS: {} file(s) scanned, 0 forbidden pattern hits in EmailSendMsgHandle/SendEmailTopic log.*() calls",
        targets.size());
  }

  private String detectRepoRoot() {
    String cp = System.getProperty("java.class.path", "");
    if (cp.contains("mightyPay-boot")) {
      int idx = cp.indexOf("mightyPay-boot");
      String candidate = cp.substring(0, idx + "mightyPay-boot".length());
      if (new File(candidate).isDirectory()) {
        return candidate;
      }
    }
    String userDir = System.getProperty("user.dir", "");
    if (new File(userDir, "pom.xml").exists()) {
      return userDir;
    }
    File f = new File("").getAbsoluteFile();
    while (f != null) {
      if (new File(f, "pom.xml").exists() && new File(f, "jeecg-boot-base-core").isDirectory()) {
        return f.getAbsolutePath();
      }
      f = f.getParentFile();
    }
    return null;
  }

  private void collectTargets(File root, List<File> out) {
    if (root == null || !root.isDirectory()) {
      return;
    }
    File[] children = root.listFiles();
    if (children == null) {
      return;
    }
    for (File child : children) {
      if (child.isDirectory()) {
        String name = child.getName();
        if ("target".equals(name) || ".git".equals(name) || "node_modules".equals(name)) {
          continue;
        }
        collectTargets(child, out);
      } else {
        for (String suffix : TARGET_FILE_SUFFIXES) {
          if (child.getName().endsWith(suffix)) {
            out.add(child);
            break;
          }
        }
      }
    }
  }

  private void scanFile(File f, List<String> violations) {
    try (BufferedReader br = new BufferedReader(new FileReader(f))) {
      String line;
      int ln = 0;
      boolean insideLogCall = false;
      StringBuilder logStatement = new StringBuilder();
      while ((line = br.readLine()) != null) {
        ln++;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*")) {
          continue;
        }
        if (insideLogCall) {
          logStatement.append(' ').append(trimmed);
          if (trimmed.contains(";")) {
            checkLogStatement(logStatement.toString(), f.getPath(), ln, violations);
            insideLogCall = false;
            logStatement.setLength(0);
          }
        } else if (isLogStatement(trimmed)) {
          insideLogCall = true;
          logStatement.setLength(0);
          logStatement.append(trimmed);
          if (trimmed.contains(";")) {
            checkLogStatement(logStatement.toString(), f.getPath(), ln, violations);
            insideLogCall = false;
            logStatement.setLength(0);
          }
        }
      }
      if (insideLogCall && logStatement.length() > 0) {
        checkLogStatement(logStatement.toString(), f.getPath(), ln, violations);
      }
    } catch (IOException e) {
      log.warn("H4 verifier: cannot read {}: {}", f.getPath(), e.toString());
    }
  }

  private boolean isLogStatement(String trimmed) {
    return trimmed.startsWith("log.")
        && (trimmed.contains("info(")
            || trimmed.contains("warn(")
            || trimmed.contains("error(")
            || trimmed.contains("debug(")
            || trimmed.contains("trace("));
  }

  private void checkLogStatement(String stmt, String path, int line, List<String> violations) {
    for (Pattern p : FORBIDDEN_LOG_PATTERNS) {
      if (p.matcher(stmt).find()) {
        violations.add(path + ":" + line + " matches [" + p.pattern() + "]");
      }
    }
  }

  public static class BeanInitializationSecurityException extends RuntimeException {
    public BeanInitializationSecurityException(String msg) {
      super(msg);
    }
  }
}
