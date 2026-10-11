package org.jeecg.common.env.secrets;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Huawei DEW/CSMS adapter for the §6 {@code KMS:} protocol.
 *
 * <p>Selected only when {@code MPAY_KMS_PROVIDER=huawei-csms|huawei-dew}. Credentials must be
 * supplied via {@code MPAY_KMS_*} (token file / token / AK+SK) — never via OBS brand keys.
 */
public final class HuaweiCsmsKmsDecryptor implements MpayKmsDecryptor {

  private final HttpJsonKmsDecryptor delegate;

  private HuaweiCsmsKmsDecryptor(HttpJsonKmsDecryptor delegate) {
    this.delegate = delegate;
  }

  public static Optional<MpayKmsDecryptor> tryCreate(Function<String, String> cfg) {
    String endpoint = HttpJsonKmsDecryptor.trim(cfg.apply("MPAY_KMS_ENDPOINT"));
    String projectId = HttpJsonKmsDecryptor.trim(cfg.apply("MPAY_KMS_PROJECT_ID"));
    if (endpoint.isEmpty() || projectId.isEmpty()) {
      return Optional.empty();
    }
    String path = HttpJsonKmsDecryptor.trim(cfg.apply("MPAY_KMS_DECRYPT_PATH"));
    if (path.isEmpty()) {
      path = "/v1.0/" + projectId + "/kms/decrypt-data";
    }

    String token = HttpJsonKmsDecryptor.readToken(cfg);
    if (token.isEmpty()) {
      token = fetchTokenByAkSk(cfg);
    }
    if (token.isEmpty()) {
      return Optional.empty();
    }

    final String tokenFinal = token;
    final String pathFinal = path;
    Function<String, String> overlay =
        k -> {
          if ("MPAY_KMS_DECRYPT_PATH".equals(k)) {
            return pathFinal;
          }
          if ("MPAY_KMS_TOKEN".equals(k)) {
            return tokenFinal;
          }
          if ("MPAY_KMS_AUTH_HEADER".equals(k)) {
            String h = HttpJsonKmsDecryptor.trim(cfg.apply(k));
            return h.isEmpty() ? "X-Auth-Token" : h;
          }
          if ("MPAY_KMS_REQUEST_JSON".equals(k)) {
            String b = HttpJsonKmsDecryptor.trim(cfg.apply(k));
            return b.isEmpty() ? "{\"cipher_text\":\"%s\"}" : b;
          }
          return cfg.apply(k);
        };

    return HttpJsonKmsDecryptor.tryCreate("huawei-csms", overlay).map(HuaweiCsmsKmsDecryptor::new);
  }

  /**
   * Optional IAM token via {@code MPAY_KMS_AK}/{@code MPAY_KMS_SK}.
   *
   * <p>{@code MPAY_KMS_IAM_ENDPOINT} overrides the default IAM URL built from {@code
   * MPAY_KMS_REGION}.
   */
  private static String fetchTokenByAkSk(Function<String, String> cfg) {
    String ak = HttpJsonKmsDecryptor.trim(cfg.apply("MPAY_KMS_AK"));
    String sk = HttpJsonKmsDecryptor.trim(cfg.apply("MPAY_KMS_SK"));
    if (ak.isEmpty() || sk.isEmpty()) {
      return "";
    }
    String region = HttpJsonKmsDecryptor.trim(cfg.apply("MPAY_KMS_REGION"));
    if (region.isEmpty()) {
      region = "ap-southeast-3";
    }
    String iam = HttpJsonKmsDecryptor.trim(cfg.apply("MPAY_KMS_IAM_ENDPOINT"));
    if (iam.isEmpty()) {
      iam = "https://iam." + region + ".myhuaweicloud.com/v3/auth/tokens";
    }
    String scopeProject = HttpJsonKmsDecryptor.trim(cfg.apply("MPAY_KMS_IAM_SCOPE_PROJECT"));
    if (scopeProject.isEmpty()) {
      scopeProject = region;
    }
    String body =
        "{\"auth\":{\"identity\":{\"methods\":[\"hw_ak_sk\"],\"hw_ak_sk\":{"
            + "\"access\":{\"key\":\""
            + HttpJsonKmsDecryptor.jsonEscape(ak)
            + "\"},\"secret\":{\"key\":\""
            + HttpJsonKmsDecryptor.jsonEscape(sk)
            + "\"}}},\"scope\":{\"project\":{\"name\":\""
            + HttpJsonKmsDecryptor.jsonEscape(scopeProject)
            + "\"}}}}";
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(iam))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new IllegalStateException(
            "KMS IAM token HTTP " + resp.statusCode() + " (check MPAY_KMS_AK/SK + IAM policy)");
      }
      String token = resp.headers().firstValue("X-Subject-Token").orElse("");
      if (token.isEmpty()) {
        token = resp.headers().firstValue("X-Auth-Token").orElse("");
      }
      if (token.isEmpty()) {
        throw new IllegalStateException("KMS IAM token response missing X-Subject-Token");
      }
      return token;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("KMS IAM token fetch interrupted", e);
    } catch (IOException e) {
      throw new IllegalStateException("KMS IAM token fetch failed: " + e.getMessage(), e);
    }
  }

  @Override
  public String providerName() {
    return delegate.providerName();
  }

  @Override
  public String decrypt(String ciphertextOrSecretId) {
    return delegate.decrypt(ciphertextOrSecretId);
  }
}
