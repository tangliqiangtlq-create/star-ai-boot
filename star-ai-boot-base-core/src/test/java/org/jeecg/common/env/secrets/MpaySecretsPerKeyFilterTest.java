package org.jeecg.common.env.secrets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@code /run/secrets} is bind-mounted read-only into every container, so whatever the sealing tooling
 * leaves in that directory ends up in the Spring {@code Environment} ("file name = key name").
 *
 * <p>Regression: {@code seal_dotenv_to_enc.py --apply} drops {@code
 * mpay-secrets.enc.properties.bak.<ts>} next to the vault; it was injected as a bogus property and, being
 * root-owned while the app runs as uid 1000, produced {@code failed reading secret file ...} noise.
 */
class MpaySecretsPerKeyFilterTest {

  @Test
  void skipsFilesConsumedExplicitly() {
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("application.properties"));
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("mpay-secrets.properties"));
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("mpay-secrets.enc.properties"));
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("master.key"));
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("MPAY_SECRETS_MASTER_KEY"));
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("README.md"));
  }

  @Test
  void skipsBackupAndEditorArtifacts() {
    assertTrue(
        MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile(
            "mpay-secrets.enc.properties.bak.20260930_152012"));
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("secrets.bak"));
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("master.key~"));
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("master.key.orig"));
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile(".hidden.key"));
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile(".swp"));
  }

  @Test
  void skipsBlankNames() {
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile(null));
    assertTrue(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile(""));
  }

  @Test
  void keepsRealSecretKeys() {
    assertFalse(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("jeecg.redisson.password"));
    assertFalse(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("spring.data.redis.password"));
    assertFalse(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("spring.redis.password"));
    assertFalse(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("SPRING_REDIS_PASSWORD"));
    assertFalse(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("OTP_PEPPER"));
    assertFalse(MpaySecretsEnvironmentPostProcessor.isIgnoredPerKeyFile("GATEWAY_SECRET"));
  }
}
