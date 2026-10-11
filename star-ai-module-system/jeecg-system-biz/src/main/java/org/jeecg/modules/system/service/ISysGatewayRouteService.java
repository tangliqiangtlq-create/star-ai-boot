package org.jeecg.modules.system.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.system.entity.SysGatewayRoute;

/**
 * @Description: Gateway route management
 * @Author: jeecg-boot
 * @Date:   2020-05-26
 * @Version: V1.0
 */
public interface ISysGatewayRouteService extends IService<SysGatewayRoute> {

    /**
     * Add all route info to redis
     * @param key
     */
     void addRoute2Redis(String key);

    /**
     * Delete route.
     * @param id
     */
     void deleteById(String id);

    /**
     * Save route configuration.
     * @param array
     */
    void updateAll(JSONObject array);

    /**
     * Clear route info in redis
     */
    void clearRedis();

}
