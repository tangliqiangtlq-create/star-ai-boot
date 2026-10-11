package org.jeecg.common.util;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * Invoke Restful API Util
 *
 * @author sunjianlei
 */
@Slf4j
public class RestUtil {

    private static String domain = null;
    private static String path = null;
    
    private static String getDomain() {
        if (domain == null) {
            domain = SpringContextUtils.getDomain();
            // issues/2959
            // The microservice version integrates WeCom (Enterprise WeChat) single sign-on
            // Because the microservice version does not have a port number, the port number of the domain name obtained by the SpringContextUtils.getDomain() method becomes: -1, so there is a problem. You only need to remove the -1.
            String port=":-1";
            if (domain.endsWith(port)) {
                domain = domain.substring(0, domain.length() - 3);
            }
        }
        return domain;
    }

    private static String getPath() {
        if (path == null) {
            path = SpringContextUtils.getApplicationContext().getEnvironment().getProperty("server.servlet.context-path");
        }
        return oConvertUtils.getString(path);
    }

    public static String getBaseUrl() {
        String basepath = getDomain() + getPath();
        log.info(" RestUtil.getBaseUrl: " + basepath);
        return basepath;
    }

    /**
     * RestAPI invoker
     */
    private final static RestTemplate RT;

    static {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        RT = new RestTemplate(requestFactory);
        // Solve the problem of garbled characters
        RT.getMessageConverters().set(1, new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    public static RestTemplate getRestTemplate() {
        return RT;
    }

    /**
     * Send a GET request
     */
    public static JSONObject get(String url) {
        return getNative(url, null, null).getBody();
    }

    /**
     * Send a GET request
     */
    public static JSONObject get(String url, JSONObject variables) {
        return getNative(url, variables, null).getBody();
    }

    /**
     * Send a GET request
     */
    public static JSONObject get(String url, JSONObject variables, JSONObject params) {
        return getNative(url, variables, params).getBody();
    }

    /**
     * Send a GET request and return the native ResponseEntity
     */
    public static ResponseEntity<JSONObject> getNative(String url, JSONObject variables, JSONObject params) {
        return request(url, HttpMethod.GET, variables, params);
    }

    /**
     * Send POST request.
     */
    public static JSONObject post(String url) {
        return postNative(url, null, null).getBody();
    }

    /**
     * Send POST request.
     */
    public static JSONObject post(String url, JSONObject params) {
        return postNative(url, null, params).getBody();
    }

    /**
     * Send POST request.
     */
    public static JSONObject post(String url, JSONObject variables, JSONObject params) {
        return postNative(url, variables, params).getBody();
    }

    /**
     * Send POST request; returns the native ResponseEntity.
     */
    public static ResponseEntity<JSONObject> postNative(String url, JSONObject variables, JSONObject params) {
        return request(url, HttpMethod.POST, variables, params);
    }

    /**
     * Send a PUT request
     */
    public static JSONObject put(String url) {
        return putNative(url, null, null).getBody();
    }

    /**
     * Send a PUT request
     */
    public static JSONObject put(String url, JSONObject params) {
        return putNative(url, null, params).getBody();
    }

    /**
     * Send a PUT request
     */
    public static JSONObject put(String url, JSONObject variables, JSONObject params) {
        return putNative(url, variables, params).getBody();
    }

    /**
     * Send a PUT request and return the native ResponseEntity
     */
    public static ResponseEntity<JSONObject> putNative(String url, JSONObject variables, JSONObject params) {
        return request(url, HttpMethod.PUT, variables, params);
    }

    /**
     * Send DELETE request.
     */
    public static JSONObject delete(String url) {
        return deleteNative(url, null, null).getBody();
    }

    /**
     * Send DELETE request.
     */
    public static JSONObject delete(String url, JSONObject variables, JSONObject params) {
        return deleteNative(url, variables, params).getBody();
    }

    /**
     * Send a DELETE request and return the native ResponseEntity
     */
    public static ResponseEntity<JSONObject> deleteNative(String url, JSONObject variables, JSONObject params) {
        return request(url, HttpMethod.DELETE, null, variables, params, JSONObject.class);
    }

    /**
     * Send a request
     */
    public static ResponseEntity<JSONObject> request(String url, HttpMethod method, JSONObject variables, JSONObject params) {
        return request(url, method, getHeaderApplicationJson(), variables, params, JSONObject.class);
    }

    /**
     * Send a request
     *
     * @param url          request URL
     * @param method       HTTP method
     * @param headers      request headers (optional)
     * @param variables    URL query parameters (optional)
     * @param params       request body parameters (optional)
     * @param responseType response type
     * @return ResponseEntity<responseType>
     */
    public static <T> ResponseEntity<T> request(String url, HttpMethod method, HttpHeaders headers, JSONObject variables, Object params, Class<T> responseType) {
        log.info(" RestUtil  --- request ---  url = "+ url);
        if (StringUtils.isEmpty(url)) {
            throw new RuntimeException("url cannot be empty");
        }
        if (method == null) {
            throw new RuntimeException("method cannot be empty");
        }
        if (headers == null) {
            headers = new HttpHeaders();
        }
        // Request body
        String body = "";
        if (params != null) {
            if (params instanceof JSONObject) {
                body = ((JSONObject) params).toJSONString();

            } else {
                body = params.toString();
            }
        }
        // Splicing url parameters
        if (variables != null && !variables.isEmpty()) {
            url += ("?" + asUrlVariables(variables));
        }
        // Send request
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        return RT.exchange(url, method, request, responseType);
    }

    /**
     * Get JSON request header
     */
    public static HttpHeaders getHeaderApplicationJson() {
        return getHeader(MediaType.APPLICATION_JSON_UTF8_VALUE);
    }

    /**
     * Get request headers
     */
    public static HttpHeaders getHeader(String mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        headers.add("Accept", mediaType);
        return headers;
    }

    /**
     * Convert JSONObject to a=1&b=2&c=3...&n=n form
     */
    public static String asUrlVariables(JSONObject variables) {
        Map<String, Object> source = variables.getInnerMap();
        Iterator<String> it = source.keySet().iterator();
        StringBuilder urlVariables = new StringBuilder();
        while (it.hasNext()) {
            String key = it.next();
            String value = "";
            Object object = source.get(key);
            if (object != null) {
                if (!StringUtils.isEmpty(object.toString())) {
                    value = object.toString();
                }
            }
            urlVariables.append("&").append(key).append("=").append(value);
        }
        // Remove the first&
        return urlVariables.substring(1);
    }

}
