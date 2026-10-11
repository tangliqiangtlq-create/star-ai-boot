package org.jeecg.config;

import org.jeecg.common.constant.CommonConstant;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * CORS configuration load condition
 * @author: jeecg-boot
 */
public class CorsFilterCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Object object = context.getEnvironment().getProperty(CommonConstant.CLOUD_SERVER_KEY);
        // If service discovery config is absent, it is a monolithic app; load CORS config and return true
        if(object==null){
            return true;
        }
        return false;
    }
}
