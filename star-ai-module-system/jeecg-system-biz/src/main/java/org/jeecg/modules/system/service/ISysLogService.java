package org.jeecg.modules.system.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jeecg.modules.system.entity.SysLog;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * System log table service
 * </p>
 *
 * @Author zhangweijian
 * @since 2018-12-26
 */
public interface ISysLogService extends IService<SysLog> {

	/**
	 * Clear all log records
	 */
	public void removeAll();
	
	/**
	 * Get total visit count
	 *
	 * @return Long
	 */
	Long findTotalVisitCount();

	//update-begin--Author:zhangweijian  Date:20190428 for: pass begin/end time params
	/**
	 * Get today's visit count
     * @param dayStart
	 * @param dayEnd
	 * @return Long
	 */
	Long findTodayVisitCount(Date dayStart, Date dayEnd);

	/**
	 * Get today's visit IP count
	 * @param dayStart start time
     * @param dayEnd end time
	 * @return Long
	 */
	Long findTodayIp(Date dayStart, Date dayEnd);
	//update-end--Author:zhangweijian  Date:20190428 for: pass begin/end time params
	
	/**
	 *   Home page: visit count / IP count by time range
	 * @param dayStart
	 * @param dayEnd
	 * @return
	 */
	List<Map<String,Object>> findVisitCount(Date dayStart, Date dayEnd);

	/**
	 * Delete sys_log rows older than {@code cutoff} in bounded 1000-row batches so we never hold a
	 * table-level lock for long enough to cause latency spikes on the interactive query path.
	 *
	 * @param cutoff rows with create_time older or equal to this timestamp are removed (softly, in batches)
	 */
	void deleteOldLogs(Date cutoff);
}
