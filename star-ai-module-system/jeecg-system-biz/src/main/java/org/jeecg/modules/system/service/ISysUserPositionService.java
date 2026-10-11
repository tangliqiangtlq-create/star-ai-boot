package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserPosition;
import com.baomidou.mybatisplus.extension.service.IService;
/**
 * @Description: User-position relation table
 * @Author: jeecg-boot
 * @Date:   2023-02-14
 * @Version: V1.0
 */
public interface ISysUserPositionService extends IService<SysUserPosition> {

    /**
     * Get users under position
     * @param page
     * @param positionId
     * @return
     */
    IPage<SysUser> getPositionUserList(Page<SysUser> page, String positionId);

    /**
     * Add members to user-position relation table
     * @param userIds
     * @param positionId
     */
    void saveUserPosition(String userIds, String positionId);

    /**
     * Delete user-position relations by position id
     * @param positionId
     */
    void removeByPositionId(String positionId);

    /**
     * Remove member
     * @param userIds
     * @param positionId
     */
    void removePositionUser(String userIds, String positionId);
}
