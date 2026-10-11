package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.Date;
import java.util.Optional;
import org.jeecg.modules.system.entity.SysUserMfa;

public interface ISysUserMfaService extends IService<SysUserMfa> {

  Optional<SysUserMfa> findEnabledTotp(String userId, Integer tenantId);

  Optional<SysUserMfa> findAnyTotp(String userId, Integer tenantId);

  boolean isLocked(SysUserMfa mfa, Date now);

  /**
   * Persist a TOTP factor with column-encrypted seed; writes {@link
   * org.jeecg.modules.system.service.crypto.MfaSeedCryptoService#MFA_CIPHER_PREFIX} format.
   */
  SysUserMfa createOrUpdateTotp(String userId, Integer tenantId, String plainSeed, boolean enabled);

  /** Returns the decrypted plaintext TOTP seed; null for legacy (pre-migration) rows. */
  String decryptSeed(SysUserMfa mfa);
}

