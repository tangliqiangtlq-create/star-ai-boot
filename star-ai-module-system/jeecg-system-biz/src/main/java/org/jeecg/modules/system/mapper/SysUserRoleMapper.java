package org.jeecg.modules.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.system.entity.SysUserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

  @Select("SELECT sr.role_code "
      + " FROM sys_user su "
      + " INNER JOIN sys_user_role sur ON sur.user_id = su.id "
      + " INNER JOIN sys_role sr       ON sr.id       = sur.role_id "
      + " WHERE su.username = #{username}")
  List<String> getRoleByUserName(@Param("username") String username);

  @Select("SELECT sr.role_code "
      + " FROM sys_user_role sur "
      + " INNER JOIN sys_role sr ON sr.id = sur.role_id "
      + " WHERE sur.user_id = #{userId}")
  List<String> getRoleCodeByUserId(@Param("userId") String userId);

  @Select("SELECT sr.id "
      + " FROM sys_user su "
      + " INNER JOIN sys_user_role sur ON sur.user_id = su.id "
      + " INNER JOIN sys_role sr       ON sr.id       = sur.role_id "
      + " WHERE su.username = #{username}")
  List<String> getRoleIdByUserName(@Param("username") String username);

  @Select("SELECT sr.id "
      + " FROM sys_user_role sur "
      + " INNER JOIN sys_role sr ON sr.id = sur.role_id "
      + " WHERE sur.user_id = #{userId}")
  List<String> getRoleIdByUserId(@Param("userId") String userId);
}
