package org.jeecg.common.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.ServiceNameConstants;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @Description: spring context tool class
 * @author: jeecg-boot
 */
@Component
public class SpringContextUtils implements ApplicationContextAware {

	/**
	 * Context object instance
	 */
	private static ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		SpringContextUtils.applicationContext = applicationContext;
	}

	/**
	 * GetapplicationContext
	 *
	 * @return
	 */
	public static ApplicationContext getApplicationContext() {
		return applicationContext;
	}

	/**
	  *  GetHttpServletRequest
	 */
	public static HttpServletRequest getHttpServletRequest() {
		return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
	}

	/**
	 * Null-safe variant of {@link #getHttpServletRequest()} for background threads (MQ, jobs, etc.)
	 * where no servlet request context is bound. Returns {@code null} instead of throwing NPE.
	 */
	public static HttpServletRequest getHttpServletRequestQuietly() {
		try {
			Object attrs = RequestContextHolder.getRequestAttributes();
			if (!(attrs instanceof ServletRequestAttributes)) return null;
			return ((ServletRequestAttributes) attrs).getRequest();
		} catch (Throwable ignore) {
			return null;
		}
	}
	/**
	 * GetHttpServletResponse
	 */
	public static HttpServletResponse getHttpServletResponse() {
		return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
	}

	/**
	*  Get project root path basePath
	*/
	public static String getDomain(){
		HttpServletRequest request = getHttpServletRequest();
		StringBuffer url = request.getRequestURL();
		//1. In the case of microservices, obtain the basePath of the gateway
		String basePath = request.getHeader(ServiceNameConstants.X_GATEWAY_BASE_PATH);
		if(oConvertUtils.isNotEmpty(basePath)){
			return basePath;
		}else{
			String domain = url.delete(url.length() - request.getRequestURI().length(), url.length()).toString();
			//2. [Compat] After SSL termination, request.getScheme() may not return https
			// https://blog.csdn.net/weixin_34376986/article/details/89767950
			String scheme = request.getHeader(CommonConstant.X_FORWARDED_SCHEME);
			if(scheme!=null && !request.getScheme().equals(scheme)){
				domain = domain.replace(request.getScheme(),scheme);
			}
			return domain;
		}
	}

	public static String getOrigin(){
		HttpServletRequest request = getHttpServletRequest();
		return request.getHeader("x_gateway_base_path");
	}
	
	/**
	 * ApprovenameGet Bean.
	 *
	 * @param name
	 * @return
	 */
	public static Object getBean(String name) {
		return getApplicationContext().getBean(name);
	}

	/**
	 * ApproveclassGetBean.
	 *
	 * @param clazz
	 * @param       <T>
	 * @return
	 */
	public static <T> T getBean(Class<T> clazz) {
		return getApplicationContext().getBean(clazz);
	}

	/**
	 * Get specified Bean by name and clazz
	 *
	 * @param name
	 * @param clazz
	 * @param       <T>
	 * @return
	 */
	public static <T> T getBean(String name, Class<T> clazz) {
		return getApplicationContext().getBean(name, clazz);
	}
}
