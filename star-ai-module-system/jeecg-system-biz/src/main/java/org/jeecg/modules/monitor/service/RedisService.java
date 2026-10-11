package org.jeecg.modules.monitor.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.fastjson.JSONArray;
import org.jeecg.modules.monitor.domain.RedisInfo;
import org.jeecg.modules.monitor.exception.RedisConnectException;

/**
 * @Description: redisInfoserviceAPI
 * @author: jeecg-boot
 */
public interface RedisService {

	/**
	 * Get Redis detailed info
	 *
	 * @return List
     * @throws RedisConnectException
	 */
	List<RedisInfo> getRedisInfo() throws RedisConnectException;

	/**
	 * Get Redis key count
	 *
	 * @return Map
     * @throws RedisConnectException
	 */
	Map<String, Object> getKeysSize() throws RedisConnectException;

	/**
	 * Get Redis memory info
	 *
	 * @return Map
     * @throws RedisConnectException
	 */
	Map<String, Object> getMemoryInfo() throws RedisConnectException;
	/**
	 * Get Redis info required by reports
	 * @param type
	 * @return Map
     * @throws RedisConnectException
	 */
	Map<String, JSONArray> getMapForReport(String type) throws RedisConnectException ;

	/**
	 * Get historical performance metrics
	 * @return
	 * @author chenrui
	 * @date 2024/5/14 14:57
	 */
	Map<String, List<Map<String, Object>>> getMetricsHistory();

	/**
	 * CodePlan 36 / C2-3: list keys by pattern via SCAN (never KEYS). Result capped.
	 *
	 * @param pattern Redis MATCH pattern (must include a non-wildcard prefix)
	 * @param limit max keys to return (server-side also capped by RedisScanSupport)
	 * @return matched keys
	 */
	Set<String> scanKeysByPattern(String pattern, long limit);
}
