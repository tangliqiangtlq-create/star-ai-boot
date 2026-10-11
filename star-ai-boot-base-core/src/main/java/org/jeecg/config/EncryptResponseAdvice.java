package org.jeecg.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.EncryptResponse;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import org.jeecg.common.util.encryption.EncryptedString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Encrypt API response data with national SM4 cipher. */
@Slf4j
@ControllerAdvice
public class EncryptResponseAdvice implements ResponseBodyAdvice<Object> {

  @Autowired private RedisUtil redisUtil;

  private String encryptSm4(String plainText, String key, String iv) {
    byte[] keyBytes = HexUtil.decodeHex(SmCryptoUtil.deriveSm4KeyHex(key + ":" + iv));
    byte[] ivBytes = new byte[16];
    byte[] rawIv = iv.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(rawIv, 0, ivBytes, 0, Math.min(rawIv.length, 16));
    return SmCryptoUtil.sm4EncryptWithIv(plainText, keyBytes, ivBytes);
  }

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return returnType.hasMethodAnnotation(EncryptResponse.class);
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (body instanceof Result) {
      Result<?> result = (Result<?>) body;
      Object data = result.getResult();
      if (data != null) {
        try {
          String token = StpUtil.getTokenValue();
          String keyInfo = (String) redisUtil.get("sys:cache:encryption:aes:" + token);

          String encryptedData;
          // Disable $ref so encrypted payloads stay self-contained and frontend JSON.parse succeeds.
          String plainJson =
              JSON.toJSONString(data, SerializerFeature.DisableCircularReferenceDetect);
          if (keyInfo != null && keyInfo.contains(":")) {
            String[] parts = keyInfo.split(":", 2);
            encryptedData = encryptSm4(plainJson, parts[0], parts[1]);
          } else {
            encryptedData = encryptSm4(plainJson, EncryptedString.key, EncryptedString.iv);
          }

          ((Result<Object>) result).setResult(encryptedData);
          log.debug("API data encryption succeeded: {}", returnType.getMethod().getName());
        } catch (Exception e) {
          log.error("API data encryption failed", e);
        }
      }
    }
    return body;
  }
}
