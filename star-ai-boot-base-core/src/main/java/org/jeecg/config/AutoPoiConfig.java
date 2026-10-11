package org.jeecg.config;

import org.jeecgframework.core.util.ApplicationContextUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: Scott
 * @Date: 2018/2/7
 * @description: autopoi configuration class
 */

@Configuration
public class AutoPoiConfig {

    /**
     * Excel annotation dictionary support (import/export dict values with auto-translation).
     * Example: @Excel(name = "sex", width = 15, dicCode = "sex")
     * 1. On export, values 1,2 are translated to display labels per dict config;
     * 2. On import, display labels are translated to 1,2 before saving to the database;
     * @return
     */
    @Bean
    public ApplicationContextUtil applicationContextUtil() {
        return new org.jeecgframework.core.util.ApplicationContextUtil();
    }

}
