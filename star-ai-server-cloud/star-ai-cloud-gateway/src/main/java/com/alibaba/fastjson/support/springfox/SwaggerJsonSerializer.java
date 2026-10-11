package com.alibaba.fastjson.support.springfox;

import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Gateway classpath stub: {@code jeecg-boot-starter-cloud}'s {@code FeignConfig} still
 * references this fastjson 1.x class; fastjson 2.0.65 compat jar no longer ships it.
 * Gateway does not package {@code jeecg-boot-base-core}, so the stub must live here.
 */
public class SwaggerJsonSerializer implements ObjectSerializer {

  @Override
  public void write(
      JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
      throws IOException {
    if (object == null) {
      serializer.writeNull();
      return;
    }
    serializer.write(String.valueOf(object));
  }
}
