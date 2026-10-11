package org.jeecg.common.config.mqtoken;

/**
 * In scenarios without HTTP sessions such as asynchronous tasks, MQ, and scheduled jobs, the internal session token (ThreadLocal) is passed for calls such as Feign.
 */
public final class UserTokenContext {

  private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

  private UserTokenContext() {}

  public static void setToken(String token) {
    if (token == null || token.isEmpty()) {
      TOKEN.remove();
    } else {
      TOKEN.set(token);
    }
  }

  public static String getToken() {
    return TOKEN.get();
  }

  public static void remove() {
    TOKEN.remove();
  }
}
