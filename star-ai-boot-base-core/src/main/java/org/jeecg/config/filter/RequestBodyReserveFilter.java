package org.jeecg.config.filter;

import org.jeecg.common.constant.CommonConstant;
import org.jeecg.config.sign.util.BodyReaderHttpServletRequestWrapper;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * For POST requests, wraps HttpServletRequest to preserve body parameters
 * @Author taoYan
 * @Date 2022/4/25 19:19
 **/
public class RequestBodyReserveFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        ServletRequest requestWrapper = null;

        if(servletRequest instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) servletRequest;
            // For POST requests only, read POST body
            if(CommonConstant.HTTP_POST.equals(req.getMethod())){
                requestWrapper = new BodyReaderHttpServletRequestWrapper(req);
            }
        }

        if(requestWrapper == null) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            filterChain.doFilter(requestWrapper, servletResponse);
        }
    }
}
