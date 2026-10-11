package org.jeecg.modules.system.service;

import org.jeecg.modules.system.entity.SysUser;

/** Account-created email (ACCOUNT_CREATED + PasswordResetLinkFacade). */
public interface IAccountCreatedEmailService {

  void scheduleAfterCommit(
      SysUser user, String source, String creatorUserId, String remark);
}
