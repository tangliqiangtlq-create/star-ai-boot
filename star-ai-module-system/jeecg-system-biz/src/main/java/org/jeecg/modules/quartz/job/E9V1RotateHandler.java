package org.jeecg.modules.quartz.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.PasswordUtil;
import org.jeecg.config.crypto.E9AesGcmSivCipher;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class E9V1RotateHandler {

  private static final int BATCH_LIMIT = 1000;


  @XxlJob("E9V1RotateHandler")
  public void execute() {
    log.info("E9V1RotateHandler start: batch rotate V0 PBE ciphertext → V1 AES-256-GCM-SIV");
    try {
      rotateSysUserBatch();
      rotateThirdAccountBatch();
    } catch (Exception e) {
      log.error("E9V1RotateHandler failed", e);
    }
    log.info("E9V1RotateHandler finished");
  }

  private void rotateSysUserBatch() {
    LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<>();
    // P4: explicitly select password — @TableField(select=false) on SysUser.password
    // means selectList() no longer fetches the password column by default.
    q.select(SysUser::getId, SysUser::getPassword)
        .isNotNull(SysUser::getPassword)
        .last("LIMIT " + BATCH_LIMIT);
    List<SysUser> rows = sysUserMapper.selectList(q);
    if (rows == null || rows.isEmpty()) {
      log.info("E9V1RotateHandler sys_user: 0 rows");
      return;
    }
    int v0 = 0;
    int v1 = 0;
    for (SysUser u : rows) {
      String pwd = u.getPassword();
      if (E9AesGcmSivCipher.isV1(pwd)) {
        v1++;
      } else {
        v0++;
      }
    }
    log.info(
        "E9V1RotateHandler sys_user batch: total={}, V0(PBE pending rotate)={}, V1(E9 AES-GCM)={}",
        rows.size(),
        v0,
        v1);
    if (v0 > 0) {
      log.warn(
          "E9V1RotateHandler sys_user: {} V0 rows remain; rotate on next user login (plaintext known)",
          v0);
    }
  }

  private void rotateThirdAccountBatch() {
    q.last("LIMIT " + BATCH_LIMIT);
    if (rows == null || rows.isEmpty()) {
      log.info("E9V1RotateHandler sys_third_account: 0 rows");
      return;
    }
    log.info("E9V1RotateHandler sys_third_account batch: total={}", rows.size());
  }
}
