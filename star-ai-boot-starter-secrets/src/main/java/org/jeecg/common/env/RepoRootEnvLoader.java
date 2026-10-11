package org.jeecg.common.env;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Load repository-root {@code .env} into JVM system properties for IDE local runs.
 *
 * <p>Spring Boot / IntelliJ do not auto-load Docker-style {@code .env}. Call {@link #load()} at the
 * very beginning of {@code main} before {@code SpringApplication.run}. Prefer {@link
 * RepoRootDotEnvEnvironmentPostProcessor} for Spring placeholder resolution ({@code
 * ${INTERNAL_CALL_SECRET:}} in Nacos); this loader mirrors the same file into system properties for
 * early non-Spring readers.
 */
public final class RepoRootEnvLoader {

  private RepoRootEnvLoader() {}

  public static void load() {
    Path envFile = locateEnvFile();
    if (envFile == null) {
      System.out.println(
          "[env] repo-root .env not found (cwd=" + Paths.get("").toAbsolutePath() + ")");
      return;
    }
    Map<String, Object> map = readDotEnvFile(envFile);
    int loaded = 0;
    for (Map.Entry<String, Object> e : map.entrySet()) {
      String key = e.getKey();
      String value = String.valueOf(e.getValue());
      // Prefer non-blank OS env; blank OS env must NOT block .env (IDE trap).
      String os = System.getenv(key);
      if (os != null && !os.isBlank()) {
        continue;
      }
      String existing = System.getProperty(key);
      if (existing != null && !existing.isBlank()) {
        continue;
      }
      System.setProperty(key, value);
      loaded++;
    }
    System.out.println(
        "[env] loaded " + loaded + " keys from " + envFile.toAbsolutePath() + " into system properties");
  }

  /** Shared parse used by {@link RepoRootDotEnvEnvironmentPostProcessor}. */
  public static Map<String, Object> readDotEnvAsMap() {
    Path envFile = locateEnvFile();
    if (envFile == null) {
      return Collections.emptyMap();
    }
    return readDotEnvFile(envFile);
  }

  static Map<String, Object> readDotEnvFile(Path envFile) {
    Map<String, Object> out = new LinkedHashMap<>();
    try (BufferedReader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
      String line;
      boolean first = true;
      while ((line = reader.readLine()) != null) {
        if (first) {
          // Strip UTF-8 BOM from first line (PowerShell Set-Content -Encoding utf8).
          if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
            line = line.substring(1);
          }
          first = false;
        }
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        int eq = line.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        String key = line.substring(0, eq).trim();
        if (key.isEmpty()) {
          continue;
        }
        String value = stripQuotes(line.substring(eq + 1).trim());
        out.put(key, value);
      }
    } catch (IOException e) {
      System.err.println("[env] failed to read " + envFile + ": " + e.getMessage());
      return Collections.emptyMap();
    }
    return out;
  }

  private static String stripQuotes(String value) {
    if ((value.startsWith("\"") && value.endsWith("\""))
        || (value.startsWith("'") && value.endsWith("'"))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  static Path locateEnvFile() {
    Path cwd = Paths.get("").toAbsolutePath().normalize();
    Path dir = cwd;
    for (int i = 0; i < 10 && dir != null; i++) {
      Path env = dir.resolve(".env");
      if (Files.isRegularFile(env)) {
        return env;
      }
      dir = dir.getParent();
    }
    // Fallback: walk from this class location (IDE often uses module cwd).
    try {
      Path codeSource =
          Paths.get(RepoRootEnvLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      dir = codeSource;
      for (int i = 0; i < 15 && dir != null; i++) {
        Path env = dir.resolve(".env");
        if (Files.isRegularFile(env)) {
          return env;
        }
        dir = dir.getParent();
      }
    } catch (Exception ignore) {
      // keep null
    }
    return null;
  }
}
