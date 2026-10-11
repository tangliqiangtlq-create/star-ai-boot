package org.jeecg.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.LocaleResolver;
import org.jeecg.common.util.RocketMQUtil;
import org.jeecg.common.util.log.SensitiveDataTurboFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.PoolException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

/** Exception Handler @Author scott @Date 2019 */
@RestControllerAdvice
@Slf4j
public class JeecgBootExceptionHandler {
  private static final java.util.concurrent.atomic.LongAdder AUTHZ_403_COUNTER = new java.util.concurrent.atomic.LongAdder();
  private static final long AUTHZ_403_SPIKE_THRESHOLD = 1000L;

  @Autowired private MessageSource messageSource;

  @Autowired private RocketMQUtil rocketMQUtil;

  @Value("${spring.profiles.active}")
  private String active;

  /** ProcessCustomException */
  @ExceptionHandler(JeecgBootException.class)
  public Result<?> handleJeecgBootException(JeecgBootException e, HttpServletResponse response) {
    int code = e.getErrCode();
    // 拒绝码统一标准 (rejection-code standard): business rejections must surface the matching HTTP
    // status — 400 bad-request / 401 unauthenticated / 403 forbidden / 404 not-found / 429 rate
    // limited — never HTTP 200 + a business code. Keeps the JSON body contract unchanged
    // (success=false, code=<same>). Real server errors (code=500) intentionally keep the legacy
    // HTTP 200 + code 500 shape (decision D1 = A).
    if (code == 400 || code == 401 || code == 403 || code == 404 || code == 429) {
      response.setStatus(code);
      if (code == 403) {
        // Feed the same AUTHZ spike counter as Sa-Token permission/role denials (L3 observability).
        onAuthz403Spike("jeecgBoot403");
      }
      // Expected rejections — warn without stack spam.
      log.warn(
          "operation failed code={} msg={}",
          code,
          SensitiveDataTurboFilter.redact(e.getMessage()));
    } else {
      log.error("operation failed msg={}", SensitiveDataTurboFilter.redact(e.getMessage()), e);
    }
    return Result.error(code, e.getMessage());
  }

  @ExceptionHandler(SecurityException.class)
  public Result<?> handleSecurityException(SecurityException e) {
    log.error("operation failed msg={}", SensitiveDataTurboFilter.redact(e.getMessage()), e);
    Locale locale = LocaleResolver.getCurrLocale();
    String exceptionValue =
        e.getMessage() != null && e.getMessage().contains(":")
            ? e.getMessage().split(":", 2)[1].trim()
            : StrUtil.blankToDefault(e.getMessage(), "");
    return Result.error(
        msg("common.security_exception", new Object[] {exceptionValue}, locale, "Security exception: " + exceptionValue));
  }

  /** Handle custom microservice exceptions */
  @ExceptionHandler(JeecgCloudException.class)
  public Result<?> handleJeecgCloudException(JeecgCloudException e) {
    log.error("operation failed msg={}", SensitiveDataTurboFilter.redact(e.getMessage()), e);
    return Result.error(e.getMessage());
  }

  /** ProcessCustomException */
  @ExceptionHandler(JeecgBoot401Exception.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public Result<?> handleJeecgBoot401Exception(JeecgBoot401Exception e) {
    log.error("operation failed msg={}", SensitiveDataTurboFilter.redact(e.getMessage()), e);
    // Must set success=false — default Result(code,msg) keeps success=true and upload UIs
    // would persist message (e.g. login-timeout i18n) as file objectId.
    return Result.error(401, e.getMessage());
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public Result<?> handlerNoFoundException(Exception e) {
    log.error("operation failed msg={}", SensitiveDataTurboFilter.redact(e.getMessage()), e);

    Locale locale = LocaleResolver.getCurrLocale();

    return Result.error(404, msg("common.path_not_exist", locale, "Path does not exist"));
  }

  @ExceptionHandler(SizeLimitExceededException.class)
  @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
  public Result<?> handleSizeLimitExceededException(SizeLimitExceededException e) {
    log.warn("payload too large msg={}", SensitiveDataTurboFilter.redact(e.getMessage()));
    Locale locale = LocaleResolver.getCurrLocale();
    // Upload too large is a client error (413), not HTTP 200 + business 500.
    return Result.error(413, msg("common.file_size_exceeded", locale, "File size exceeded"));
  }

  @ExceptionHandler(DuplicateKeyException.class)
  public Result<?> handleDuplicateKeyException(DuplicateKeyException e) {
    log.error("operation failed msg={}", SensitiveDataTurboFilter.redact(e.getMessage()), e);
    Locale locale = LocaleResolver.getCurrLocale();
    return Result.error(500, msg("common.oops_issues", locale, "Ops, encountered some issues, please try again later!"));
  }

  /** Section 7 / G04: not logged in must return HTTP 401 (not only body.code=401). */
  @ExceptionHandler({NotLoginException.class})
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public Result<?> handleAuthorizationException(NotLoginException e) {
    log.error("operation failed msg={}", SensitiveDataTurboFilter.redact(e.getMessage()), e);
    Locale locale = LocaleResolver.getCurrLocale();
    // Branch on exception type and return different error messages
    if (StrUtil.contains(e.getMessage(), "Token has been kicked offline")) {
      // Token kicked out by another login
      return Result.error(401, msg("common.token_kicked_out", locale, "Login expired, please sign in again"));
    } else {
      // Other not-logged-in cases
      return Result.error(
          401, msg("common.authentication_exception", locale, "Authentication failed, please sign in again"));
    }
  }

  /** Sa-Token permission deny → JSON 403 (avoid generic handler + missing i18n → HTML 500). */
  @ExceptionHandler(NotPermissionException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public Result<?> handleNotPermissionException(NotPermissionException e) {
    onAuthz403Spike("perm=" + e.getPermission());
    log.error("operation failed msg={}", SensitiveDataTurboFilter.redact(e.getMessage()), e);
    Locale locale = LocaleResolver.getCurrLocale();
    String permission = e.getPermission();
    return Result.error(
        403,
        msg(
            "common.not_permission",
            new Object[] {permission},
            locale,
            "No permission: " + permission));
  }

  /**
   * §7/G10 + G14: role deny must also feed AUTHZ spike ALERT (Sa-Token {@link NotRoleException} is
   * distinct from {@link NotPermissionException}).
   */
  @ExceptionHandler(NotRoleException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public Result<?> handleNotRoleException(NotRoleException e) {
    onAuthz403Spike("role=" + e.getRole());
    log.error("operation failed msg={}", SensitiveDataTurboFilter.redact(e.getMessage()), e);
    Locale locale = LocaleResolver.getCurrLocale();
    String role = e.getRole();
    return Result.error(
        403,
        msg(
            "common.not_permission",
            new Object[] {role},
            locale,
            "No role: " + role));
  }

  private static void onAuthz403Spike(String detail) {
    AUTHZ_403_COUNTER.increment();
    long total403 = AUTHZ_403_COUNTER.sum();
    if (total403 >= AUTHZ_403_SPIKE_THRESHOLD) {
      log.error(
          "ALERT_AUTHZ_403_SPIKE total403={} threshold={} {}",
          total403,
          AUTHZ_403_SPIKE_THRESHOLD,
          detail);
      AUTHZ_403_COUNTER.reset();
    }
  }

  /** Bean-validation failure on the request body is a client error: HTTP 400, not 200 + code 500. */
  @ExceptionHandler({MethodArgumentNotValidException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<?> handleAuthorizationException(MethodArgumentNotValidException e) {
    log.warn("bad request msg={}", SensitiveDataTurboFilter.redact(e.getMessage()));
    Locale locale = LocaleResolver.getCurrLocale();
    return Result.error(400, msg("common.invalid.param", locale, "Invalid parameters"));
  }

  /**
   * Missing required {@code @RequestParam} / header / path variable (e.g. {@code
   * /sys/common/file?token=x}) is a client error. It used to fall through to {@link
   * #handleException(Exception)} and answer HTTP 200 with a business 500.
   */
  @ExceptionHandler(ServletRequestBindingException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<?> handleServletRequestBindingException(ServletRequestBindingException e) {
    log.warn("bad request msg={}", SensitiveDataTurboFilter.redact(e.getMessage()));
    Locale locale = LocaleResolver.getCurrLocale();
    return Result.error(400, msg("common.invalid.param", locale, "Invalid parameters"));
  }

  /** Unparsable param / path-variable type (e.g. {@code pageSize=abc}) is a client error. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
    log.warn("bad request msg={}", SensitiveDataTurboFilter.redact(e.getMessage()));
    Locale locale = LocaleResolver.getCurrLocale();
    return Result.error(400, msg("common.invalid.param", locale, "Invalid parameters"));
  }

  /** Malformed request body must not be reported as a server-side 500 with HTTP 200. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
    log.warn("bad request msg={}", SensitiveDataTurboFilter.redact(e.getMessage()));
    Locale locale = LocaleResolver.getCurrLocale();
    return Result.error(400, msg("common.invalid.param", locale, "Invalid parameters"));
  }

  /** NullPointerException is almost always a bug, log warn without stack spam (avoid DoS via big stack traces). */
  @ExceptionHandler(NullPointerException.class)
  public Result<?> handleNullPointerException(NullPointerException e) {
    log.warn("operation failed NullPointerException msg={}",
        SensitiveDataTurboFilter.redact(e.getMessage()));
    Locale locale = LocaleResolver.getCurrLocale();
    return Result.error(msg("common.oops_issues", locale, "Ops, encountered some issues, please try again later!"));
  }

  /** IllegalArgumentException is a client-side bad argument — answer HTTP 400, never 500. */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<?> handleIllegalArgumentException(IllegalArgumentException e) {
    log.warn("bad request msg={}", SensitiveDataTurboFilter.redact(e.getMessage()));
    Locale locale = LocaleResolver.getCurrLocale();
    return Result.error(400, msg("common.invalid.param", locale, "Invalid parameters"));
  }

  /** IllegalStateException is usually a broken precondition — treat as client error when the message looks like validation. */
  @ExceptionHandler(IllegalStateException.class)
  public Result<?> handleIllegalStateException(IllegalStateException e) {
    log.warn("operation failed IllegalStateException msg={}",
        SensitiveDataTurboFilter.redact(e.getMessage()));
    Locale locale = LocaleResolver.getCurrLocale();
    return Result.error(msg("common.oops_issues", locale, "Ops, encountered some issues, please try again later!"));
  }

  @ExceptionHandler(Exception.class)
  public Result<?> handleException(Exception e) {
    Locale locale = LocaleResolver.getCurrLocale();
    String errMsg = SensitiveDataTurboFilter.redact(e.getMessage());
    log.error("operation failed msg={}", errMsg, e);
    if (e instanceof NotLoginException) {
      NotLoginException notLoginException = (NotLoginException) e;
      // Branch on exception type and return different error messages
      if ("KICK_OUT".equals(notLoginException.getType())) {
        // Token kicked out by another login
        return Result.error(401, msg("common.token_kicked_out", locale, "Login expired, please sign in again"));
      } else {
        // Other not-logged-in cases
        return Result.error(
            401, msg("common.authentication_exception", locale, "Authentication failed, please sign in again"));
      }
    } else if (e instanceof NotPermissionException) {
      NotPermissionException npe = (NotPermissionException) e;
      return Result.error(
          403,
          msg(
              "common.not_permission",
              new Object[] {npe.getPermission()},
              locale,
              "No permission: " + npe.getPermission()));
    } else {

      if (StrUtil.equals(active, "prod")) {

        rocketMQUtil.sendRetry(
            "sendEmailTopic_topic",
            new JSONObject()
                .set("emailScene", "EXCEPTION")
                .set("exceptionDetails", StrUtil.format("SystemError: Exception:{}", errMsg)),
            null);
      }

      return Result.error(msg("common.oops_issues", locale, "Ops, encountered some issues, please try again later!"));
    }
  }

  private String msg(String code, Locale locale, String defaultMessage) {
    return messageSource.getMessage(code, null, defaultMessage, locale);
  }

  private String msg(String code, Object[] args, Locale locale, String defaultMessage) {
    return messageSource.getMessage(code, args, defaultMessage, locale);
  }

  /**
   * @Author Zheng Hui
   *
   * @param e
   * @return
   */
  /**
   * When not logged in, wrong HTTP method must not expose allowed methods (section 7 / G04: not logged in /sys/user/list -> 401).
   * When logged in, return 405.
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Result<?>> httpRequestMethodNotSupportedException(
      HttpRequestMethodNotSupportedException e) {
    Locale locale = LocaleResolver.getCurrLocale();
    if (!isLoggedIn()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(
              Result.error(
                  401,
                  msg(
                      "common.authentication_exception",
                      locale,
                      "Authentication failed, please sign in again")));
    }
    StringBuffer sb = new StringBuffer();
    sb.append("Not supported");
    sb.append(e.getMethod());
    sb.append(" request method. ");
    sb.append("Supported methods: ");
    String[] methods = e.getSupportedMethods();
    if (methods != null) {
      for (String str : methods) {
        sb.append(str);
        sb.append(", ");
      }
    }
    String safeMsg = SensitiveDataTurboFilter.redact(sb.toString());
    log.error(safeMsg, e);
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Result.error(405, sb.toString()));
  }

  private static boolean isLoggedIn() {
    try {
      return StpUtil.isLogin();
    } catch (Exception ignored) {
      return false;
    }
  }

  /** Spring's default upload size is 100MB and exceeds the size to catch exception MaxUploadSizeExceededException */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
  public Result<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
    log.warn("payload too large msg={}", SensitiveDataTurboFilter.redact(e.getMessage()));
    Locale locale = LocaleResolver.getCurrLocale();

    // Upload too large is a client error (413), not HTTP 200 + business 500.
    return Result.error(413, msg("common.file_size_exceeded", locale, "File size exceeded"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public Result<?> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
    String errMsg = SensitiveDataTurboFilter.redact(e.getMessage());
    log.error("operation failed msg={}", errMsg, e);
    // 【issues/3624】Incorrect database execution exception handleDataIntegrityViolationException prompt #3624

    if (StrUtil.equals(active, "prod")) {

      rocketMQUtil.sendRetry(
          "sendEmailTopic_topic",
          new JSONObject()
              .set("emailScene", "EXCEPTION")
              .set("exceptionDetails", StrUtil.format("DataExecuteException: Exception:{}", errMsg)),
          null);
    }

    Locale locale = LocaleResolver.getCurrLocale();
    return Result.error(msg("common.oops_issues", locale, "Ops, encountered some issues, please try again later!"));
  }

  @ExceptionHandler(PoolException.class)
  public Result<?> handlePoolException(PoolException e) {
    String errMsg = SensitiveDataTurboFilter.redact(e.getMessage());
    log.error("operation failed msg={}", errMsg, e);
    Locale locale = LocaleResolver.getCurrLocale();

    if (StrUtil.equals(active, "prod")) {

      rocketMQUtil.sendRetry(
          "sendEmailTopic_topic",
          new JSONObject()
              .set("emailScene", "EXCEPTION")
              .set(
                  "exceptionDetails",
                  StrUtil.format("PoolException: Exception:{}", errMsg)),
          null);
    }

    return Result.error(msg("common.oops_issues", locale, "Ops, encountered some issues, please try again later!"));
  }
}
