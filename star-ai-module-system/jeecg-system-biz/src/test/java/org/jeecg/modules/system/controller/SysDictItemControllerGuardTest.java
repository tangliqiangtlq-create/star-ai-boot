package org.jeecg.modules.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jeecg.modules.system.controller.SysDictItemController.ProtectedWrite;
import org.junit.jupiter.api.Test;

/**
 * Gate evidence for the dict-write security guard (C8 + security-steering dicts).
 *
 * <p>These cases pin the decision itself, so the C8-1 gate can no longer be satisfied by a stray
 * log/comment string: the assertions below fail if the classification stops protecting a key (or
 * starts protecting a business label).
 */
class SysDictItemControllerGuardTest {

  @Test
  void protectsTheAdminRoleKey() {
    assertEquals(
        ProtectedWrite.ADMIN_ROLE_KEY,
        SysDictItemController.classifyProtectedDictWrite("sys_config", "admin_role"));
    // case-insensitive on both halves
    assertEquals(
        ProtectedWrite.ADMIN_ROLE_KEY,
        SysDictItemController.classifyProtectedDictWrite("sys_config", "ADMIN_ROLE"));
    assertEquals(
        ProtectedWrite.ADMIN_ROLE_KEY,
        SysDictItemController.classifyProtectedDictWrite("SYS_CONFIG", "admin_role"));
  }

  @Test
  void protectsSecuritySteeringSysConfigKeys() {
    assertEquals(
        ProtectedWrite.SECURITY_CONFIG,
        SysDictItemController.classifyProtectedDictWrite("sys_config", "ops_notify_email"));
    assertEquals(
        ProtectedWrite.SECURITY_CONFIG,
        SysDictItemController.classifyProtectedDictWrite("sys_config", "OPS_NOTIFY_EMAIL"));
  }

  @Test
  void protectsTheStepUpAmountThresholdDict() {
    assertEquals(
        ProtectedWrite.SECURITY_CONFIG,
        SysDictItemController.classifyProtectedDictWrite(
            "transaction_amount_threshold", "withdrawalAccount"));
    assertEquals(
        ProtectedWrite.SECURITY_CONFIG,
        SysDictItemController.classifyProtectedDictWrite(
            "transaction_amount_threshold", "depositAccount_USDT"));
  }

  @Test
  void leavesBusinessLabelsWritableByTheOpsRole() {
    assertEquals(
        ProtectedWrite.NONE,
        SysDictItemController.classifyProtectedDictWrite("user_label", "1"));
    assertEquals(
        ProtectedWrite.NONE,
        SysDictItemController.classifyProtectedDictWrite("sys_config", "dev_email"));
    assertEquals(
        ProtectedWrite.NONE,
        SysDictItemController.classifyProtectedDictWrite(
            "sys_config", "Policy_and_Regulation_Document"));
    assertEquals(
        ProtectedWrite.NONE,
        SysDictItemController.classifyProtectedDictWrite("role_list", "super_admin"));
    assertEquals(
        ProtectedWrite.NONE,
        SysDictItemController.classifyProtectedDictWrite("ledger_approver_role", "CHECKER"));
  }

  @Test
  void unknownOrMissingDictCodeIsNotProtected() {
    assertEquals(
        ProtectedWrite.NONE, SysDictItemController.classifyProtectedDictWrite(null, "admin_role"));
    assertEquals(
        ProtectedWrite.NONE, SysDictItemController.classifyProtectedDictWrite("  ", "admin_role"));
    assertEquals(
        ProtectedWrite.NONE, SysDictItemController.classifyProtectedDictWrite("sys_config", null));
  }
}
