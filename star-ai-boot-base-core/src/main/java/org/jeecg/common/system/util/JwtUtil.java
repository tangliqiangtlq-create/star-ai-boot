package org.jeecg.common.system.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cn.dev33.satoken.stp.StpUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.base.Joiner;
import java.io.IOException;
import java.util.Date;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.DataBaseConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.constant.TenantConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.system.vo.SysUserCacheInfo;
import org.jeecg.common.util.DateUtils;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;

/**
 * @Author Scott @Date 2018-07-12 14:23 @Desc JWTTools
 */
public class JwtUtil {
  private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);


  /** TokenValid for24Hour(TokenexistreidsMedium cache time multiplied by2） */
  public static final long EXPIRE_TIME = 30L * 24 * 60 * 60 * 1000;

  static final String WELL_NUMBER = SymbolConstant.WELL_NUMBER + SymbolConstant.LEFT_CURLY_BRACKET;

  /**
   * @param response
   * @param code
   * @param errorMsg
   */
  public static void responseError(ServletResponse response, Integer code, String errorMsg) {
    HttpServletResponse httpServletResponse = (HttpServletResponse) response;
    Result jsonResult = new Result(code, errorMsg);
    jsonResult.setSuccess(false);
    try {
      httpServletResponse.setStatus(code);
      // application/json + nosniff + Anti-hijacking prefix (forbidden text/html bear JSON）
      org.jeecg.common.util.HttpResponseUtils.writeJson(httpServletResponse, jsonResult);
    } catch (IOException e) {
      // avoid printStackTrace leak to standard error stream
    }
  }

  /**
   * checktokenIs it correct?
   *
   * @param token key
   * @param secret User password
   * @return Is it correct?
   */
  public static boolean verify(String token, String username, String secret) {
    try {
      // according toPasswordgenerateJWTvalidator
      Algorithm algorithm = Algorithm.HMAC256(secret);
      JWTVerifier verifier = JWT.require(algorithm).withClaim("username", username).build();
      // EffectivenessTOKEN
      DecodedJWT jwt = verifier.verify(token);
      return true;
    } catch (Exception exception) {
      return false;
    }
  }

  /**
   * gettokenThe information in is not requiredsecretDecryption can also be obtained
   *
   * @return tokenincluded inUsername
   */
  public static String getUsername(String token) {
    try {
      DecodedJWT jwt = JWT.decode(token);
      return jwt.getClaim("username").asString();
    } catch (JWTDecodeException e) {
      return null;
    }
  }

  /**
   * Generate signature,5minexpires later
   *
   * @param username username
   * @param secret User password
   * @return Encryptoftoken
   */
  public static String sign(String username, String secret) {
    Date date = new Date(System.currentTimeMillis() + EXPIRE_TIME);
    Algorithm algorithm = Algorithm.HMAC256(secret);
    // Comes withusernameinformation
    return JWT.create().withClaim("username", username).withExpiresAt(date).sign(algorithm);
  }

  /**
   * according torequestintokenGet user account
   *
   * @param request
   * @return
   * @throws JeecgBootException
   */
  public static String getUserNameByToken(HttpServletRequest request) throws JeecgBootException {

    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");

    return loginUser.getUsername();
  }

  /**
   * fromsessionGet variables in
   *
   * @param key
   * @return
   */
  public static String getSessionData(String key) {
    // ${myVar}%
    // get${} The value behind
    String moshi = "";
    String wellNumber = WELL_NUMBER;

    if (key.indexOf(SymbolConstant.RIGHT_CURLY_BRACKET) != -1) {
      moshi = key.substring(key.indexOf("}") + 1);
    }
    String returnValue = null;
    if (key.contains(wellNumber)) {
      key = key.substring(2, key.indexOf("}"));
    }
    if (oConvertUtils.isNotEmpty(key)) {
      HttpSession session = SpringContextUtils.getHttpServletRequest().getSession();
      returnValue = (String) session.getAttribute(key);
    }
    // result plus${} The value behind
    if (returnValue != null) {
      returnValue = returnValue + moshi;
    }
    return returnValue;
  }

  /**
   * Get variables from current user
   *
   * @param key
   * @param user
   * @return
   */
  public static String getUserSystemData(String key, SysUserCacheInfo user) {
    if (user == null) {
      user = JeecgDataAutorUtils.loadUserInfo();
    }
    // #{sys_user_code}%

    // Get loginUser info
    LoginUser sysUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();

    String moshi = "";
    String wellNumber = WELL_NUMBER;
    if (key.indexOf(SymbolConstant.RIGHT_CURLY_BRACKET) != -1) {
      moshi = key.substring(key.indexOf("}") + 1);
    }
    String returnValue = null;
    // Processing for special markings#{sysOrgCode}，Judgment replacement
    if (key.contains(wellNumber)) {
      key = key.substring(2, key.indexOf("}"));
    } else {
      key = key;
    }
    // Replace with system login user account
    if (key.equals(DataBaseConstant.SYS_USER_CODE)
        || key.toLowerCase().equals(DataBaseConstant.SYS_USER_CODE_TABLE)) {
      if (user == null) {
        returnValue = sysUser.getUsername();
      } else {
        returnValue = user.getSysUserCode();
      }
    }
    // Replace with the real name of the system login user
    else if (key.equals(DataBaseConstant.SYS_USER_NAME)
        || key.toLowerCase().equals(DataBaseConstant.SYS_USER_NAME_TABLE)) {
      if (user == null) {
        returnValue = sysUser.getRealname();
      } else {
        returnValue = user.getSysUserName();
      }
    }

    // Replace with the Organization code used by the system user to log in
    else if (key.equals(DataBaseConstant.SYS_ORG_CODE)
        || key.toLowerCase().equals(DataBaseConstant.SYS_ORG_CODE_TABLE)) {
      if (user == null) {
        returnValue = sysUser.getOrgCode();
      } else {
        returnValue = user.getSysOrgCode();
      }
    }
    // Replaced with all owned by the system userOrganization code
    else if (key.equals(DataBaseConstant.SYS_MULTI_ORG_CODE)
        || key.toLowerCase().equals(DataBaseConstant.SYS_MULTI_ORG_CODE_TABLE)) {
      if (user == null) {
        // TODO Temporarily log in with userDepartment，There is a logical flaw and it is not owned by the user.Department
        returnValue = sysUser.getOrgCode();
      } else {
        if (user.isOneDepart()) {
          returnValue = user.getSysMultiOrgCode().get(0);
        } else {
          returnValue = Joiner.on(",").join(user.getSysMultiOrgCode());
        }
      }
    }
    // Replace with current system time(year month day)
    else if (key.equals(DataBaseConstant.SYS_DATE)
        || key.toLowerCase().equals(DataBaseConstant.SYS_DATE_TABLE)) {
      returnValue = DateUtils.formatDate();
    }
    // Replace with the current system time (year, month, day, hour, minute, and second)
    else if (key.equals(DataBaseConstant.SYS_TIME)
        || key.toLowerCase().equals(DataBaseConstant.SYS_TIME_TABLE)) {
      returnValue = DateUtils.now();
    }
    // Process status default value (default not initiated)
    else if (key.equals(DataBaseConstant.BPM_STATUS)
        || key.toLowerCase().equals(DataBaseConstant.BPM_STATUS_TABLE)) {
      returnValue = "1";
    }
    // update-begin-author:taoyan date:20210330 for:multi-tenantIDas system variable
    else if (key.equals(TenantConstant.TENANT_ID)
        || key.toLowerCase().equals(TenantConstant.TENANT_ID_TABLE)) {
      returnValue = SpringContextUtils.getHttpServletRequest().getHeader(CommonConstant.TENANT_ID);
    }
    // update-end-author:taoyan date:20210330 for:multi-tenantIDas system variable
    if (returnValue != null) {
      returnValue = returnValue + moshi;
    }
    return returnValue;
  }

  //	public static void main(String[] args) {
  //		 String token =
  // "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE1NjUzMzY1MTMsInVzZXJuYW1lIjoiYWRtaW4ifQ.xjhud_tWCNYBOg_aRlMgOdlZoWFFKB_givNElHNw3X0";
  //		 log.debug(String.valueOf(JwtUtil.getUsername(token)));
  //	}
}
