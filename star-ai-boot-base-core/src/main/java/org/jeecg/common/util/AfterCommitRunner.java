package org.jeecg.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

@Slf4j
public final class AfterCommitRunner {

  private AfterCommitRunner() {}

  public static boolean isActiveTransaction() {
    return TransactionSynchronizationManager.isActualTransactionActive();
  }

  public static void runAfterCommit(Runnable action) {
    if (action == null) return;
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      log.debug(
          "AfterCommitRunner: no active transaction synchronization — running action on caller thread.");
      action.run();
      return;
    }
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              action.run();
            } catch (RuntimeException ex) {
              log.warn(
                  "AfterCommitRunner: action threw after commit (commit already done): {}",
                  ex.getMessage(),
                  ex);
            }
          }
        });
  }

  public static <T> void runAfterCommitAsync(
      Supplier<T> readWithinTx, java.util.function.Consumer<T> actionAfterCommit) {
    if (actionAfterCommit == null) return;
    T value = readWithinTx == null ? null : readWithinTx.get();
    runAfterCommit(() -> actionAfterCommit.accept(value));
  }
}
