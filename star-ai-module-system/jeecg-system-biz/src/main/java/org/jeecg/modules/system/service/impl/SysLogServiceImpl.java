package org.jeecg.modules.system.service.impl;

import cn.hutool.core.date.DateUtil;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import com.baomidou.mybatisplus.annotation.DbType;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.modules.system.entity.SysLog;
import org.jeecg.modules.system.mapper.SysLogMapper;
import org.jeecg.modules.system.service.ISysLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * <p>
 * System log table service implementation
 * </p>
 *
 * @Author zhangweijian
 * @since 2018-12-26
 */
@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements ISysLogService {

	private static final Logger log = LoggerFactory.getLogger(SysLogServiceImpl.class);

	@Resource
	private SysLogMapper sysLogMapper;

	@Value("${jeecg.sys-log.cleanup.batch-size:1000}")
	private int cleanupBatchSize;

	@Value("${jeecg.sys-log.cleanup.safety-stop-rows:200000}")
	private long cleanupSafetyStopRows;

	@Autowired private JdbcTemplate jdbcTemplate;

	/**
	 * Delete sys_log rows older than {@code cutoff} in bounded 1000-row batches so we never hold a
	 * table-level lock for long enough to cause latency spikes on the interactive query path.
	 * Mirrors the bounded-delete pattern used by {@link #removeAll()} but exposes a cut-off based
	 * entry point for the XXL-Job cleanup job.
	 */
	@Override
	public void deleteOldLogs(Date cutoff) {
		if (cutoff == null) {
			return;
		}
		int batch = Math.min(Math.max(1, cleanupBatchSize), 1000);
		long total = 0L;
		java.sql.Timestamp ts = new java.sql.Timestamp(cutoff.getTime());
		String sql =
			"DELETE FROM sys_log WHERE create_time <= ? ORDER BY id LIMIT " + batch;
		while (true) {
			int affected;
			try {
				affected = jdbcTemplate.update(sql, ts);
			} catch (Exception e) {
				log.warn("[SysLogCleanup] deleteOldLogs batch failed: {}", e.getMessage());
				break;
			}
			if (affected <= 0) {
				break;
			}
			total += affected;
			if (total >= cleanupSafetyStopRows) {
				log.warn("[SysLogCleanup] deleteOldLogs safety stop cutoff={} after {} rows", cutoff, total);
				break;
			}
		}
		log.info("[SysLogCleanup] deleteOldLogs done cutoff={} rows~{}", cutoff, total);
	}

	/**
	 * Admin "clear all logs" — bounded batch delete (C1-4 / B3-4), never unbounded full-table DELETE.
	 */
	@Override
	public void removeAll() {
		long total = 0L;
		Date cutoff = DateUtil.date();
		while (true) {
			int deleted = sysLogMapper.deleteBatchBefore(cutoff, cleanupBatchSize);
			if (deleted <= 0) {
				break;
			}
			total += deleted;
			if (total >= cleanupSafetyStopRows) {
				log.warn("[SysLogCleanup] removeAll safety stop after {} rows", total);
				break;
			}
		}
		log.info("[SysLogCleanup] removeAll done, total~{}", total);
	}

	@Override
	public Long findTotalVisitCount() {
		return sysLogMapper.findTotalVisitCount();
	}

	//update-begin--Author:zhangweijian Date:20190428 for：pass begin/end time params
	@Override
	public Long findTodayVisitCount(Date dayStart, Date dayEnd) {
		return sysLogMapper.findTodayVisitCount(dayStart,dayEnd);
	}

	@Override
	public Long findTodayIp(Date dayStart, Date dayEnd) {
		return sysLogMapper.findTodayIp(dayStart,dayEnd);
	}
	//update-end--Author:zhangweijian Date:20190428 for：pass begin/end time params

	@Override
	public List<Map<String,Object>> findVisitCount(Date dayStart, Date dayEnd) {
		DbType dbType = CommonUtils.getDatabaseTypeEnum();
		return sysLogMapper.findVisitCount(dayStart, dayEnd,dbType.getDb());
	}
}
