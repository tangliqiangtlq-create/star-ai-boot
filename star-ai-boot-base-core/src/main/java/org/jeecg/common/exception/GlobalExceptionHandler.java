package org.jeecg.common.exception;

import cn.hutool.json.JSONUtil;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.LocaleResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @Autowired private MessageSource messageSource;

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<String> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException e) {

    log.warn("payload too large");
    Locale locale = LocaleResolver.getCurrLocale();
    Result result =
        Result.error(413, messageSource.getMessage("common.file_size_exceeded", null, locale));

    // 拒绝码统一标准: upload too large is a client error (413), never HTTP 200 + code 500.
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(JSONUtil.toJsonStr(result));
  }
}
