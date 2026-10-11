package org.jeecg.config.sign.interceptor;

import java.util.SortedMap;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.util.DateUtils;
import org.jeecg.common.util.HttpResponseUtils;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.sign.util.BodyReaderHttpServletRequestWrapper;
import org.jeecg.config.sign.util.HttpUtils;
import org.jeecg.config.sign.util.SignUtil;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Signature interceptor
 * @author qinfeng
 */
@Slf4j
public class SignAuthInterceptor implements HandlerInterceptor {
    /**
     * 5-minute validity
     */
    private final static long MAX_EXPIRE = 5 * 60;

    private boolean signEnabled = true;

    public void setSignEnabled(boolean signEnabled) {
        this.signEnabled = signEnabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!signEnabled) {
            return true;
        }
        log.info("Sign Interceptor request URI = " + request.getRequestURI());
        HttpServletRequest requestWrapper = new BodyReaderHttpServletRequestWrapper(request);
        // Get all params (URL and body)
        SortedMap<String, String> allParams = HttpUtils.getAllParams(requestWrapper);
        // Sign-verify parameters
        String headerSign = request.getHeader(CommonConstant.X_SIGN);
        String xTimestamp = request.getHeader(CommonConstant.X_TIMESTAMP);
        
        if(oConvertUtils.isEmpty(xTimestamp)){
            Result<?> result = Result.error(I18nUtil.getMessage("signAuth.timestampEmpty"));
            log.error("Sign verification failed! Header xTimestamp is empty");
            HttpResponseUtils.writeJson(response, result);
            return false;
        }

        // Client time
        Long clientTimestamp = Long.parseLong(xTimestamp);

        int length = 14;
        int length1000 = 1000;
        //1. Verify signature timestamp (compatible with old/new X_TIMESTAMP formats)
        if (xTimestamp.length() == length) {
            //a. X_TIMESTAMP format is yyyyMMddHHmmss (example: 20220308152143)
            if ((DateUtils.getCurrentTimestamp() - clientTimestamp) > MAX_EXPIRE) {
                log.error("Signature verification failed: X-TIMESTAMP expired, check if system time differs from server time!");
                throw new IllegalArgumentException("Signature verification failed: X-TIMESTAMP expired");
            }
        } else {
            //b. X_TIMESTAMP format is timestamp (example: 1646552406000)
            if ((System.currentTimeMillis() - clientTimestamp) > (MAX_EXPIRE * length1000)) {
                log.error("Signature verification failed: X-TIMESTAMP expired, check if system time differs from server time!");
                throw new IllegalArgumentException("Signature verification failed: X-TIMESTAMP expired");
            }
        }

        //2. Verify signature
        boolean isSigned = SignUtil.verifySign(allParams,headerSign);

        if (isSigned) {
            log.debug("Sign verification passed! Header Sign: {}",headerSign);
            return true;
        } else {
            log.info("sign allParams: {}", allParams);
            log.error("request URI = " + request.getRequestURI());
            log.error("Sign verification failed! Header Sign: {}",headerSign);
            Result<?> result = Result.error(I18nUtil.getMessage("signAuth.verifyFailed"));
            HttpResponseUtils.writeJson(response, result);
            return false;
        }
    }

}
