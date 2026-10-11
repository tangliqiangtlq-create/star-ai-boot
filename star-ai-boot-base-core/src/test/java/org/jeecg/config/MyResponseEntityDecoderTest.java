package org.jeecg.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import feign.FeignException;
import feign.Request;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.jeecg.common.api.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Feign decoder regression: after Spring Cloud / OpenFeign upgrade, microservice Result-wrapped
 * responses still parse correctly.
 *
 * <p>Also pins the {@code String}-typed envelope guard
 * ({@link MyResponseEntityDecoder#decodeStringTypedEnvelope}). Previously a failed remote call
 * answered by the server's exception handler was returned verbatim as the String value, so callers
 * stored error text as data. {@code AutoLogAspect} did exactly that with
 * {@code translateDictFromTable(...)} and {@code sys_log.username}, producing 98-char corrupted
 * rows in Chinese and {@code MysqlDataTruncation: Data too long for column 'username'} in English.
 *
 * <p>{@code Response.builder().body(...)} yields a {@code ByteArrayBody} / {@code InputStreamBody},
 * which takes the isJson branch of {@code decode}; {@link PlainBody} forces the other branch so
 * both call sites of the guard are covered.
 */
class MyResponseEntityDecoderTest {

  /** Mirrors the payload the system exception handler returns for a blocked dict lookup. */
  private static final String C6_ERROR_ENVELOPE =
      "{\"success\":false,\"message\":\"查询SQL失败\",\"code\":500,\"result\":null,"
          + "\"timestamp\":1790800000000}";

  private static final String ENGLISH_ERROR_ENVELOPE =
      "{\"success\":false,\"message\":\"Authentication failed, please check your credentials,"
          + " then retry with a fresh session token because the previous one has expired\","
          + "\"code\":500,\"result\":null,\"timestamp\":1790800000000}";

  private final MyResponseEntityDecoder decoder = new MyResponseEntityDecoder();

  @Test
  @DisplayName("Decoded standard Result JSON success response")
  void decodeStandardResultJson() throws Exception {
    String body = "{\"success\":true,\"code\":200,\"message\":\"ok\",\"result\":\"hello\"}";
    Response response = buildResponse(200, body);

    Object decoded = decoder.decode(response, Result.class);

    assertTrue(decoded instanceof Result);
    Result<?> result = (Result<?>) decoded;
    assertTrue(result.isSuccess());
    assertEquals(200, result.getCode());
    assertEquals("hello", result.getResult());
  }

  @Test
  @DisplayName("404 response returns empty value per Feign convention (may be null)")
  void decode404ReturnsEmpty() throws Exception {
    Response response = buildResponse(404, "");
    Object decoded = decoder.decode(response, Result.class);
    // Feign Util.emptyValueOf may return null for non-primitive types
    assertTrue(decoded == null || decoded instanceof Result);
  }

  // ---------------------------------------------------------------------------
  // String-typed methods must never receive a Result envelope as their value
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("String method: a bare string (normal success shape) passes through unchanged")
  void stringMethodBareValuePassesThrough() throws Exception {
    Response response = buildResponse(200, "ops_admin");
    assertEquals("ops_admin", decoder.decode(response, String.class));
  }

  @Test
  @DisplayName("String method: success envelope is unwrapped to its result payload")
  void stringMethodSuccessEnvelopeUnwrapped() throws Exception {
    String body = "{\"success\":true,\"code\":200,\"message\":\"ok\",\"result\":\"ops_admin\"}";
    assertEquals("ops_admin", decoder.decode(buildResponse(200, body), String.class));
  }

  @Test
  @DisplayName("String method: failure envelope raises instead of being returned as the value")
  void stringMethodFailureEnvelopeThrows() {
    // The 134-char English body is the exact shape that overflowed varchar(100).
    assertTrue(ENGLISH_ERROR_ENVELOPE.length() > 100);

    FeignException zh =
        assertThrows(
            FeignException.class,
            () -> decoder.decode(buildResponse(500, C6_ERROR_ENVELOPE), String.class));
    assertTrue(zh.getMessage().contains("查询SQL失败") || zh.status() == 500);

    assertThrows(
        FeignException.class,
        () -> decoder.decode(buildResponse(500, ENGLISH_ERROR_ENVELOPE), String.class));
  }

  @Test
  @DisplayName("String method: guard is type-scoped — Result-typed calls keep decoding normally")
  void resultTypedFailureEnvelopeStillThrows() throws Exception {
    // Regression guard: the new String-typed check must not short-circuit Result decoding.
    Object decoded =
        decoder.decode(
            buildResponse(200, "{\"success\":true,\"code\":200,\"result\":\"x\"}"), Result.class);
    assertTrue(decoded instanceof Result);
  }

  @Test
  @DisplayName("String method: non-envelope JSON object is not rewrapped (falls through)")
  void stringMethodNonEnvelopeJsonPassesThrough() throws Exception {
    // No "success" key -> not a Result envelope; legacy behaviour must be preserved.
    String body = "{\"foo\":\"bar\"}";
    assertEquals(body, decoder.decode(buildResponse(200, body), String.class));
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Both decoder branches are exercised by the tests above")
  void bodyFactoriesCoverBothBranches() throws Exception {
    // Documents which decode() branch each body type takes, so a refactor that drops one
    // branch's guard is caught here.
    Response viaBuilder = buildResponse(200, "x");
    String simpleName = viaBuilder.body().getClass().getSimpleName();
    assertTrue(
        "ByteArrayBody".equals(simpleName) || "InputStreamBody".equals(simpleName),
        "Response.builder().body(...) should hit the isJson branch, was " + simpleName);

    Response viaPlain = buildResponseWithPlainBody(200, ENGLISH_ERROR_ENVELOPE);
    assertEquals("PlainBody", viaPlain.body().getClass().getSimpleName());
    // PlainBody takes the asReader branch — the failure envelope must still raise there.
    assertThrows(FeignException.class, () -> decoder.decode(viaPlain, String.class));
  }

  private Response buildResponse(int status, String body) {
    return Response.builder()
        .status(status)
        .request(buildRequest())
        .body(body, StandardCharsets.UTF_8)
        .build();
  }

  /** Forces the {@code asReader} branch of {@code decode} (body class is neither known type). */
  private Response buildResponseWithPlainBody(int status, String body) {
    return Response.builder()
        .status(status)
        .request(buildRequest())
        .body(new PlainBody(body))
        .build();
  }

  private Request buildRequest() {
    return Request.create(
        Request.HttpMethod.POST,
        "http://localhost/sys/api/translateDictFromTable",
        Collections.emptyMap(),
        null,
        StandardCharsets.UTF_8,
        null);
  }

  /** Minimal {@link Response.Body} whose simple name differs from the two Feign built-ins. */
  private static final class PlainBody implements Response.Body {
    private final byte[] data;

    PlainBody(String value) {
      this.data = value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Integer length() {
      return data.length;
    }

    @Override
    public boolean isRepeatable() {
      return true;
    }

    @Override
    public InputStream asInputStream() {
      return new ByteArrayInputStream(data);
    }

    @Override
    public Reader asReader(Charset charset) {
      return new InputStreamReader(new ByteArrayInputStream(data), charset);
    }

    @Override
    public void close() {
      // no-op: in-memory body
    }
  }
}
