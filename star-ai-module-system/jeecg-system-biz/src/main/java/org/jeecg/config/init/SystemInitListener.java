package org.jeecg.config.init;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.config.JeecgCloudCondition;
import org.jeecg.modules.system.service.ISysGatewayRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * @desc: Bootstrap program; initializes route configuration
 * @author: flyme
 */
@Slf4j
@Component
@Conditional(JeecgCloudCondition.class)
public class SystemInitListener implements ApplicationListener<ApplicationReadyEvent>, Ordered {


    @Autowired
    private ISysGatewayRouteService sysGatewayRouteService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {

        log.info(" Service started, initializing route configuration ###################");
        String context = "AnnotationConfigServletWebServerApplicationContext";
        if (applicationReadyEvent.getApplicationContext().getDisplayName().indexOf(context) > -1) {
            try {
                sysGatewayRouteService.addRoute2Redis(CacheConstant.GATEWAY_ROUTES);
            } catch (Exception e) {
                // Do not crash the whole JVM if Redis Cluster/ACL is misconfigured at ready-time;
                // gateway routes can be refreshed later. Log loud for ops.
                log.error(
                    "init gateway routes to Redis failed (service stays up): {}",
                    e.toString(),
                    e);
            }
        }

    }

    @Override
    public int getOrder() {
        return 1;
    }
}
