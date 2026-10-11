package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.system.entity.SysPosition;

import java.util.List;

/**
 * @Description: Position table
 * @Author: jeecg-boot
 * @Date: 2019-09-19
 * @Version: V1.0
 */
public interface ISysPositionService extends IService<SysPosition> {

    /**
     * Query by code
     * @param code position code
     * @return SysPosition
     */
    SysPosition getByCode(String code);

    /**
     * Get position names by user id
     * @param userId
     * @return
     */
    List<SysPosition> getPositionList(String userId);

    /**
     * Get position name
     * @param postList
     * @return
     */
    String getPositionName(List<String> postList);
}
