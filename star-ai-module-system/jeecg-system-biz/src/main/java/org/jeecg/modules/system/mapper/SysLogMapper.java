package org.jeecg.modules.system.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.system.entity.SysLog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * SystemLogTable Mapper API
 * </p>
 *
 * @Author zhangweijian
 * @since 2018-12-26
 */
public interface SysLogMapper extends BaseMapper<SysLog> {

	/**
	 * Clear all log records
	 */
	public void removeAll();

	/**
	 * Batch delete logs older than cutoff (C1-4 / B3-4 — DELETE LIMIT).
	 *
	 * @return rows deleted in this batch
	 */
	int deleteBatchBefore(@Param("cutoff") Date cutoff, @Param("limit") int limit);

	/**
	 * Get total visit count (deduplicated by IP)
	 *
	 * @return Long
	 */
	Long findTotalVisitCount();

	//update-begin--Author:zhangweijian  Date:20190428 for: pass begin/end time params
	/**
	 * Get today's visit count (deduplicated by IP)
	 * @param dayStart start time
     * @param dayEnd end time
	 * @return Long
	 */
	Long findTodayVisitCount(@Param("dayStart") Date dayStart, @Param("dayEnd") Date dayEnd);

	/**
	 * Get today's unique visit IP count (deduplicated by IP)
	 * @param dayStart start time
     * @param dayEnd end time
	 * @return Long
	 */
	Long findTodayIp(@Param("dayStart") Date dayStart, @Param("dayEnd") Date dayEnd);
	//update-end--Author:zhangweijian  Date:20190428 for: pass begin/end time params
	
	/**
	 *   Home page: daily visit count (deduplicated by IP)
	 * @param dayStart
	 * @param dayEnd
     * @param dbType
	 * @return
	 */
	List<Map<String,Object>> findVisitCount(@Param("dayStart") Date dayStart, @Param("dayEnd") Date dayEnd, @Param("dbType") String dbType);
}
