package org.jeecg.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.system.entity.SysPosition;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * @Description: Position table
 * @Author: jeecg-boot
 * @Date: 2019-09-19
 * @Version: V1.0
 */
public interface SysPositionMapper extends BaseMapper<SysPosition> {

    /**
     * Get position names by user id
     * @param userId
     * @return
     */
    List<SysPosition> getPositionList(@Param("userId") String userId);

    /**
     * Get position name by position id
     * @param postList
     * @return
     */
    List<SysPosition> getPositionName(@Param("postList") List<String> postList);

    /**
     * Get position id by position name
     * @param name
     * @return
     */
    @Select("SELECT id FROM sys_position WHERE name = #{name} AND tenant_id = #{tenantId} ORDER BY create_time DESC")
    List<String> getPositionIdByName(@Param("name") String name, @Param("tenantId") Integer tenantId, @Param("page") Page<SysPosition> page);
}
