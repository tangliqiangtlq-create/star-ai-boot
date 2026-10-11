package org.jeecg.common.util;

import cn.hutool.core.util.StrUtil;
import java.time.Duration;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

  @Value("${spring.profiles.active}")
  private String active;

  public void setCookie(HttpServletResponse response, String token) {

    if (StrUtil.isBlank(token)) {
      return;
    }

    ResponseCookie jwtCookie; // Prevent JavaScript access

    if (active.equals("dev")) {
      jwtCookie =
          ResponseCookie.from("auth_token", token)
              .httpOnly(true) // ✅ Prevent JavaScript access
              .secure(false) // ✅ **Must be `false` for local development, `true` for production environment**
              .sameSite("Lax") // ✅ **If it is cross-domain, you must use `None`**
              .path("/") // ✅ Applies to the entire app
              .maxAge(Duration.ofDays(15)) // ✅ Token validity period
              .build();

    } else {
      // Create HttpOnly Cookie
      jwtCookie =
          ResponseCookie.from("auth_token", token)
              .httpOnly(true) // Prevent JavaScript access
              .secure(true) // HTTPS transfer only
              .sameSite("None") // Prevent CSRF
              .path("/") // Applies to entire application
              .maxAge(Duration.ofMinutes(15)) // Token validity period
              .build();
    }
    response.setHeader("Set-Cookie", jwtCookie.toString());
  }
}
