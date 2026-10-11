package org.jeecg.common.env.secrets;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Section 6 / G07 multi-backend secret injection (into Spring Environment, <b>not</b> OS {@code environ}).
 *
 * <h2>Backends (stackable)</h2>
 *
 * <ol>
 *   <li><b>file / K8S projected</b> — {@code /run/secrets} (or {@code MPAY_SECRETS_DIR}):
 *       {@code application.properties}, {@code mpay-secrets.properties}, one file name = key name
 *   <li><b>local-aes</b> — values prefixed {@code AESGCM:...}, decrypted with a 32-byte master key (Compose / local)
 *   <li><b>kms</b> — values prefixed {@code KMS:...}; {@code MPAY_KMS_PROVIDER=}{@code
 *       none|local-aes|huawei-csms|huawei-dew|aws-kms} (pluggable vendors; appendix B.8 default {@code none})
 * </ol>
 *
 * <h2>Master key sources (local-aes)</h2>
 *
 * {@code MPAY_SECRETS_MASTER_KEY_FILE} → 32-byte file contents; or under secrets dir {@code
 * MPAY_SECRETS_MASTER_KEY} / {@code master.key}. Do not put business DB passwords in compose {@code
 * environment:}.
 *
 * <h2>Strict mode</h2>
 *
 * {@code MPAY_SECRETS_STRICT=true} (default on {@code prod}/{@code staging}): fail-fast at startup if critical
 * secrets exist only as plaintext OS env and are not provided via secrets.
 */
public class MpaySecretsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  public static final String PROPERTY_SOURCE_NAME = "mpaySecrets";
  public static final String DEFAULT_DIR = "/run/secrets";
  public static final String DIR_ENV = "MPAY_SECRETS_DIR";
  public static final String KMS_PREFIX = "KMS:";

  private static final String[] CRITICAL_KEYS = {
    "SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD",
    "OTP_PEPPER",
    "INTERNAL_CALL_SECRET",
    "GATEWAY_SECRET",
    "SA_TOKEN_JWT_SECRET_KEY",
    "JASYPT_ENCRYPTOR_PASSWORD"
  };

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 5;
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
      return;
    }

    Function<String, String> cfg = k -> firstNonBlank(environment.getProperty(k), System.getenv(k));

    Map<String, Object> map = new LinkedHashMap<>();
    Path dir = resolveDir(cfg);
    System.err.println(
        "[env] MpaySecrets EPP enter dir="
            + (dir != null ? dir.toAbsolutePath() : "(null)")
            + " exists="
            + (dir != null && Files.isDirectory(dir)));
    if (dir != null && Files.isDirectory(dir)) {
      loadPropertiesFile(dir.resolve("application.properties"), map);
      loadPropertiesFile(dir.resolve("mpay-secrets.properties"), map);
      loadPropertiesFile(dir.resolve("mpay-secrets.enc.properties"), map);
      loadPerKeyFiles(dir, map);
    }

    byte[] masterKey = loadMasterKey(dir, cfg);
    MpayKmsDecryptor kms = resolveKms(cfg, masterKey);

    Map<String, Object> resolved = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : map.entrySet()) {
      String key = e.getKey();
      String raw = String.valueOf(e.getValue());
      resolved.put(key, resolveValue(key, raw, masterKey, kms));
    }

    if (!resolved.isEmpty()) {
      mirrorSystemProps(resolved);
      environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, resolved));
      System.err.println(
          "[env] MpaySecrets loaded "
              + resolved.size()
              + " keys from "
              + (dir != null ? dir : "(empty)")
              + " kms="
              + (kms != null ? kms.providerName() : "none")
              + " (not exported to OS env)");
    } else {
      System.err.println("[env] MpaySecrets loaded 0 keys (dir unresolved or empty secrets files)");
    }

    enforceStrict(environment, resolved, cfg);
  }

  private static void enforceStrict(
      ConfigurableEnvironment environment,
      Map<String, Object> resolved,
      Function<String, String> cfg) {
    String flag = firstNonBlank(cfg.apply("MPAY_SECRETS_STRICT"), "");
    if ("false".equalsIgnoreCase(flag)) {
      return;
    }
    boolean strict =
        "true".equalsIgnoreCase(flag)
            || (hasProfile(environment, "prod") && !resolved.isEmpty());
    if (!strict) {
      return;
    }
    for (String key : CRITICAL_KEYS) {
      String fromOs = System.getenv(key);
      if (fromOs != null && !fromOs.isBlank()) {
        throw new IllegalStateException(
            "[G07] MPAY_SECRETS_STRICT: critical key '"
                + key
                + "' is present in OS env (printenv). "
                + "Remove it from compose environment/env_file; inject via /run/secrets or KMS only.");
      }
    }
  }

  private static boolean hasProfile(ConfigurableEnvironment env, String name) {
    for (String p : env.getActiveProfiles()) {
      if (name.equalsIgnoreCase(p)) {
        return true;
      }
    }
    return false;
  }

  private static String resolveValue(
      String key, String raw, byte[] masterKey, MpayKmsDecryptor kms) {
    if (raw == null) {
      return "";
    }
    String v = raw.trim();
    if (LocalAesGcmSecretsCrypto.isEncrypted(v)) {
      if (masterKey == null) {
        throw new IllegalStateException(
            "Encrypted secret for '" + key + "' but master key missing (MPAY_SECRETS_MASTER_KEY_FILE)");
      }
      return LocalAesGcmSecretsCrypto.decryptPrefixed(masterKey, v);
    }
    if (v.startsWith(KMS_PREFIX)) {
      if (kms == null) {
        throw new IllegalStateException(
            "KMS: value for '" + key + "' but MPAY_KMS_PROVIDER not configured");
      }
      return kms.decrypt(v);
    }
    return v;
  }

  private static MpayKmsDecryptor resolveKms(Function<String, String> cfg, byte[] masterKey) {
    String provider =
        firstNonBlank(cfg.apply("MPAY_KMS_PROVIDER"), masterKey != null ? "local-aes" : "none")
            .toLowerCase(Locale.ROOT);
    if ("none".equals(provider) || provider.isEmpty()) {
      return null;
    }
    if ("local-aes".equals(provider)) {
      if (masterKey == null) {
        return null;
      }
      final byte[] key = masterKey;
      return new MpayKmsDecryptor() {
        @Override
        public String decrypt(String ciphertextOrSecretId) {
          String c = ciphertextOrSecretId;
          if (c.startsWith(KMS_PREFIX)) {
            c = c.substring(KMS_PREFIX.length()).trim();
          }
          if (!LocalAesGcmSecretsCrypto.isEncrypted(c)
              && !c.startsWith(LocalAesGcmSecretsCrypto.PREFIX)) {
            c = LocalAesGcmSecretsCrypto.PREFIX + c;
          }
          return LocalAesGcmSecretsCrypto.decryptPrefixed(key, c);
        }

        @Override
        public String providerName() {
          return "local-aes";
        }
      };
    }
    if ("huawei-csms".equals(provider) || "huawei-dew".equals(provider)) {
      return HuaweiCsmsKmsDecryptor.tryCreate(cfg)
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "MPAY_KMS_PROVIDER="
                          + provider
                          + " requires MPAY_KMS_ENDPOINT + MPAY_KMS_PROJECT_ID + "
                          + "(MPAY_KMS_TOKEN_FILE|MPAY_KMS_TOKEN|MPAY_KMS_AK+MPAY_KMS_SK)"));
    }
    if ("aws-kms".equals(provider) || "aws".equals(provider)) {
      return AwsKmsDecryptor.tryCreate(cfg)
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "MPAY_KMS_PROVIDER=aws-kms requires MPAY_KMS_ENDPOINT + "
                          + "MPAY_KMS_DECRYPT_PATH + MPAY_KMS_TOKEN_FILE|MPAY_KMS_TOKEN "
                          + "(HTTP proxy/SDK gateway). Wire format remains KMS:<cipher>"));
    }
    throw new IllegalStateException(
        "Unknown MPAY_KMS_PROVIDER="
            + provider
            + " (supported: none|local-aes|huawei-csms|huawei-dew|aws-kms)");
  }

  private static byte[] loadMasterKey(Path secretsDir, Function<String, String> cfg) {
    String file = firstNonBlank(cfg.apply("MPAY_SECRETS_MASTER_KEY_FILE"), "");
    if (!file.isEmpty()) {
      return readKeyFile(Paths.get(file));
    }
    if (secretsDir != null) {
      Path a = secretsDir.resolve("MPAY_SECRETS_MASTER_KEY");
      Path b = secretsDir.resolve("master.key");
      if (Files.isRegularFile(a)) {
        return readKeyFile(a);
      }
      if (Files.isRegularFile(b)) {
        return readKeyFile(b);
      }
    }
    String inline = firstNonBlank(cfg.apply("MPAY_SECRETS_MASTER_KEY"), "");
    if (!inline.isEmpty()) {
      byte[] raw = inline.getBytes(StandardCharsets.UTF_8);
      if (raw.length == 32) {
        System.err.println(
            "[env] WARN: MPAY_SECRETS_MASTER_KEY from env; prefer MPAY_SECRETS_MASTER_KEY_FILE");
        return raw;
      }
      // allow base64 32-byte
      try {
        byte[] b64 = java.util.Base64.getDecoder().decode(inline);
        if (b64.length == 32) {
          return b64;
        }
      } catch (IllegalArgumentException ignore) {
        // fall through
      }
      throw new IllegalStateException("MPAY_SECRETS_MASTER_KEY must be 32 raw bytes or base64(32)");
    }
    return null;
  }

  private static byte[] readKeyFile(Path p) {
    try {
      byte[] raw = Files.readAllBytes(p);
      // trim single trailing newline for text files of exact 32 printable chars
      if (raw.length == 33 && (raw[32] == '\n' || raw[32] == '\r')) {
        raw = java.util.Arrays.copyOf(raw, 32);
      }
      if (raw.length == 34 && raw[32] == '\r' && raw[33] == '\n') {
        raw = java.util.Arrays.copyOf(raw, 32);
      }
      if (raw.length != 32) {
        // try base64 text
        String s = new String(raw, StandardCharsets.UTF_8).trim();
        byte[] b64 = java.util.Base64.getDecoder().decode(s);
        if (b64.length == 32) {
          return b64;
        }
        throw new IllegalStateException("Master key file must be 32 bytes: " + p);
      }
      return raw;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read master key file: " + p, e);
    }
  }

  private static Path resolveDir(Function<String, String> cfg) {
    String override = firstNonBlank(cfg.apply(DIR_ENV), "");
    if (!override.isEmpty()) {
      return Paths.get(override);
    }
    Path def = Paths.get(DEFAULT_DIR);
    return Files.isDirectory(def) ? def : null;
  }

  private static void mirrorSystemProps(Map<String, Object> map) {
    for (Map.Entry<String, Object> e : map.entrySet()) {
      String k = e.getKey();
      String v = String.valueOf(e.getValue());
      if (v.isEmpty()) {
        continue;
      }
      if (System.getProperty(k) == null || System.getProperty(k).isBlank()) {
        System.setProperty(k, v);
      }
      if ("javax.net.ssl.trustStorePassword".equals(k)
          || "SSL_TRUSTSTORE_PASSWORD".equals(k)
          || "JAVAX_NET_SSL_TRUSTSTORE_PASSWORD".equals(k)) {
        System.setProperty("javax.net.ssl.trustStorePassword", v);
      }
      if ("javax.net.ssl.trustStore".equals(k) || "SSL_TRUSTSTORE".equals(k)) {
        System.setProperty("javax.net.ssl.trustStore", v);
      }
      if ("javax.net.ssl.trustStoreType".equals(k) || "SSL_TRUSTSTORE_TYPE".equals(k)) {
        System.setProperty("javax.net.ssl.trustStoreType", v);
      }
    }
  }

  private static void loadPropertiesFile(Path file, Map<String, Object> map) {
    if (!Files.isRegularFile(file)) {
      return;
    }
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      boolean first = true;
      while ((line = reader.readLine()) != null) {
        if (first) {
          if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
            line = line.substring(1);
          }
          first = false;
        }
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
          continue;
        }
        int eq = line.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        String key = line.substring(0, eq).trim();
        String val = line.substring(eq + 1).trim();
        if ((val.startsWith("\"") && val.endsWith("\""))
            || (val.startsWith("'") && val.endsWith("'"))) {
          val = val.substring(1, val.length() - 1);
        }
        if (!key.isEmpty()) {
          map.put(key, val);
        }
      }
    } catch (IOException ex) {
      System.err.println("[env] failed reading " + file + ": " + ex.getMessage());
    }
  }

  private static void loadPerKeyFiles(Path dir, Map<String, Object> map) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path p : stream) {
        if (!Files.isRegularFile(p)) {
          continue;
        }
        String name = p.getFileName().toString();
        if (isIgnoredPerKeyFile(name)) {
          continue;
        }
        try {
          String val = Files.readString(p, StandardCharsets.UTF_8).trim();
          if (!val.isEmpty()) {
            map.putIfAbsent(name, val);
          }
        } catch (IOException | SecurityException ex) {
          reportUnreadableSecretFile(p, ex);
        }
      }
    } catch (IOException ex) {
      System.err.println("[env] failed listing " + dir + ": " + ex.getMessage());
    }
  }

  /**
   * Files that are consumed explicitly (or are not secrets) must not be re-read as "file name = key".
   *
   * <p>Backup / editor artifacts must be skipped as well: {@code seal_dotenv_to_enc.py --apply} leaves
   * {@code mpay-secrets.enc.properties.bak.&lt;ts&gt;} next to the vault, and because {@code /run/secrets}
   * is mounted read-only into every container those artifacts would otherwise be injected as bogus
   * properties (and, when owned by another uid, produce misleading read failures).
   */
  static boolean isIgnoredPerKeyFile(String name) {
    if (name == null || name.isEmpty()) {
      return true;
    }
    String lower = name.toLowerCase(Locale.ROOT);
    if (name.startsWith(".")) {
      return true;
    }
    if (lower.equals("application.properties")
        || lower.equals("mpay-secrets.properties")
        || lower.equals("mpay-secrets.enc.properties")
        || lower.equals("master.key")
        || name.equals("MPAY_SECRETS_MASTER_KEY")
        || lower.endsWith(".md")) {
      return true;
    }
    return lower.contains(".bak")
        || lower.endsWith("~")
        || lower.endsWith(".orig")
        || lower.endsWith(".rej")
        || lower.endsWith(".save")
        || lower.endsWith(".swp")
        || lower.endsWith(".tmp");
  }

  /**
   * A secret file that exists but cannot be read silently disables that secret (observed as
   * {@code NOAUTH} from Redis when the mounted file was root-owned while the app runs as uid 1000), so
   * the reason is reported explicitly instead of echoing the bare path as the exception message does.
   */
  private static void reportUnreadableSecretFile(Path p, Exception ex) {
    System.err.println(
        "[env] failed reading secret file "
            + p
            + " ("
            + ex.getClass().getSimpleName()
            + "): "
            + ex.getMessage()
            + " — the secret is NOT loaded; ensure the file is readable by the container user");
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    if (b != null && !b.isBlank()) {
      return b;
    }
    return "";
  }
}
