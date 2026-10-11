package org.jeecg.modules.system.mapper;

import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.system.entity.SysDataLog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * @Description: SystemDataLogMapperAPI
 * @author: jeecg-boot
 */
public interface SysDataLogMapper extends BaseMapper<SysDataLog>{
	/**
	 * Get max version by table name and data id
	 * @param tableName
	 * @param dataId
	 * @return
	 */
	public String queryMaxDataVer(@Param("tableName") String tableName,@Param("dataId") String dataId);
	
}
