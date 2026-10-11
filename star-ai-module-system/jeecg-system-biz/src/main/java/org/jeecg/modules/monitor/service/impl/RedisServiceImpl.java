package org.jeecg.modules.monitor.service.impl;

import java.util.*;

import jakarta.annotation.Resource;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import org.jeecg.common.util.RedisScanSupport;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.monitor.domain.RedisInfo;
import org.jeecg.modules.monitor.exception.RedisConnectException;
import org.jeecg.modules.monitor.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis monitoring info retrieval
 *
 * @Author MrBird
 */
@Service("redisService")
@Slf4j
public class RedisServiceImpl implements RedisService {

	@Resource
	private RedisConnectionFactory redisConnectionFactory;

	@Autowired(required = false)
	private RedisTemplate<String, Object> redisTemplate;

	/**
	 * redisInfo
	 */
	private static final String REDIS_MESSAGE = "3";

	/**
	 * Redis performance metrics
	 */
	private static final Map<String,List<Map<String, Object>>> REDIS_METRICS = new HashMap<>(2);

	/**
	 * Redis details
	 */
	@Override
	public List<RedisInfo> getRedisInfo() throws RedisConnectException {
		Properties info = redisConnectionFactory.getConnection().info();
		List<RedisInfo> infoList = new ArrayList<>();
		if (info == null) {
			return infoList;
		}
		RedisInfo redisInfo = null;
		for (Map.Entry<Object, Object> entry : info.entrySet()) {
			redisInfo = new RedisInfo();
			redisInfo.setKey(oConvertUtils.getString(entry.getKey()));
			redisInfo.setValue(oConvertUtils.getString(entry.getValue()));
			infoList.add(redisInfo);
		}
		return infoList;
	}

	@Override
	public Map<String, Object> getKeysSize() throws RedisConnectException {
		Long dbSize = redisConnectionFactory.getConnection().dbSize();
		Map<String, Object> map = new HashMap(5);
		map.put("create_time", System.currentTimeMillis());
		map.put("dbSize", dbSize != null ? dbSize : 0L);

		log.debug("--getKeysSize--: " + map.toString());
		return map;
	}

	@Override
	public Map<String, Object> getMemoryInfo() throws RedisConnectException {
		Map<String, Object> map = new HashMap<>(5);
		map.put("create_time", System.currentTimeMillis());
		// Prefer memory section under cluster/sentinel (works on single node too); fall back to full info
		Properties info = redisConnectionFactory.getConnection().info("memory");
		if (info == null) {
			info = redisConnectionFactory.getConnection().info();
		}
		if (info != null) {
			for (Map.Entry<Object, Object> entry : info.entrySet()) {
				String key = oConvertUtils.getString(entry.getKey());
				if ("used_memory".equals(key)) {
					map.put("used_memory", entry.getValue());
					break;
				}
			}
		}
		if (!map.containsKey("used_memory")) {
			log.warn("Redis INFO did not contain used_memory (cluster/proxy or different Redis version)");
		}
		log.debug("--getMemoryInfo--: {}", map);
		return map;
	}

    /**
     * Query Redis information for reports
     * @param type 1 Redis key count, 2 memory usage, 3 Redis info
     * @return
     * @throws RedisConnectException
     */
	@Override
	public Map<String, JSONArray> getMapForReport(String type)  throws RedisConnectException {
		Map<String,JSONArray> mapJson=new HashMap(5);
		JSONArray json = new JSONArray();
		if(REDIS_MESSAGE.equals(type)){
			List<RedisInfo> redisInfo = getRedisInfo();
			for(RedisInfo info:redisInfo){
				Map<String, Object> map= Maps.newHashMap();
				BeanMap beanMap = BeanMap.create(info);
				for (Object key : beanMap.keySet()) {
					map.put(key+"", beanMap.get(key));
				}
				json.add(map);
			}
			mapJson.put("data",json);
			return mapJson;
		}
		int length = 5;
		for(int i = 0; i < length; i++){
			JSONObject jo = new JSONObject();
			Map<String, Object> map;
			if("1".equals(type)){
				map= getKeysSize();
				jo.put("value",map.get("dbSize"));
			}else{
				map = getMemoryInfo();
				Object usedMemoryObj = map.get("used_memory");
				int usedMemory = (usedMemoryObj != null) ? Integer.parseInt(usedMemoryObj.toString()) : 0;
				jo.put("value", usedMemory / 1000);
			}
			String createTime = DateUtil.formatTime(DateUtil.date((Long) map.get("create_time")-(4-i)*1000));
			jo.put("name",createTime);
			json.add(jo);
		}
		mapJson.put("data",json);
		return mapJson;
	}

	//update-begin---author:chenrui ---date:20240514  for: [QQYUN-9247] system monitor optimization ------------
	/**
	 * Get historical performance metrics
	 * @return
	 * @author chenrui
	 * @date 2024/5/14 14:57
	 */
	@Override
	public Map<String, List<Map<String, Object>>> getMetricsHistory() {
		return REDIS_METRICS;
	}

	/**
	 * Record Redis metrics for the past hour <br/>
	 * Every 60s, store keysize and memory
	 * @throws RedisConnectException
	 * @author chenrui
	 * @date 2024/5/14 14:09
	 */
	@Scheduled(fixedRate = 60000)
	public void recordCustomMetric() throws RedisConnectException {
		List<Map<String, Object>> list= new ArrayList<>();
		if(REDIS_METRICS.containsKey("dbSize")){
			list = REDIS_METRICS.get("dbSize");
		}else{
			REDIS_METRICS.put("dbSize",list);
		}
		if(list.size()>60){
			list.remove(0);
		}
		list.add(getKeysSize());
		list= new ArrayList<>();
		if(REDIS_METRICS.containsKey("memory")){
			list = REDIS_METRICS.get("memory");
		}else{
			REDIS_METRICS.put("memory",list);
		}
		if(list.size()>60){
			list.remove(0);
		}
		list.add(getMemoryInfo());
	}
	//update-end---author:chenrui ---date:20240514  for: [QQYUN-9247] system monitor optimization ------------

	@Override
	public Set<String> scanKeysByPattern(String pattern, long limit) {
		if (redisTemplate == null || pattern == null || pattern.isBlank()) {
			return Collections.emptySet();
		}
		long effectiveLimit =
				limit <= 0
						? Math.min(1000L, RedisScanSupport.DEFAULT_SCAN_LIMIT)
						: Math.min(limit, RedisScanSupport.DEFAULT_SCAN_LIMIT);
		return RedisScanSupport.scanKeys(
				redisTemplate, pattern, RedisScanSupport.DEFAULT_SCAN_COUNT, effectiveLimit);
	}
}
