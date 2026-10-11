package org.jeecg.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import feign.FeignException;
import feign.FeignException.InternalServerError;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MyResponseEntityDecoder implements Decoder {

  /** Consistent with HttpUtils / BodyReader etc.; prevents Feign response body OOM (DoS) */
  private static final int MAX_RESPONSE_BODY_BYTES = 10485760; // 10MB

  @Override
  public Object decode(Response response, Type type) throws IOException, FeignException {

    if (response.status() == 404 || response.status() == 204) {
      return Util.emptyValueOf(type);
    }
    if (response.body() == null) {
      return null;
    }
    if (byte[].class.equals(type)) {
      try (InputStream in = response.body().asInputStream()) {
        return readBodyWithByteLimit(in, MAX_RESPONSE_BODY_BYTES);
      }
    }

    String bodyImplSimpleName = response.body().getClass().getSimpleName();

    if ("ByteArrayBody".equals(bodyImplSimpleName) || "InputStreamBody".equals(
        bodyImplSimpleName)) {
      StringBuilder result = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(response.body().asInputStream(), StandardCharsets.UTF_8))) {
        String line;
        int maxLength = 10485760; // 10MB
        while ((line = reader.readLine()) != null) {
          if (result.length() + line.length() > maxLength) {
            throw new IOException("Response body is too large");
          }
          result.append(line);
        }
      } catch (IOException e) {
        log.error("operation failed", e);
        throw new InternalServerError(e.getMessage(), response.request(), null, null);
      }

      String bodyAsString = result.toString();

      if (JSONUtil.isJson(bodyAsString)) {

        // A String-typed Feign method must never be handed a failure Result envelope verbatim.
        // This branch is the one that actually fires for {@code Response.builder()} bodies, and
        // it is where the audit-log corruption originated: JSONUtil.toBean(<envelope>, String
        // .class) does NOT produce a Result, so the isSuccess() guard just below was skipped and
        // the raw envelope was returned as the method's value. AutoLogAspect then stored it into
        // sys_log.username (98 chars zh / 134 chars en) — "Data too long for column 'username'"
        // and, once truncated, a multi-statement guard trip. Run the envelope check first.
        Object stringTypedValue = decodeStringTypedEnvelope(bodyAsString, type, response);
        if (stringTypedValue != NOT_A_STRING_ENVELOPE) {
          return stringTypedValue;
        }

        Object obj = JSONUtil.toBean(bodyAsString, type, false);
        obj = normalizeFeignResult(obj, type);

        if (obj instanceof Result) {

          Result r = (Result) obj;

          if (!r.isSuccess()) {

            log.info("Request URL: {}", response.request().url());
            log.info("Service exception: {}", bodyAsString);

            throw new InternalServerError(r.getMessage(), response.request(),
                bodyAsString.getBytes(StandardCharsets.UTF_8), null);
          }
        }
        return obj;
      } else {

        return bodyAsString;
      }

    }
    String body;
    try (Reader reader = response.body().asReader(StandardCharsets.UTF_8)) {
      body = Util.toString(reader);
    }

    if (StrUtil.isBlank(body)) {
      return null;
    }

    if (type.getTypeName().equals("java.lang.String")) {
      // Same guard as the ByteArrayBody / InputStreamBody branch above — a String-typed method
      // must never be handed a failure Result envelope verbatim. A genuine bare string (the
      // normal success shape of these endpoints) is not JSON and passes straight through.
      Object stringTypedValue = decodeStringTypedEnvelope(body, type, response);
      if (stringTypedValue != NOT_A_STRING_ENVELOPE) {
        return stringTypedValue;
      }
      return body;
    }

    Object obj = JSONUtil.toBean(body, type, false);
    obj = normalizeFeignResult(obj, type);

    if (obj instanceof Result) {

      Result r = (Result) obj;

      if (!r.isSuccess()) {

        log.info("Request URL: {}", response.request().url());
        log.info("Service exception: {}", body);

        throw new InternalServerError(r.getMessage(), response.request(), body.getBytes(StandardCharsets.UTF_8), null);
      }
    }

    return obj;

  }

  /**
   * Sentinel meaning "this is not a String-typed Result envelope — use the normal decode path".
   *
   * <p>A dedicated object (rather than {@code null}) is required because {@code null} is a
   * legitimate decoded value: a success envelope may carry {@code "result": null}.
   */
  private static final Object NOT_A_STRING_ENVELOPE = new Object();

  /**
   * Result-envelope handling for Feign methods declared to return {@code String}.
   *
   * <p>When the remote service's exception handler answers a failed call it returns the standard
   * {@code Result} envelope — {@code success=false} with the error text in {@code message}. A
   * {@code String}-typed method has no envelope to deserialize into, so without this guard the
   * body is handed back as the method's return value and callers persist error text as data. The
   * concrete failure this closes: {@code AutoLogAspect} resolves the operator name via
   * {@code translateDictFromTable("sys_user", "username", "id", loginId)} and stores the result in
   * {@code sys_log.username}, so a firewall-blocked lookup wrote the error JSON into every audit
   * row — 98 chars in Chinese (silent corruption), 134 in English
   * ({@code MysqlDataTruncation: Data too long for column 'username'}).
   *
   * @return the unwrapped {@code result} payload for a successful envelope, the
   *     {@link #NOT_A_STRING_ENVELOPE} sentinel when the body is not a String-typed envelope, and
   *     throws {@link InternalServerError} for a failure envelope so the caller sees the remote
   *     error instead of its message text
   */
  private Object decodeStringTypedEnvelope(String body, Type type, Response response)
      throws IOException {
    if (body == null || !type.getTypeName().equals("java.lang.String")) {
      return NOT_A_STRING_ENVELOPE;
    }
    if (!JSONUtil.isJson(body)) {
      return NOT_A_STRING_ENVELOPE;
    }
    final JSONObject envelope;
    try {
      envelope = new JSONObject(body);
    } catch (Exception notAnObject) {
      // JSON array / scalar / unparsable — not a Result envelope.
      return NOT_A_STRING_ENVELOPE;
    }
    if (!envelope.containsKey("success")) {
      return NOT_A_STRING_ENVELOPE;
    }
    if (!envelope.getBool("success", false)) {
      log.info("Request URL: {}", response.request().url());
      log.info("Service exception: {}", body);
      throw new InternalServerError(
          envelope.getStr("message", "remote service returned a failure result"),
          response.request(),
          body.getBytes(StandardCharsets.UTF_8),
          null);
    }
    // Success envelope: the caller wants the payload, not the wrapper.
    Object data = envelope.get("result");
    return data == null ? null : String.valueOf(data);
  }

  /**
   * When Feign deserializes paginated records, nested objects are often {@link java.util.LinkedHashMap}; if generic is hutool {@link JSONObject}, convert to
   * JSONObject to avoid downstream {@code (JSONObject)} cast failures.
   */
  private static Object normalizeFeignResult(Object obj, Type type) {
    if (!(obj instanceof Result<?> result) || !expectsHutoolJsonRecords(type)) {
      return obj;
    }
    Object data = result.getResult();
    if (data instanceof Page<?> page) {
      normalizePageRecords(page);
    } else if (data instanceof IPage<?> page) {
      normalizePageRecords(page);
    }
    return obj;
  }

  private static boolean expectsHutoolJsonRecords(Type type) {
    if (!(type instanceof ParameterizedType parameterizedType)) {
      return false;
    }
    Type raw = parameterizedType.getRawType();
    if (!Result.class.equals(raw)) {
      return false;
    }
    Type[] args = parameterizedType.getActualTypeArguments();
    if (args.length != 1) {
      return false;
    }
    Type dataType = args[0];
    if (dataType instanceof ParameterizedType pageType) {
      Type[] pageArgs = pageType.getActualTypeArguments();
      return pageArgs.length >= 1 && JSONObject.class.equals(pageArgs[0]);
    }
    return false;
  }

  private static void normalizePageRecords(IPage<?> page) {
    List<?> records = page.getRecords();
    if (records == null || records.isEmpty()) {
      return;
    }
    Object first = records.get(0);
    if (!(first instanceof Map<?, ?>) || first instanceof JSONObject) {
      return;
    }
    List<JSONObject> converted = new ArrayList<>(records.size());
    for (Object record : records) {
      converted.add(new JSONObject((Map<?, ?>) record));
    }
    page.setRecords((List) converted);
  }

  private static byte[] readBodyWithByteLimit(InputStream in, int maxBytes) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int n;
    int total = 0;
    while ((n = in.read(chunk)) != -1) {
      if (total + n > maxBytes) {
        throw new IOException("Response body is too large");
      }
      buf.write(chunk, 0, n);
      total += n;
    }
    return buf.toByteArray();
  }
}
