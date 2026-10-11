package org.jeecg.common.env.secrets;

/**
 * Pluggable KMS/HSM decryptor for wire values prefixed with {@code KMS:} (§6 envelope).
 *
 * <p>Provider is selected only by {@code MPAY_KMS_PROVIDER} — never inferred from cloud brand
 * env vars (e.g. do not bind to {@code HUAWEI_OBS_*}).
 *
 * <ul>
 *   <li>{@code none} — reject {@code KMS:} values (appendix B.8 default plaintext {@code .env}/secrets path)
 *   <li>{@code local-aes} — local AES-256-GCM bootstrap ({@code AESGCM:} / {@code KMS:} + master key)
 *   <li>{@code huawei-csms} / {@code huawei-dew} — Huawei DEW/CSMS (appendix B.8 alternate carrier)
 *   <li>{@code aws-kms} — AWS KMS adapter (via HTTP gateway; credentials only {@code MPAY_KMS_*})
 * </ul>
 */
public interface MpayKmsDecryptor {

  /** @return plaintext secret value */
  String decrypt(String ciphertextOrSecretId);

  String providerName();
}
