package org.jeecg.config.sign.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.http.HttpMethod;

import com.alibaba.fastjson.JSONObject;

/**
 * HTTP utility: extract parameters from the request
 *
 * @author jeecg
 * @date 20210621
 */
@Slf4j
public class HttpUtils {

    /**
     * Merge URL parameters and body parameters
     *
     * @author jeecg
     * @date 20210621
     * @param request
     */
    public static SortedMap<String, String> getAllParams(HttpServletRequest request) throws IOException {

        SortedMap<String, String> result = new TreeMap<>();
        // Get trailing comma param from URL sys/dict/getDictItems/sys_user,realname,username
        String pathVariable = request.getRequestURI().substring(request.getRequestURI().lastIndexOf("/") + 1);
        if (pathVariable.contains(SymbolConstant.COMMA)) {
            log.info(" pathVariable: {}",pathVariable);
            String deString = URLDecoder.decode(pathVariable, "UTF-8");
          
            //https://www.52dianzi.com/category/article/37/565371.html
            if(deString.contains("%")){
                try {
                    deString = URLDecoder.decode(deString, "UTF-8");
                    log.info("When % exists, perform double decode — pathVariable decode: {}",deString);
                } catch (Exception e) {
                    //log.error("operation failed", e);
                }
            }
            log.info(" pathVariable decode: {}",deString);
            result.put(SignUtil.X_PATH_VARIABLE, deString);
        }
        // Get URL params
        Map<String, String> urlParams = getUrlParams(request);
        for (Map.Entry entry : urlParams.entrySet()) {
            // Cannot cast directly to String; type conversion error
            result.put((String)entry.getKey(), String.valueOf(entry.getValue()));
        }
        Map<String, String> allRequestParam = new HashMap<>(16);
        // GET requests do not need body parameters
        if (!HttpMethod.GET.name().equals(request.getMethod())) {
            allRequestParam = getAllRequestParam(request);
        }
        // Merge URL params and body params
        if (allRequestParam != null) {
            for (Map.Entry entry : allRequestParam.entrySet()) {
                // Cannot cast directly to String; type conversion error
                result.put((String)entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    /**
     * Merge URL parameters and body parameters
     *
     * @author jeecg
     * @date 20210621
     * @param queryString
     */
    public static SortedMap<String, String> getAllParams(String url, String queryString, byte[] body, String method)
        throws IOException {

        SortedMap<String, String> result = new TreeMap<>();
        // Get trailing comma param from URL sys/dict/getDictItems/sys_user,realname,username
        String pathVariable = url.substring(url.lastIndexOf("/") + 1);
        if (pathVariable.contains(SymbolConstant.COMMA)) {
            log.info(" pathVariable: {}",pathVariable);
            String deString = URLDecoder.decode(pathVariable, "UTF-8");
           
            //https://www.52dianzi.com/category/article/37/565371.html
            if(deString.contains("%")){
                deString = URLDecoder.decode(deString, "UTF-8");
                log.info("When % exists, perform double decode — pathVariable decode: {}",deString);
            }
            log.info(" pathVariable decode: {}",deString);
            result.put(SignUtil.X_PATH_VARIABLE, deString);
        }
        // Get URL params
        Map<String, String> urlParams = getUrlParams(queryString);
        for (Map.Entry entry : urlParams.entrySet()) {
            result.put((String)entry.getKey(), (String)entry.getValue());
        }
        Map<String, String> allRequestParam = new HashMap<>(16);
        // GET requests do not need body parameters
        if (!HttpMethod.GET.name().equals(method)) {
            allRequestParam = getAllRequestParam(body);
        }
        // Merge URL params and body params
        if (allRequestParam != null) {
            for (Map.Entry entry : allRequestParam.entrySet()) {
                result.put((String)entry.getKey(), (String)entry.getValue());
            }
        }
        return result;
    }

    /**
     * Get Body Parameter
     *
     * @date 15:04 20210621
     * @param request
     */
    public static Map<String, String> getAllRequestParam(final HttpServletRequest request) throws IOException {

        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(request.getInputStream()))) {
          String str;
          StringBuilder wholeStr = new StringBuilder();
          while ((str = reader.readLine()) != null) {
            wholeStr.append(str);
          }
          return JSONObject.parseObject(wholeStr.toString(), Map.class);
        }
    }

    /**
     * Get Body Parameter
     *
     * @date 15:04 20210621
     * @param body
     */
    public static Map<String, String> getAllRequestParam(final byte[] body) throws IOException {
        if(body==null){
            return null;
        }
        String wholeStr = new String(body);
        // Convert to JSON object
        return JSONObject.parseObject(wholeStr.toString(), Map.class);
    }

    /**
     * Convert URL request parameters to Map
     *
     * @param request
     */
    public static Map<String, String> getUrlParams(HttpServletRequest request) {
        Map<String, String> result = new HashMap<>(16);
        if (oConvertUtils.isEmpty(request.getQueryString())) {
            return result;
        }
        String param = "";
        try {
            param = URLDecoder.decode(request.getQueryString(), "utf-8");
        } catch (UnsupportedEncodingException e) {
            log.error("operation failed", e);
        }
        String[] params = param.split("&");
        for (String s : params) {
            int index = s.indexOf("=");
            //update-begin---author:chenrui ---date:20240222  for: [issues/5879] exception when data query passes ds="" ------------
            if (index != -1) {
                result.put(s.substring(0, index), s.substring(index + 1));
            }
            //update-end---author:chenrui ---date:20240222  for: [issues/5879] exception when data query passes ds="" ------------
        }
        return result;
    }

    /**
     * Convert URL request parameters to Map
     * 
     * @param queryString
     */
    public static Map<String, String> getUrlParams(String queryString) {
        Map<String, String> result = new HashMap<>(16);
        if (oConvertUtils.isEmpty(queryString)) {
            return result;
        }
        String param = "";
        try {
            param = URLDecoder.decode(queryString, "utf-8");
        } catch (UnsupportedEncodingException e) {
            log.error("operation failed", e);
        }
        String[] params = param.split("&");
        for (String s : params) {
            int index = s.indexOf("=");
            //update-begin---author:chenrui ---date:20240222  for: [issues/5879] exception when data query passes ds="" ------------
            if (index != -1) {
                result.put(s.substring(0, index), s.substring(index + 1));
            }
            //update-end---author:chenrui ---date:20240222  for: [issues/5879] exception when data query passes ds="" ------------
        }
        return result;
    }
}