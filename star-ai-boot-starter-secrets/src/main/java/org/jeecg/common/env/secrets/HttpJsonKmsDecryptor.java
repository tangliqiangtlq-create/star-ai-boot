package org.jeecg.common.env.secrets;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared HTTP JSON decrypt client used by cloud KMS adapters ({@code huawei-*} / {@code aws-kms}).
 *
 * <p>Not a Compose §B.8 carrier by itself — appendix B.8 is {@code .env}(600)/{@code /run/secrets} or
 * Huawei DEW/CSMS. Do not select a standalone {@code http-json} provider.
 *
 * <p>Config (all {@code MPAY_KMS_*} — no cloud-brand fallbacks):
 *
 * <ul>
 *   <li>{@code MPAY_KMS_ENDPOINT} — base URL, required
 *   <li>{@code MPAY_KMS_DECRYPT_PATH} — path; may contain {@code {project_id}}
 *   <li>{@code MPAY_KMS_PROJECT_ID} — optional path substitution
 *   <li>{@code MPAY_KMS_TOKEN_FILE} / {@code MPAY_KMS_TOKEN} — bearer / auth token
 *   <li>{@code MPAY_KMS_AUTH_HEADER} — default {@code X-Auth-Token}
 *   <li>{@code MPAY_KMS_REQUEST_JSON} — default {@code {"cipher_text":"%s"}} ({@code %s} = cipher)
 * </ul>
 */
public final class HttpJsonKmsDecryptor implements MpayKmsDecryptor {

  private static final Pattern JSON_PLAIN =
      Pattern.compile("\"(?:plain_text|plaintext|Plaintext|plaintext_blob)\"\\s*:\\s*\"([^\"]+)\"");

  private final String providerName;
  private final String endpoint;
  private final String decryptUrl;
  private final String token;
  private final String authHeader;
  private final String requestJsonTemplate;

  public HttpJsonKmsDecryptor(
      String providerName,
      String endpoint,
      String decryptUrl,
      String token,
      String authHeader,
      String requestJsonTemplate) {
    this.providerName = providerName;
    this.endpoint = endpoint;
    this.decryptUrl = decryptUrl;
    this.token = token;
    this.authHeader = authHeader;
    this.requestJsonTemplate = requestJsonTemplate;
  }

  public static Optional<HttpJsonKmsDecryptor> tryCreate(
      String providerName, Function<String, String> cfg) {
    String endpoint = trim(cfg.apply("MPAY_KMS_ENDPOINT"));
    if (endpoint.isEmpty()) {
      return Optional.empty();
    }
    String projectId = trim(cfg.apply("MPAY_KMS_PROJECT_ID"));
    String path = trim(cfg.apply("MPAY_KMS_DECRYPT_PATH"));
    if (path.isEmpty()) {
      return Optional.empty();
    }
    if (!projectId.isEmpty()) {
      path = path.replace("{project_id}", projectId);
    }
    String token = readToken(cfg);
    if (token.isEmpty()) {
      return Optional.empty();
    }
    String authHeader = trim(cfg.apply("MPAY_KMS_AUTH_HEADER"));
    if (authHeader.isEmpty()) {
      authHeader = "X-Auth-Token";
    }
    String bodyTpl = trim(cfg.apply("MPAY_KMS_REQUEST_JSON"));
    if (bodyTpl.isEmpty()) {
      bodyTpl = "{\"cipher_text\":\"%s\"}";
    }
    String url = endpoint.replaceAll("/$", "") + (path.startsWith("/") ? path : "/" + path);
    return Optional.of(
        new HttpJsonKmsDecryptor(providerName, endpoint, url, token, authHeader, bodyTpl));
  }

  static String readToken(Function<String, String> cfg) {
    String file = trim(cfg.apply("MPAY_KMS_TOKEN_FILE"));
    if (!file.isEmpty()) {
      try {
        return Files.readString(Path.of(file), StandardCharsets.UTF_8).trim();
      } catch (IOException e) {
        throw new IllegalStateException("Cannot read MPAY_KMS_TOKEN_FILE: " + file, e);
      }
    }
    return trim(cfg.apply("MPAY_KMS_TOKEN"));
  }

  static String trim(String s) {
    return s == null ? "" : s.trim();
  }

  static String jsonEscape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  @Override
  public String providerName() {
    return providerName;
  }

  @Override
  public String decrypt(String ciphertextOrSecretId) {
    String cipher = ciphertextOrSecretId;
    if (cipher.startsWith("KMS:")) {
      cipher = cipher.substring(4).trim();
    }
    String body = String.format(requestJsonTemplate, jsonEscape(cipher));
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(decryptUrl))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/json")
              .header(authHeader, token)
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new IllegalStateException(
            "KMS decrypt HTTP " + resp.statusCode() + " provider=" + providerName + " endpoint=" + endpoint);
      }
      Matcher m = JSON_PLAIN.matcher(resp.body());
      if (!m.find()) {
        throw new IllegalStateException(
            "KMS decrypt response missing plaintext field; provider=" + providerName);
      }
      String plain = m.group(1);
      try {
        return new String(Base64.getDecoder().decode(plain), StandardCharsets.UTF_8);
      } catch (IllegalArgumentException ignore) {
        return plain;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("KMS decrypt interrupted", e);
    } catch (IOException e) {
      throw new IllegalStateException("KMS decrypt failed: " + e.getMessage(), e);
    }
  }
}
