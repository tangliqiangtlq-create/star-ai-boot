package org.jeecg.modules.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.system.entity.SysPosition;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserPosition;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.jeecg.modules.system.mapper.SysUserPositionMapper;
import org.jeecg.modules.system.service.ISysUserPositionService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @Description: User position relationship table
 * @Author: jeecg-boot
 * @Date: 2023-02-14
 * @Version: V1.0
 */
@Service
public class SysUserPositionServiceImpl extends ServiceImpl<SysUserPositionMapper, SysUserPosition> implements ISysUserPositionService {

    @Autowired
    private SysUserPositionMapper sysUserPositionMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Override
    public IPage<SysUser> getPositionUserList(Page<SysUser> page, String positionId) {
        return page.setRecords(sysUserPositionMapper.getPositionUserList(page, positionId));
    }

    @Override
    public void saveUserPosition(String userIds, String positionId) {
        String[] userIdArray = userIds.split(SymbolConstant.COMMA);
        //Existing users
        StringBuilder userBuilder = new StringBuilder();
        for (String userId : userIdArray) {
            //Get whether the member exists in the position
            Long count = sysUserPositionMapper.getUserPositionCount(userId, positionId);
            if (count == 0) {
                //Insert into user position relationship table
                SysUserPosition userPosition = new SysUserPosition();
                userPosition.setPositionId(positionId);
                userPosition.setUserId(userId);
                sysUserPositionMapper.insert(userPosition);
            } else {
                userBuilder.append(userId).append(SymbolConstant.COMMA);
            }
        }
        //If the user id exists, it means that it already exists in the user position relationship table, prompting that the user already exists.
        String uIds = userBuilder.toString();
        if (oConvertUtils.isNotEmpty(uIds)) {
            //Query user list — P2 (CodePlan §4.3.4): realname is all the error message needs.
            List<SysUser> sysUsers = userMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
                    .select("id", "realname")
                    .in("id", Arrays.asList(uIds.split(SymbolConstant.COMMA))));
            String realnames = sysUsers.stream().map(SysUser::getRealname).collect(Collectors.joining(SymbolConstant.COMMA));
            throw new JeecgBootException(realnames + "Already exists in this position");
        }
    }

    @Override
    public void removeByPositionId(String positionId) {
        sysUserPositionMapper.removeByPositionId(positionId);
    }

    @Override
    public void removePositionUser(String userIds, String positionId) {
        String[] userIdArray = userIds.split(SymbolConstant.COMMA);
		sysUserPositionMapper.removePositionUser(Arrays.asList(userIdArray),positionId);
    }

}
