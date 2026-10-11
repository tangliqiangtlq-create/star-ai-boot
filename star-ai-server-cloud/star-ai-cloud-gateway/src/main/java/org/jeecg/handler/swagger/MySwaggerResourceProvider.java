package org.jeecg.handler.swagger;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Aggregates swagger APIs across services
 * @author zyf
 * @date: 2022/4/21 10:55
 */
@Component
public class MySwaggerResourceProvider {
    private static final Logger log = LoggerFactory.getLogger(MySwaggerResourceProvider.class);
    /**
     * Default Swagger2 URL suffix.
     */
    private static final String SWAGGER2URL = "/v2/api-docs";

    /**
     * Gateway route
     */
    private final RouteLocator routeLocator;
    /**
     * Nacos naming service
     */
    private NamingService naming;

    /**
     * Nacos service address.
     */
    @Value("${spring.cloud.nacos.discovery.server-addr}")
    private String serverAddr;
    /**
     * nacos namespace
     */
    @Value("${spring.cloud.nacos.discovery.namespace:#{null}}")
    private String namespace;
    
    /**
     * nacos groupName
     */
    @Value("${spring.cloud.nacos.config.group:DEFAULT_GROUP:#{null}}")
    private String group;
    
    /**
     * nacos username
     */
    @Value("${spring.cloud.nacos.discovery.username:#{null}}")
    private String username;
    /**
     * nacos password
     */
    @Value("${spring.cloud.nacos.discovery.password:#{null}}")
    private String password;
    
    /**
     * Services to exclude from Swagger.
     */
    private String[] excludeServiceIds=new String[]{};


    /**
     * Gateway application name
     */
    @Value("${spring.application.name}")
    private String self;

    @Autowired
    public MySwaggerResourceProvider(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
    }

    public List<GatewaySwaggerResource> get() {
        List<GatewaySwaggerResource> resources = new ArrayList<>();
        List<String> routeHosts = new ArrayList<>();
        // Get all available hosts: serviceId
        routeLocator.getRoutes().filter(route -> route.getUri().getHost() != null)
                .filter(route -> !self.equals(route.getUri().getHost()))
                .subscribe(route ->{
                    //update-begin---author:zyf ---date:20220413 for: filter invalid routes to avoid API doc errors
                    boolean hasRoute=checkRoute(route.getId());
                    if(hasRoute){
                        routeHosts.add(route.getUri().getHost());
                    }
                    //update-end---author:zyf ---date:20220413 for: filter invalid routes to avoid API doc errors
                });

        // Track servers already added; same app may register multiple services on Nacos
        Set<String> dealed = new HashSet<>();
        routeHosts.forEach(instance -> {
            // Concatenate URL
            String url = "/" + instance.toLowerCase() + SWAGGER2URL;
            if (!dealed.contains(url)) {
                dealed.add(url);
                log.info(" Gateway add SwaggerResource: {}",url);
                GatewaySwaggerResource swaggerResource = new GatewaySwaggerResource();
                swaggerResource.setUrl(url);
                swaggerResource.setSwaggerVersion("2.0");
                swaggerResource.setName(instance);
                //Swagger exclude services not shown
                if(!ArrayUtil.contains(excludeServiceIds,instance)){
                    resources.add(swaggerResource);
                }
            }
        });
        return resources;
    }

    /**
     * Check whether Nacos has healthy instances
     * @param routeId
     * @return
     */
    private Boolean checkRoute(String routeId) {
        Boolean hasRoute = false;
        try {
            // Fix gateway swagger missing API docs when started with namespace
            Properties properties=new Properties();
            properties.setProperty("serverAddr",serverAddr);
            if(namespace!=null && !"".equals(namespace)){
                log.info("nacos.discovery.namespace = {}", namespace);
                properties.setProperty("namespace",namespace);
            }
            if(username!=null && !"".equals(username)){
                properties.setProperty("username",username);
            }
            if(password!=null && !"".equals(password)){
                properties.setProperty("password",password);
            }
            //[issues/5115] gateway OOM caused by swagger docs
            if (this.naming == null) {
                this.naming = NamingFactory.createNamingService(properties);
            }
            log.info(" config.group : {}", group);
            List<Instance> list = this.naming.selectInstances(routeId, group , true);
            if (ObjectUtil.isNotEmpty(list)) {
                hasRoute = true;
            }
        } catch (com.alibaba.nacos.api.exception.NacosException | RuntimeException e) {
            log.error("operation failed", e);
        }
        return hasRoute;
    }

    public static class GatewaySwaggerResource {
        private String name;
        private String url;
        private String swaggerVersion;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSwaggerVersion() {
            return swaggerVersion;
        }

        public void setSwaggerVersion(String swaggerVersion) {
            this.swaggerVersion = swaggerVersion;
        }
    }
}
