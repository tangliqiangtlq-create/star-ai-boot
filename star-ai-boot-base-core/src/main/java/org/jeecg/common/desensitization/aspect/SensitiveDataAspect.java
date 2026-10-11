package org.jeecg.common.desensitization.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.jeecg.common.desensitization.annotation.SensitiveDecode;
import org.jeecg.common.desensitization.annotation.SensitiveEncode;
import org.jeecg.common.desensitization.util.SensitiveInfoUtil;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Sensitive data aspect processing class
 * @Author taoYan
 * @Date 2022/4/20 17:45
 **/
@Slf4j
@Aspect
@Component
public class SensitiveDataAspect {

    /**
     * Define pointcut
     */
    @Pointcut("@annotation(org.jeecg.common.desensitization.annotation.SensitiveEncode) || @annotation(org.jeecg.common.desensitization.annotation.SensitiveDecode)")
    public void sensitivePointCut() {
    }

    @Around("sensitivePointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        // ProcessResult
        Object result = point.proceed();
        if(result == null){
            return result;
        }
        Class resultClass = result.getClass();
        log.debug(" resultClass  = {}" , resultClass);

        if(resultClass.isPrimitive()){
            //It is a basic type and is returned directly without processing.
            return result;
        }
        // Get method annotation information: which entity it is, whether it is Encrypt or Decrypt
        boolean isEncode = true;
        Class entity = null;
        MethodSignature methodSignature = (MethodSignature) point.getSignature();
        Method method = methodSignature.getMethod();
        SensitiveEncode encode = method.getAnnotation(SensitiveEncode.class);
        if(encode==null){
            SensitiveDecode decode = method.getAnnotation(SensitiveDecode.class);
            if(decode!=null){
                entity = decode.entity();
                isEncode = false;
            }
        }else{
            entity = encode.entity();
        }

        long startTime=System.currentTimeMillis();
        if(resultClass.equals(entity) || entity.equals(Object.class)){
            // The entity returned by the method is the same as the entity of the annotation. If the annotation does not declare the entity attribute, it is considered to be (the entity returned by the method is the same as the entity of the annotation)
            SensitiveInfoUtil.handlerObject(result, isEncode);
        } else if(result instanceof List){
            // Method returns List<entity>
            SensitiveInfoUtil.handleList(result, entity, isEncode);
        }else{
            // Method returns an object
            SensitiveInfoUtil.handleNestedObject(result, entity, isEncode);
        }
        long endTime=System.currentTimeMillis();
        log.info((isEncode ? "Encryption operation, " : "Decryption operation, ") + "Aspect execution elapsed: " + (endTime - startTime) + "ms");

        return result;
    }

}
