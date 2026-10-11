package org.jeecg.common.util;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import feign.jackson.JacksonEncoder;
import java.lang.reflect.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class CompositeEncoder implements Encoder {

  private final Encoder jsonEncoder;
  private final Encoder formEncoder;

  public CompositeEncoder() {
    this.jsonEncoder = new JacksonEncoder();
    this.formEncoder = new SpringFormEncoder();
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
    log.info("Encoding object: {}, type: {}, existing Content-Type: {}",
        object != null ? object.getClass().getSimpleName() : "null",
        bodyType,
        template.headers().get("Content-Type"));

    // Clear existing Content-Type to avoid Feign default value interference
    template.header("Content-Type", (String) null);

    if (isMultipartRequest(object, bodyType, template)) {
      log.info("Using SpringFormEncoder for multipart/form-data");
      // The boundary is automatically written by SpringFormEncoder. Content-Type cannot be preset here.
      formEncoder.encode(object, bodyType, template);
    } else {
      log.info("Using JacksonEncoder for application/json");
      template.header("Content-Type", "application/json");
      jsonEncoder.encode(object, bodyType, template);
    }
  }

  private boolean isMultipartRequest(Object object, Type bodyType, RequestTemplate template) {
    // CheckParameterType
    if (object instanceof MultipartFile || object instanceof MultipartFile[]) {
      return true;
    }
    if (object instanceof java.util.Map) {
      return true;
    }

    // Check the Content-Type specified by the Feign client (set by consumes)
    String contentType = template.headers().get("Content-Type") != null
        ? template.headers().get("Content-Type").iterator().next()
        : null;
    boolean isMultipart = contentType != null && contentType.contains("multipart/form-data");
    log.info("Content-Type check: {}, isMultipart: {}", contentType, isMultipart);
    return isMultipart;
  }
}