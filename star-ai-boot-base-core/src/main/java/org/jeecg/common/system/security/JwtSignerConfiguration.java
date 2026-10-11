package org.jeecg.common.system.security;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class JwtSignerConfiguration {

    @Bean
    public Object jwtSm2SignerGuard(@Value("${SM2_JWT_PRIVATE_KEY:}") String pem) {
        if (!StringUtils.hasText(pem) || pem.length() < 120
            || pem.contains("EXAMPLE") || pem.contains("CHANGE_ME")) {
            throw new BeanInitializationException(
                "D5 SM2_JWT_PRIVATE_KEY invalid/stub; refuse start. "
              + "PEM empty or too short (len=" + (pem == null ? 0 : pem.length())
              + ") or contains EXAMPLE/CHANGE_ME template stubs. "
              + "Generate a NEW keypair per env and set via env_file.");
        }
        return new Object();
    }
}
