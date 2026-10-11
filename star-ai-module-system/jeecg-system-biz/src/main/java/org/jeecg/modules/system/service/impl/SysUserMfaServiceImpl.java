package org.jeecg.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import java.util.Date;
import java.util.Optional;
import org.jeecg.modules.system.entity.SysUserMfa;
import org.jeecg.modules.system.mapper.SysUserMfaMapper;
import org.jeecg.modules.system.service.ISysUserMfaService;
import org.jeecg.modules.system.service.crypto.MfaSeedCryptoService;
import org.springframework.stereotype.Service;

@Service
public class SysUserMfaServiceImpl extends ServiceImpl<SysUserMfaMapper, SysUserMfa>
    implements ISysUserMfaService {

  private static final String MFA_TYPE_TOTP = "TOTP";

  @Resource
  private MfaSeedCryptoService mfaSeedCryptoService;

  @Override
  public Optional<SysUserMfa> findEnabledTotp(String userId, Integer tenantId) {
    SysUserMfa mfa =
        this.getOne(
            new LambdaQueryWrapper<SysUserMfa>()
                .eq(SysUserMfa::getTenantId, tenantId)
                .eq(SysUserMfa::getUserId, userId)
                .eq(SysUserMfa::getMfaType, MFA_TYPE_TOTP)
                .eq(SysUserMfa::getEnabled, 1)
                .eq(SysUserMfa::getDelFlag, 0),
            false);
    return Optional.ofNullable(mfa);
  }

  @Override
  public Optional<SysUserMfa> findAnyTotp(String userId, Integer tenantId) {
    SysUserMfa mfa =
        this.getOne(
            new LambdaQueryWrapper<SysUserMfa>()
                .eq(SysUserMfa::getTenantId, tenantId)
                .eq(SysUserMfa::getUserId, userId)
                .eq(SysUserMfa::getMfaType, MFA_TYPE_TOTP)
                .eq(SysUserMfa::getDelFlag, 0),
            false);
    return Optional.ofNullable(mfa);
  }

  @Override
  public boolean isLocked(SysUserMfa mfa, Date now) {
    return mfa != null && mfa.getLockedUntil() != null && mfa.getLockedUntil().after(now);
  }

  @Override
  public SysUserMfa createOrUpdateTotp(
      String userId, Integer tenantId, String plainSeed, boolean enabled) {
    if (userId == null || tenantId == null || plainSeed == null) {
      throw new IllegalArgumentException("userId/tenantId/plainSeed required");
    }
    String wrapped = mfaSeedCryptoService.wrapSeed(plainSeed, userId, tenantId);
    Optional<SysUserMfa> existing = findAnyTotp(userId, tenantId);
    SysUserMfa row = existing.orElseGet(SysUserMfa::new);
    row.setUserId(userId);
    row.setTenantId(tenantId);
    row.setMfaType(MFA_TYPE_TOTP);
    row.setSecretEnc(wrapped);
    row.setEnabled(enabled ? 1 : 0);
    row.setDelFlag(0);
    if (row.getId() == null) {
      row.setCreateTime(new Date());
      row.setCreateBy(userId);
    } else {
      row.setUpdateTime(new Date());
      row.setUpdateBy(userId);
    }
    this.saveOrUpdate(row);
    return row;
  }

  @Override
  public String decryptSeed(SysUserMfa mfa) {
    if (mfa == null || mfa.getSecretEnc() == null) {
      return null;
    }
    return mfaSeedCryptoService.unwrapSeed(mfa.getSecretEnc(), mfa.getUserId(), mfa.getTenantId());
  }
}
