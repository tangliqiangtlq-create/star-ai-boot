package org.jeecg.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * On startup, modify DruidWallConfig
 * Allow SELECT WHERE clause to be a tautology
 * @author eightmonth
 * @date 2024/4/8 11:37
 */
public class DruidWallConfigRegister implements SpringApplicationRunListener {

    public SpringApplication application;

    private String[] args;


    /**
     * Required; startup fails without it
     * @param application
     * @param args
     */
    public DruidWallConfigRegister(SpringApplication application, String[] args) {
        this.application = application;
        this.args = args;
    }

    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.dynamic.druid.wall.selectWhereAlwayTrueCheck", false);

        MutablePropertySources propertySources = env.getPropertySources();

        PropertySource<Map<String, Object>> propertySource = new MapPropertySource("jeecg-datasource-config", props);

        propertySources.addLast(propertySource);
    }
}