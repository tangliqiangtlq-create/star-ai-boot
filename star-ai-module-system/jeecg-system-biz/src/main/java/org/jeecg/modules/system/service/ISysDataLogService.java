package org.jeecg.modules.system.service;

import org.jeecg.modules.system.entity.SysDataLog;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: DataLogserviceAPI
 * @author: jeecg-boot
 */
public interface ISysDataLogService extends IService<SysDataLog> {
	
	/**
	 * AddDataLog
	 * @param tableName
	 * @param dataId
	 * @param dataContent
	 */
	public void addDataLog(String tableName, String dataId, String dataContent);

}
