package org.jeecg.config.feign;

import cn.hutool.core.util.StrUtil;
import org.jeecg.common.config.feign.FeignAuthRetrySupport;
import org.jeecg.common.config.mqtoken.UserTokenContext;

public final class FeignAuthRetryConditions {

  private FeignAuthRetryConditions() {}

  public static boolean shouldEmitRetryableForHttpStatus(
      int status, String requestUrl, FeignAuthRetryProperties props) {
    if (props == null || !props.isEnabled()) {
      return false;
    }
    if (status != 401 && status != 403) {
      return false;
    }
    if (FeignAuthRetrySupport.isSkipAuthRetry()) {
      return false;
    }
    return StrUtil.isNotBlank(UserTokenContext.getToken());
  }
}
