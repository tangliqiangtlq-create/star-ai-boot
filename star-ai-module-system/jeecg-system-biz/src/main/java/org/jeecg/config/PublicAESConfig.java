package org.jeecg.config;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PublicAESConfig {
  private static final Logger log = LoggerFactory.getLogger(PublicAESConfig.class);


  @Value("${jeecg.aesKey}")
  private String privateKeyStr;

  @Bean
  public AES aes() {

    // Create AES instance
    return new AES("ECB", "PKCS5Padding", privateKeyStr.getBytes());
  }

  public static void main(String[] args) {

    String a = "GXKCl2xVWNGCMuqnZ0gGl4FLolEukDb0";

    AES aes = new AES("ECB", "PKCS5Padding", a.getBytes());

    String encryptedStr = aes.encryptBase64("test");

    String decryptStr = aes.encryptBase64(encryptedStr);

    log.debug(String.valueOf(decryptStr));
  }
}
