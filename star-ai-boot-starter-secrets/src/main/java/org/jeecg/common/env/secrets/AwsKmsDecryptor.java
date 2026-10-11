package org.jeecg.common.env.secrets;

import java.util.Optional;
import java.util.function.Function;

/**
 * AWS KMS adapter placeholder for the §6 {@code KMS:} protocol.
 *
 * <p>Uses the same vendor-neutral HTTP JSON contract when a decrypt gateway is exposed via {@code
 * MPAY_KMS_ENDPOINT} + {@code MPAY_KMS_DECRYPT_PATH} + token (e.g. internal KMS proxy). Native AWS
 * SDK decrypt can be added behind this provider without changing the {@code KMS:} wire format.
 */
public final class AwsKmsDecryptor implements MpayKmsDecryptor {

  private final HttpJsonKmsDecryptor delegate;

  private AwsKmsDecryptor(HttpJsonKmsDecryptor delegate) {
    this.delegate = delegate;
  }

  public static Optional<MpayKmsDecryptor> tryCreate(Function<String, String> cfg) {
    String endpoint = HttpJsonKmsDecryptor.trim(cfg.apply("MPAY_KMS_ENDPOINT"));
    String path = HttpJsonKmsDecryptor.trim(cfg.apply("MPAY_KMS_DECRYPT_PATH"));
    if (endpoint.isEmpty() || path.isEmpty()) {
      return Optional.empty();
    }
    Function<String, String> overlay =
        k -> {
          if ("MPAY_KMS_AUTH_HEADER".equals(k)) {
            String h = HttpJsonKmsDecryptor.trim(cfg.apply(k));
            // AWS SigV4 gateways often use Authorization; allow override
            return h.isEmpty() ? "Authorization" : h;
          }
          if ("MPAY_KMS_REQUEST_JSON".equals(k)) {
            String b = HttpJsonKmsDecryptor.trim(cfg.apply(k));
            // Default closer to AWS Decrypt JSON shape when calling a proxy
            return b.isEmpty() ? "{\"CiphertextBlob\":\"%s\"}" : b;
          }
          return cfg.apply(k);
        };
    return HttpJsonKmsDecryptor.tryCreate("aws-kms", overlay).map(AwsKmsDecryptor::new);
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
