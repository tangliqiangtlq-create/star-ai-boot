package org.jeecg.common.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.PropertyFilter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.api.dto.LogDTO;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.security.H7ExceptionRedactor;
import org.jeecg.common.constant.enums.ModuleType;
import org.jeecg.common.constant.enums.OperateTypeEnum;
import org.jeecg.common.util.IpUtils;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.base.service.BaseCommonService;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.support.ServletRequestHandledEvent;
import org.springframework.web.multipart.MultipartFile;

/**
 * System log, aspect processing class @Author scott
 *
 * @author jeecg-boot @Date January 14, 2018
 */
@Aspect
@Component
public class AutoLogAspect implements ApplicationListener<ServletRequestHandledEvent> {

  /** Per-request thread-local queue of LogDTO instances (B10-2: async auto-log). */
  private static final ThreadLocal<List<LogDTO>> REQ_QUEUE = ThreadLocal.withInitial(ArrayList::new);

  /**
   * Bounded thread-pool used in two situations: (a) a Servlet request ends and the event fires, we
   * hand off the batch flush to this pool so the browser isn't blocked waiting on DB insert; (b) a
   * background thread (MQ consumer, @Scheduled job) invokes an @AutoLog method — there is no
   * ServletRequestHandledEvent, so enqueueing into the thread-local queue would leak memory.
   * Instead we submit an immediate flush for that single DTO through this pool.
   */
  private static ThreadPoolExecutor logFlushPool;

  static ThreadPoolExecutor logFlushPoolOrInit() {
    ThreadPoolExecutor e = logFlushPool;
    if (e == null) {
      synchronized (AutoLogAspect.class) {
        if ((e = logFlushPool) == null) {
          int core = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
          int max = Math.max(core, 4);
          ThreadFactory tf =
              new ThreadFactory() {
                final AtomicInteger n = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                  Thread t = new Thread(r, "autolog-flush-" + n.incrementAndGet());
                  t.setDaemon(true);
                  t.setPriority(Thread.NORM_PRIORITY - 1);
                  return t;
                }
              };
          e =
              new ThreadPoolExecutor(
                  core,
                  max,
                  60L,
                  TimeUnit.SECONDS,
                  new LinkedBlockingQueue<>(16384),
                  tf,
                  // CallerRunsPolicy: if queue is full (extreme burst), the caller thread writes
                  // synchronously so we never drop an audit log record.
                  new ThreadPoolExecutor.CallerRunsPolicy());
          logFlushPool = e;
        }
      }
    }
    return e;
  }

  @PostConstruct
  void init() {
    logFlushPoolOrInit();
  }

  @PreDestroy
  void destroy() {
    ThreadPoolExecutor e = logFlushPool;
    if (e != null) {
      e.shutdown();
      try {
        if (!e.awaitTermination(3, TimeUnit.SECONDS)) e.shutdownNow();
      } catch (InterruptedException ex) {
        e.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  @Lazy @Resource private BaseCommonService baseCommonService;

  @Lazy @Resource private CommonAPI commonAPI;

  /**
   * @return whether the calling thread currently has an active servlet request context that will
   *     fire ServletRequestHandledEvent later. True means we may safely enqueue into the
   *     thread-local and let the listener batch-flush at request end. False means we must flush
   *     asynchronously immediately.
   */
  private static boolean servletRequestActive() {
    try {
      // Spring MVC sets the RequestContextHolder attributes on servlet request threads. Background
      // tasks (XXL-Job handlers, RocketMQ consumers, @Async threads) do not have these, unless
      // explicitly propagated — in which case the event still won't fire, so we detect that via a
      // best-effort null check on the HttpServletRequest object.
      HttpServletRequest req = SpringContextUtils.getHttpServletRequestQuietly();
      return req != null;
    } catch (Throwable ignore) {
      return false;
    }
  }

  @Pointcut("@annotation(org.jeecg.common.aspect.annotation.AutoLog)")
  public void logPointCut() {}

  @Around("logPointCut()")
  public Object around(ProceedingJoinPoint point) throws Throwable {
    long beginTime = System.currentTimeMillis();
    Throwable raised = null;
    Object result = null;
    try {
      result = point.proceed();
      return result;
    } catch (Throwable t) {
      raised = t;
      throw t;
    } finally {
      long time = System.currentTimeMillis() - beginTime;
      try {
        enqueueSysLog(point, time, result, raised);
      } catch (Throwable logT) {
        // Never let log collection interfere with the actual business method result/exception.
        logT.printStackTrace();
      }
    }
  }

  // ======== enqueue: build DTO then choose thread-local vs immediate async flush ========
  private void enqueueSysLog(
      ProceedingJoinPoint joinPoint, long time, Object obj, Throwable raised) {
    LogDTO dto = buildSysLog(joinPoint, time, obj);
    if (dto == null) return;
    if (raised != null) {
      String existing = dto.getLogContent() == null ? "" : dto.getLogContent();
      String redactedSummary = H7ExceptionRedactor.redactSummary(raised);
      // H7 already redacts secrets and truncates; but exception messages and stack frames
      // naturally contain "; <letter>" sequences (e.g. "; uncategorized SQLException",
      // "; SQL state [null]"). DangerousSqlStringInspector inspects saveLog parameters and
      // flags such patterns as multi-statement SQL injection, which silently drops the audit
      // log. Sanitize here without losing meaning: replace ASCII ';' with fullwidth '；'.
      // The firewall regex only matches ASCII ';', so fullwidth is safe and preserves the
      // redacted summary text for forensic review.
      redactedSummary = redactedSummary.replace(';', '；');
      dto.setLogContent(existing + " | [THROWN " + redactedSummary + "]");
    }
    if (servletRequestActive()) {
      REQ_QUEUE.get().add(dto);
    } else {
      submitAsyncFlush(java.util.Collections.singletonList(dto));
    }
  }

  /** Extract the servlet-thread-local queue, clear the TL, and hand off flush to thread-pool. */
  private List<LogDTO> drainRequestQueue() {
    List<LogDTO> list = REQ_QUEUE.get();
    if (list == null || list.isEmpty()) {
      REQ_QUEUE.remove();
      return java.util.Collections.emptyList();
    }
    ArrayList<LogDTO> copy = new ArrayList<>(list);
    list.clear();
    REQ_QUEUE.remove();
    return copy;
  }

  private void submitAsyncFlush(List<LogDTO> batch) {
    if (batch == null || batch.isEmpty()) return;
    // Capture references that are safe across threads (BaseCommonService/CommonAPI are singletons,
    // so references are fine — the request-bound TenantContext/UserTokenContext inside addLog()
    // should not be needed because we already resolved username/userid into the DTO above).
    final BaseCommonService bc = baseCommonService;
    try {
      logFlushPoolOrInit()
          .execute(
              () -> {
                for (LogDTO d : batch) {
                  try {
                    bc.addLog(d);
                  } catch (Throwable t) {
                    t.printStackTrace();
                  }
                }
              });
    } catch (Throwable poolReject) {
      // Last-resort sync fallback (e.g. executor shutdown). Never silently drop logs.
      for (LogDTO d : batch) {
        try {
          bc.addLog(d);
        } catch (Throwable ignore) {
        }
      }
    }
  }

  /** Spring MVC fires this after each servlet request has been fully handled. */
  @Override
  public void onApplicationEvent(ServletRequestHandledEvent event) {
    List<LogDTO> batch = drainRequestQueue();
    if (batch.isEmpty()) return;
    submitAsyncFlush(batch);
  }

  /**
   * Build the LogDTO instance. Kept separate from enqueue so that the "enqueue-or-sync" decision
   * above can be tested in isolation.
   */
  private LogDTO buildSysLog(ProceedingJoinPoint joinPoint, long time, Object obj) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();

    LogDTO dto = new LogDTO();
    AutoLog syslog = method.getAnnotation(AutoLog.class);
    if (syslog != null) {
      String content = syslog.value();
      if (syslog.module() == ModuleType.ONLINE) {
        content = getOnlineLogContent(obj, content);
      }
      dto.setLogType(syslog.logType());
      dto.setLogContent(content);
    }

    String className = joinPoint.getTarget().getClass().getName();
    String methodName = signature.getName();
    dto.setMethod(className + "." + methodName + "()");

    if (dto.getLogType() != null && CommonConstant.LOG_TYPE_2 == dto.getLogType()) {
      dto.setOperateType(getOperateType(methodName, syslog.operateType()));
    }

    HttpServletRequest request = SpringContextUtils.getHttpServletRequestQuietly();
    if (request != null) {
      // B3-2: do not persist raw request body — method + outcome summary only
      dto.setRequestParam(buildRequestSummary(request, obj));
      dto.setIp(IpUtils.getIpAddr(request));
    }

    if (StpUtil.isLogin()) {
      String loginId = StpUtil.getLoginIdAsString();
      try {
        String username =
            (request == null)
                ? ""
                : commonAPI.translateDictFromTable("sys_user", "username", "id", loginId);
        // translateDictFromTable may return a non-username string when the underlying query
        // is blocked by the firewall (C6 / sensitive-column rules) — Feign returns the error
        // Result JSON and MyResponseEntityDecoder hands the whole JSON body back as a String.
        // That string easily exceeds sys_log.username varchar(100) and often contains "; <letter>"
        // sequences from the error message, which then trips DangerousSqlStringInspector and
        // MysqlDataTruncation on saveLog. Validate before persisting; fall back to null so the
        // log row still writes with userid set and an empty username — never an error body.
        dto.setUserid(loginId);
        dto.setUsername(sanitizeUsernameForLog(username));
      } catch (Throwable t) {
        dto.setUserid(loginId);
      }
    }

    dto.setCostTime(time);
    dto.setCreateTime(new Date());
    return dto;
  }

  /** Get operation type */
  private int getOperateType(String methodName, int operateType) {
    if (operateType > 0) {
      return operateType;
    }
    // update-begin---author:wangshuai ---date:20220331 for: Alibaba Cloud code scanning specifications (no magic values ​​are allowed to appear in the code)------------
    return OperateTypeEnum.getTypeByMethodName(methodName);
    // update-end---author:wangshuai ---date:20220331 for: Alibaba Cloud code scanning specifications (no magic values ​​are allowed to appear in the code)------------
  }

  /**
   * Validate the username string returned by {@code translateDictFromTable} before it is persisted
   * into {@code sys_log.username}.
   *
   * <p>When the underlying lookup is blocked by the SQL firewall (C6 sensitive-column rules,
   * whitelist failures, etc.), Feign returns the error {@code Result} JSON body and
   * {@code MyResponseEntityDecoder} hands the entire JSON string back as the method's String
   * return value. That string:
   *
   * <ul>
   *   <li>Exceeds {@code sys_log.username varchar(100)} → {@code MysqlDataTruncation}
   *   <li>Often contains {@code "; <letter>"} sequences from the error message → trips
   *       {@code DangerousSqlStringInspector.MULTI_STMT_SEMICOLON}
   * </ul>
   *
   * <p>Either failure silently drops the audit log. This sanitizer rejects any value that does
   * not look like a real username (length, JSON markers, control chars, semicolon-then-letter),
   * returning {@code null} so the log row still writes with {@code userid} set and an empty
   * username. Real usernames (email-like, alphanumeric, etc.) pass through unchanged.
   *
   * @param username raw value from translateDictFromTable
   * @return the username if it looks valid, otherwise {@code null}
   */
  private static String sanitizeUsernameForLog(String username) {
    if (username == null || username.isEmpty()) {
      return null;
    }
    String trimmed = username.trim();
    // sys_log.username is varchar(100) — reject anything longer (no good-faith truncation,
    // because an overlong string here is always a misrouted error body, not a real username).
    if (trimmed.length() > 100) {
      return null;
    }
    // Error Result JSON starts with '{' — a real username never does.
    if (trimmed.charAt(0) == '{' || trimmed.charAt(0) == '[') {
      return null;
    }
    // Reject if it contains JSON markers — Real usernames may contain '@', '.', '-' but not
    // the JSON key-value separator sequence.
    if (trimmed.indexOf(':') >= 0 && (trimmed.indexOf('{') >= 0 || trimmed.indexOf('"') >= 0)) {
      return null;
    }
    // Reject ASCII "; <letter>" — would trip DangerousSqlStringInspector.MULTI_STMT_SEMICOLON
    // when saveLog is later persisted. Walk the string rather than compiling a regex, since
    // this runs on every @AutoLog method invocation.
    for (int i = 0; i < trimmed.length() - 1; i++) {
      char c = trimmed.charAt(i);
      if (c == ';') {
        char next = trimmed.charAt(i + 1);
        if (next == ' ' || next == '\t' || next == '\n' || next == '\r') {
          // skip whitespace then check the first non-ws char
          for (int j = i + 1; j < trimmed.length(); j++) {
            char nc = trimmed.charAt(j);
            if (nc != ' ' && nc != '\t' && nc != '\n' && nc != '\r') {
              if (isMultiStmtTriggerChar(nc)) {
                return null;
              }
              break;
            }
          }
        } else if (isMultiStmtTriggerChar(next)) {
          return null;
        }
      }
    }
    // Reject control characters (except tab/newline which are unlikely in usernames anyway).
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (c < 0x20 && c != '\t') {
        return null;
      }
    }
    return trimmed;
  }

  private static boolean isMultiStmtTriggerChar(char c) {
    // Mirrors DangerousSqlStringInspector.MULTI_STMT_SEMICOLON class — ASCII letters,
    // underscore, CJK ideographs, quote chars, backtick.
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || c == '_'
        || (c >= '\u4e00' && c <= '\u9fa5')
        || c == '"'
        || c == '\''
        || c == '`';
  }

  /**
   * @Description: Get Request parameters
   *
   * @author: scott
   * @date: 2020/4/16 0:10
   * @param request: request
   * @param joinPoint: joinPoint @Return: java.lang.String
   */
  // B3-1: key patterns incl. otp/captcha/iban/cvv/bankAccount from Ramp fix plan
  private static final String[] SENSITIVE_NAME_TOKENS = {
      "password", "passwd", "pwd", "secret", "credential", "token",
      "apikey", "api_key", "privatekey", "private_key", "privkey",
      "seed", "mfa", "hmac", "signature", "access_token", "refreshtoken",
      "refresh_token", "id_token", "sessionid", "session_id", "cookie",
      "authorization", "authorizationtoken", "iv", "nonce", "salt",
      "mastersecret", "master_secret", "signingkey", "signing_key",
      "otp", "captcha", "smscode", "sms_code", "emailcode", "email_code",
      "iban", "cvv", "cvc", "bankaccount", "bank_account", "cardnumber",
      "card_number", "pan", "pin", "otpauth", "totp"
  };

  static boolean isSensitiveParamName(String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    String lower = name.toLowerCase();
    for (String tok : SENSITIVE_NAME_TOKENS) {
      if (lower.contains(tok)) {
        return true;
      }
    }
    return false;
  }

  /** B3-2: audit summary without raw body / args. */
  private static String buildRequestSummary(HttpServletRequest request, Object result) {
    String http = request == null ? "NA" : request.getMethod();
    String status = "ok";
    if (result instanceof Result) {
      status = ((Result<?>) result).isSuccess() ? "success" : "fail";
    } else if (result == null) {
      status = "null";
    }
    String uri = request == null ? "" : request.getRequestURI();
    return http + " " + uri + " status=" + status;
  }

  private String getReqestParams(HttpServletRequest request, JoinPoint joinPoint) {
    String httpMethod = request.getMethod();
    String params = "";
    if (CommonConstant.HTTP_POST.equals(httpMethod)
        || CommonConstant.HTTP_PUT.equals(httpMethod)
        || CommonConstant.HTTP_PATCH.equals(httpMethod)) {
      Object[] paramsArray = joinPoint.getArgs();
      Object[] arguments = new Object[paramsArray.length];
      for (int i = 0; i < paramsArray.length; i++) {
        if (paramsArray[i] instanceof BindingResult
            || paramsArray[i] instanceof ServletRequest
            || paramsArray[i] instanceof ServletResponse
            || paramsArray[i] instanceof MultipartFile) {
          continue;
        }
        arguments[i] = paramsArray[i];
      }
      PropertyFilter profilter =
          new PropertyFilter() {
            @Override
            public boolean apply(Object o, String name, Object value) {
              if (isSensitiveParamName(name)) {
                return false;
              }
              int length = 500;
              if (value != null && value.toString().length() > length) {
                return false;
              }
              if (value instanceof MultipartFile) {
                return false;
              }
              return true;
            }
          };
      params = JSONObject.toJSONString(arguments, profilter);
    } else {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      Method method = signature.getMethod();
      Object[] args = joinPoint.getArgs();
      DefaultParameterNameDiscoverer u = new DefaultParameterNameDiscoverer();
      String[] paramNames = u.getParameterNames(method);
      if (args != null && paramNames != null) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
          sb.append("  ").append(paramNames[i]).append(": ");
          if (isSensitiveParamName(paramNames[i])) {
            sb.append("***");
          } else {
            sb.append(args[i]);
          }
        }
        params = sb.toString();
      }
    }
    return params;
  }

  /**
   * onlinelog content splicing
   *
   * @param obj
   * @param content
   * @return
   */
  private String getOnlineLogContent(Object obj, String content) {
    if (Result.class.isInstance(obj)) {
      Result res = (Result) obj;
      String msg = res.getMessage();
      String tableName = res.getOnlTable();
      if (oConvertUtils.isNotEmpty(tableName)) {
        content += ",table name:" + tableName;
      }
      if (res.isSuccess()) {
        content += "," + (oConvertUtils.isEmpty(msg) ? "Operation successful" : msg);
      } else {
        content += "," + (oConvertUtils.isEmpty(msg) ? "Operation failed" : msg);
      }
    }
    return content;
  }

  /*    private void saveSysLog(ProceedingJoinPoint joinPoint, long time, Object obj) {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      Method method = signature.getMethod();

      SysLog sysLog = new SysLog();
      AutoLog syslog = method.getAnnotation(AutoLog.class);
      if(syslog != null){
          //update-begin-author:taoyan date:
          String content = syslog.value();
          if(syslog.module()== ModuleType.ONLINE){
              content = getOnlineLogContent(obj, content);
          }
          //update-end-author:taoyan date:
          // Description on annotation; operation log content
          sysLog.setLogContent(content);
          sysLog.setLogType(syslog.logType());
      }

      // Requested method name
      String className = joinPoint.getTarget().getClass().getName();
      String methodName = signature.getName();
      sysLog.setMethod(className + "." + methodName + "()");


      // Set operation type
      if (sysLog.getLogType() == CommonConstant.LOG_TYPE_2) {
          sysLog.setOperateType(getOperateType(methodName, syslog.operateType()));
      }

      // Get request
      HttpServletRequest request = SpringContextUtils.getHttpServletRequest();
      // Request parameters
      sysLog.setRequestParam(getReqestParams(request,joinPoint));

      // Set IP address
      sysLog.setIp(IPUtils.getIpAddr(request));

      // Get logged-in user info
      LoginUser sysUser = (LoginUser)SecurityUtils.getSubject().getPrincipal();
      if(sysUser!=null){
          sysLog.setUserid(sysUser.getUsername());
          sysLog.setUsername(sysUser.getRealname());

      }
      // Elapsed time
      sysLog.setCostTime(time);
      sysLog.setCreateTime(new Date());
      // Save system log
      sysLogService.save(sysLog);
  }*/
}
