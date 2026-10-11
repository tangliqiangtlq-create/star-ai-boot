package org.jeecg.config.mybatis.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.jeecg.common.aspect.annotation.DynamicTable;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.config.mybatis.ThreadLocalDataHelper;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * Dynamic table switching aspect
 *
 * @author :zyf
 * @date:2020-04-25
 */
@Aspect
@Component
public class DynamicTableAspect {


    /**
     * Define aspect interception pointcut
     */
    @Pointcut("@annotation(org.jeecg.common.aspect.annotation.DynamicTable)")
    public void dynamicTable() {
    }


    @Around("dynamicTable()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        DynamicTable dynamicTable = method.getAnnotation(DynamicTable.class);
        HttpServletRequest request = SpringContextUtils.getHttpServletRequest();
        // Get version marker from front-end
        String version = request.getHeader(CommonConstant.VERSION);
        // Store version number in thread-local variable
        ThreadLocalDataHelper.put(CommonConstant.VERSION, version);
        // Store table name in thread-local variable
        ThreadLocalDataHelper.put(CommonConstant.DYNAMIC_TABLE_NAME, dynamicTable.value());
        //Execution method
        Object result = point.proceed();
        // Clear local variables
        ThreadLocalDataHelper.clear();
        return result;
    }

}
