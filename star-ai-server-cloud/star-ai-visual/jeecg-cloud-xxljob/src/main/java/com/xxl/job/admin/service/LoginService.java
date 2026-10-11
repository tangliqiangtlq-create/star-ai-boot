package com.xxl.job.admin.service;

import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.dao.XxlJobUserDao;
import com.xxl.job.core.biz.model.ReturnT;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.util.DigestUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;

/**
 * @author xuxueli 2019-05-04 22:13:264
 */
@Configuration
public class LoginService {

    public static final String LOGIN_IDENTITY_KEY = "XXL_JOB_LOGIN_IDENTITY";

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(12);

    private static final long LEGACY_MD5_GRACE_PERIOD_MS = 14L * 24L * 3600L * 1000L;

    @Resource
    private XxlJobUserDao xxlJobUserDao;

    private void ensureBootstrapAdmin() {
        int userCount = xxlJobUserDao.pageListCount(0, 1, null, -1);
        if (userCount <= 0) {
            String envUsername = System.getenv("XXL_JOB_ADMIN_USERNAME");
            String envPassword = System.getenv("XXL_JOB_ADMIN_PASSWORD");
            String username = (envUsername != null && envUsername.trim().length() > 0) ? envUsername.trim() : "admin";
            String password =
                (envPassword != null && envPassword.trim().length() > 0) ? envPassword.trim() : null;
            if (hasText(password) && password.length() >= 4 && password.length() <= 50) {
                String passwordBcrypt = BCRYPT.encode(password);
                XxlJobUser newAdmin = new XxlJobUser();
                newAdmin.setUsername(username);
                newAdmin.setPassword(passwordBcrypt);
                newAdmin.setRole(1);
                newAdmin.setPermission(null);
                xxlJobUserDao.save(newAdmin);
            }
        }
    }

    public ReturnT<String> login(HttpServletRequest request, HttpServletResponse response, String username, String password, boolean ifRemember){

        // param
        if (!hasText(username) || !hasText(password)) {
            return new ReturnT<String>(500, I18nUtil.getString("login_param_empty"));
        }
        username = username.trim();
        password = password.trim();

        ensureBootstrapAdmin();

        XxlJobUser xxlJobUser = xxlJobUserDao.loadByUserName(username);
        if (xxlJobUser == null) {
            return new ReturnT<String>(500, I18nUtil.getString("login_param_unvalid"));
        }
        if (!hasText(xxlJobUser.getPassword())) {
            return new ReturnT<String>(500, I18nUtil.getString("login_param_unvalid"));
        }
        String passwordMd5 = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        MatchResult match = matchesStoredPassword(password, passwordMd5, xxlJobUser.getPassword());
        if (!match.ok) {
            return new ReturnT<String>(500, I18nUtil.getString("login_param_unvalid"));
        }

        if (match.legacyMd5Matched) {
            upgradeToBcryptAfterMd5Success(xxlJobUser, password);
        }

        // do login via HttpSession (server-side state), avoid relying on untrusted cookie data
        HttpSession session = request.getSession(true);
        session.setAttribute(LOGIN_IDENTITY_KEY, xxlJobUser.getUsername());
        return com.xxl.job.admin.core.util.ReturnTCompat.success();
    }

    /**
     * logout
     *
     * @param request
     * @param response
     */
    public ReturnT<String> logout(HttpServletRequest request, HttpServletResponse response){
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(LOGIN_IDENTITY_KEY);
        }
        return com.xxl.job.admin.core.util.ReturnTCompat.success();
    }

    /**
     * logout
     *
     * @param request
     * @return
     */
    public XxlJobUser ifLogin(HttpServletRequest request, HttpServletResponse response){
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object attr = session.getAttribute(LOGIN_IDENTITY_KEY);
            if (attr instanceof String) {
                String username = (String) attr;
                if (username != null && username.matches("^[a-zA-Z0-9_]+$")) {
                    XxlJobUser dbUser = xxlJobUserDao.loadByUserName(username);
                    if (dbUser != null) {
                        return dbUser;
                    }
                }
            }
        }
        return null;
    }

  private static boolean hasText(String value) {
    return StringUtils.hasText(value);
  }

  private static final class MatchResult {
    final boolean ok;
    final boolean legacyMd5Matched;
    MatchResult(boolean ok, boolean legacyMd5Matched) {
      this.ok = ok;
      this.legacyMd5Matched = legacyMd5Matched;
    }
  }

  private static MatchResult matchesStoredPassword(String rawPassword, String encodedMd5, String storedHash) {
    if (!hasText(rawPassword) || !hasText(encodedMd5) || !hasText(storedHash)) {
      return new MatchResult(false, false);
    }
    if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
      boolean bcryptMatch = BCRYPT.matches(rawPassword, storedHash);
      return new MatchResult(bcryptMatch, false);
    }
    boolean md5Match = encodedMd5.equals(storedHash);
    return new MatchResult(md5Match, md5Match);
  }

  private void upgradeToBcryptAfterMd5Success(XxlJobUser user, String rawPassword) {
    try {
      // XxlJobUser has no updateTime column; always upgrade MD5 → BCrypt on successful login.
      String newBcrypt = BCRYPT.encode(rawPassword);
      user.setPassword(newBcrypt);
      xxlJobUserDao.update(user);
    } catch (Exception ignored) {
    }
  }
}
