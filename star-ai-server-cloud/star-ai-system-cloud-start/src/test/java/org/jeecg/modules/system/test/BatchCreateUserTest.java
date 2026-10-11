package org.jeecg.modules.system.test;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;
import org.jeecg.JeecgSystemCloudApplication;
import org.jeecg.common.util.PasswordUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserRole;
import org.jeecg.modules.system.mapper.SysUserRoleMapper;
import org.jeecg.modules.system.service.ISysUserService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = JeecgSystemCloudApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BatchCreateUserTest {
  private static final Logger log = LoggerFactory.getLogger(BatchCreateUserTest.class);

  /** V2.16-2: no hardcoded test credential in the repo — inject it via env. */
  private static final String BATCH_USER_PASSWORD = System.getenv("TEST_OTP_FIXED_CODE");

  @Autowired private ISysUserService sysUserService;

  @Autowired private SysUserRoleMapper sysUserRoleMapper;

  @Test
  public void batchCreateUsers() {
    org.junit.Assume.assumeTrue(
        "V2.16-2: TEST_OTP_FIXED_CODE env not set; skipping batch user creation",
        BATCH_USER_PASSWORD != null && !BATCH_USER_PASSWORD.isBlank());
    String roleId = "1835141200229904386";
    String inviterId = "1934292771926048769";

    log.debug("Starting batch creation of users...");

    for (int i = 0; i < 20; i++) {
      try {
        long timestamp = System.currentTimeMillis();
        SysUser user = new SysUser();
        String username = "auto_user_" + timestamp + "_" + i;
        user.setUsername(username);
        user.setRealname("Auto User " + i);
        user.setIsSender("Y");

        String salt = oConvertUtils.randomGen(8);
        user.setSalt(salt);

        String password = PasswordUtil.encrypt(username, BATCH_USER_PASSWORD, salt);
        user.setPassword(password);

        user.setInviterId(inviterId);
        user.setStatus(1);
        user.setDelFlag(0);
        user.setCreateTime(new Date());
        user.setPhone("138" + String.format("%08d", i + timestamp % 10000));
        user.setKycStatus("2");

        sysUserService.save(user);

        if (user.getId() == null) {
          System.err.println("Failed to save user " + username + ", ID is null");
          continue;
        }

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(roleId);
        sysUserRoleMapper.insert(userRole);

        log.debug("Successfully created user: " + username + " (ID: " + user.getId() + ")");
      } catch (Exception e) {
        System.err.println("Error creating user at index " + i);
        log.error("operation failed", e);
      }
    }

    log.debug("Batch creation completed.");
  }
}
