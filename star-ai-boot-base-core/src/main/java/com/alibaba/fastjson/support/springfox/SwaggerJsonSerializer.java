package com.alibaba.fastjson.support.springfox;

import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Compatibility stub: fastjson 1.x had this class for springfox; fastjson 2.x compat jar
 * (2.0.65) dropped {@code support.springfox}. Jeecg {@code FeignConfig} still registers it —
 * without this class gateway fails with ClassNotFoundException at startup.
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
