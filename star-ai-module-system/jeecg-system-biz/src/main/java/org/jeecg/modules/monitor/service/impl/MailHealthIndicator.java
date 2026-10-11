package org.jeecg.modules.monitor.service.impl;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom email validation
 *
 * @author: Li Bo
 * @email: 503378406@qq.com
 * @date: 2019-06-29
 */
@Component
public class MailHealthIndicator implements HealthIndicator {


    @Override public Health health() {
        int errorCode = check();
        if (errorCode != 0) {
            return Health.down().withDetail("Error Code", errorCode) .build();
        }
        return Health.up().build();
    }
    int check(){
        // Custom DB detection logic can be implemented
        return 0;
    }
}
