package com.xxl.job.admin.core.util;

import com.xxl.job.core.biz.model.ReturnT;

public final class ReturnTCompat {

  private ReturnTCompat() {}

  public static <T> ReturnT<T> success() {
    return new ReturnT<>(ReturnT.SUCCESS_CODE, null);
  }

  public static <T> ReturnT<T> success(T content) {
    return new ReturnT<>(ReturnT.SUCCESS_CODE, null, content);
  }

  public static <T> ReturnT<T> fail() {
    return new ReturnT<>(ReturnT.FAIL_CODE, null);
  }

  public static <T> ReturnT<T> fail(String msg) {
    return new ReturnT<>(ReturnT.FAIL_CODE, msg);
  }
}
