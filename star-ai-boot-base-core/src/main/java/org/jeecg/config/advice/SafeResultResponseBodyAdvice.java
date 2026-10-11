package org.jeecg.config.advice;

import org.jeecg.common.api.vo.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Hardens {@link Result} JSON responses: blocks MIME sniffing (SECZ-SSCSP-CODE-021).
 * message is plain text; Jackson serialization suffices, no HTML entity encoding.
 *
 * <p>拒绝码统一标准 (rejection-code standard): a controller that returns {@code
 * Result.error(<4xx>, ...)} must produce the matching HTTP status, not HTTP 200 with a business
 * 4xx code. This advice is the single enforcement point so every module stays consistent. Only the
 * well-defined rejection codes are mapped; business errors (e.g. {@code code=500}) keep the legacy
 * HTTP 200 + body-code contract.
 */
@RestControllerAdvice
public class SafeResultResponseBodyAdvice implements ResponseBodyAdvice<Object> {

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return Result.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (body instanceof Result<?>) {
      response.getHeaders().set("X-Content-Type-Options", "nosniff");
      applyRejectionStatus((Result<?>) body, response);
    }
    return body;
  }

  /** Mirror body rejection code onto the HTTP status (400/401/403/404/429) for unified handling. */
  private static void applyRejectionStatus(Result<?> result, ServerHttpResponse response) {
    if (result.isSuccess()) {
      return;
    }
    Integer code = result.getCode();
    if (code != null
        && (code == 400 || code == 401 || code == 403 || code == 404 || code == 429)) {
      response.setStatusCode(HttpStatus.valueOf(code));
    }
  }
}
