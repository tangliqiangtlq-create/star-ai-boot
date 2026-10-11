package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import org.jeecg.modules.system.entity.SysUserRole;

/**
 * User-role table service @Author scott
 *
 * @since 2018-12-21
 */
public interface ISysUserRoleService extends IService<SysUserRole> {

  List<String> listUserRole(String username);

  void refreshListUserRoleCache(String username);
}
