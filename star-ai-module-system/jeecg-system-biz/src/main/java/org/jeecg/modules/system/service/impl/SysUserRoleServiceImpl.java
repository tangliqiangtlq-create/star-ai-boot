package org.jeecg.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.annotation.Resource;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserRole;
import org.jeecg.modules.system.mapper.SysUserRoleMapper;
import org.jeecg.modules.system.service.ISysRoleService;
import org.jeecg.modules.system.service.ISysUserRoleService;
import org.jeecg.modules.system.service.ISysUserService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * User-role table service implementation @Author scott
 *
 * @since 2018-12-21
 */
@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole>
    implements ISysUserRoleService {

  @Resource private ISysUserService sysUserService;

  @Resource private ISysRoleService sysRoleService;

  @Override
  @Cacheable(cacheNames = "dcs_SysUserRoleServiceImpl_listUserRole")
  public List<String> listUserRole(String username) {
    if (username == null || username.isBlank()) {
      return List.of();
    }
    // L27-4: must NOT use getUserByName (SELECT *) here. This method runs on the POST-login
    // path (CommonController.checkFileAccess -> role resolution; also StpInterfaceImpl). C6
    // (SensitiveTableBlacklistInterceptor.validateSysUserColumns) blocks SELECT * for logged-in
    // non-owner-admin sessions, so getUserByName threw here and left roleList=null. That made
    // isFileAdmin() a no-op and denied file admins access to files they do not own.
    // Use the same C6-safe projection as getEncodeUserInfo — only the id is needed and
    // credentials are never required for role resolution. This converges the SQL shape; it does
    // NOT weaken C6 (getUserByName keeps SELECT * for the anonymous /sys/login path).
    SysUser sysUser = sysUserService.getUserByNameForInfo(username);
    if (sysUser == null || sysUser.getId() == null || sysUser.getId().isBlank()) {
      return List.of();
    }
    String userId = sysUser.getId();

    List<SysUserRole> sysUserRoleList = list(new QueryWrapper<SysUserRole>().eq("user_id", userId));

    List<String> roleIds =
        sysUserRoleList.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

    List<SysRole> sysRoleList = sysRoleService.listByIds(roleIds);

    return sysRoleList.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
  }

  @Override
  @CacheEvict(cacheNames = "dcs_SysUserRoleServiceImpl_listUserRole")
  public void refreshListUserRoleCache(String username) {}
}
