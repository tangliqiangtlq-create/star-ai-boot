package org.jeecg.modules.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.system.entity.SysDepart;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserDepart;
import org.jeecg.modules.system.mapper.SysUserDepartMapper;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.jeecg.modules.system.mapper.SysUserTenantMapper;
import org.jeecg.modules.system.model.DepartIdModel;
import org.jeecg.modules.system.service.ISysDepartService;
import org.jeecg.modules.system.service.ISysUserDepartService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.system.vo.SysUserDepVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * UserDepartment table implementation class
 *
 * <p>@Author ZhiLin
 *
 * @since 2019-02-22*/
@Service
public class SysUserDepartServiceImpl extends ServiceImpl<SysUserDepartMapper, SysUserDepart>
    implements ISysUserDepartService {
  @Autowired private ISysDepartService sysDepartService;
  @Lazy @Autowired private ISysUserService sysUserService;
  @Autowired private SysUserMapper sysUserMapper;
  @Autowired private SysUserTenantMapper userTenantMapper;

  /** ByUseridQueryDepartmentInfo */
  @Override
  public List<DepartIdModel> queryDepartIdsOfUser(String userId) {
    LambdaQueryWrapper<SysUserDepart> queryUserDep = new LambdaQueryWrapper<SysUserDepart>();
    LambdaQueryWrapper<SysDepart> queryDep = new LambdaQueryWrapper<SysDepart>();
    try {
      queryUserDep.eq(SysUserDepart::getUserId, userId);
      List<String> depIdList = new ArrayList<>();
      List<DepartIdModel> depIdModelList = new ArrayList<>();
      List<SysUserDepart> userDepList = this.list(queryUserDep);
      if (userDepList != null && userDepList.size() > 0) {
        for (SysUserDepart userDepart : userDepList) {
          depIdList.add(userDepart.getDepId());
        }

        // update-begin---author:wangshuai ---date:20230112
        //for: if tenant SAAS mode on, query by current tenant------------
        if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
          Integer tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
          queryDep.eq(SysDepart::getTenantId, tenantId);
        }
        // update-end---author:wangshuai ---date:20230112
        //for: if tenant SAAS mode on, query by current tenant------------

        queryDep.in(SysDepart::getId, depIdList);
        List<SysDepart> depList = sysDepartService.list(queryDep);
        // jeecg-boot/issues/3906
        if (depList != null && depList.size() > 0) {
          for (SysDepart depart : depList) {
            depIdModelList.add(new DepartIdModel().convertByUserDepart(depart));
          }
        }
        return depIdModelList;
      }
    } catch (Exception e) {
      e.fillInStackTrace();
    }
    return null;
  }

  /** Query users by department id */
  @Override
  public List<SysUser> queryUserByDepId(String depId) {
    LambdaQueryWrapper<SysUserDepart> queryUserDep = new LambdaQueryWrapper<SysUserDepart>();
    queryUserDep.eq(SysUserDepart::getDepId, depId);
    List<String> userIdList = new ArrayList<>();
    List<SysUserDepart> uDepList = this.list(queryUserDep);
    if (uDepList != null && uDepList.size() > 0) {
      for (SysUserDepart uDep : uDepList) {
        userIdList.add(uDep.getUserId());
      }
      List<SysUser> userList = (List<SysUser>) sysUserMapper.selectBatchIds(userIdList);
      // update-begin-author:taoyan date:201905047 for:API query must not return password fields
      for (SysUser sysUser : userList) {
        sysUser.setSalt("");
        sysUser.setPassword("");
      }
      // update-end-author:taoyan date:201905047 for:API query must not return password fields
      return userList;
    }
    return new ArrayList<SysUser>();
  }

  /** Query the user information of the current department and subordinate departments according to the department code*/
  @Override
  public List<SysUser> queryUserByDepCode(String depCode, String realname) {
    // update-begin-author:taoyan date:20210422 for: optimize select-users-by-dept API
    if (oConvertUtils.isNotEmpty(realname)) {
      realname = realname.trim();
    }
    List<SysUser> userList = this.baseMapper.queryDepartUserList(depCode, realname);
    Map<String, SysUser> map = new HashMap(5);
    for (SysUser sysUser : userList) {
      // The returned user data removes the password information
      sysUser.setSalt("");
      sysUser.setPassword("");
      map.put(sysUser.getId(), sysUser);
    }
    return new ArrayList<SysUser>(map.values());
    // update-end-author:taoyan date:20210422 for: optimize select-users-by-dept API

  }

  /**
   * @param departId
   * @param username
   * @param realname
   * @param pageSize
   * @param pageNo
   * @param id
   * @param isMultiTranslate whether multi-field translate
   * @return
   */
  @Override
  public IPage<SysUser> queryDepartUserPageList(
      String departId,
      String username,
      String realname,
      int pageSize,
      int pageNo,
      String id,
      String isMultiTranslate) {
    IPage<SysUser> pageList = null;
    // No department id; query user table directly
    Page<SysUser> page = new Page<SysUser>(pageNo, pageSize);
    if (oConvertUtils.isEmpty(departId)) {
      LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
      //update-begin---author:wangshuai ---date:20220104 for: [JTC-297]frozen user could still be set as agent------------
      query.eq(SysUser::getStatus, Integer.parseInt(CommonConstant.STATUS_1));
      //update-end---author:wangshuai ---date:20220104 for: [JTC-297]frozen user could still be set as agent------------
      //update-begin---author:liusq ---date:20231215 for: comma-separated multi-user translate issue------------
      if (oConvertUtils.isNotEmpty(username)) {
        String COMMA = ",";
        if (oConvertUtils.isNotEmpty(isMultiTranslate) && username.contains(COMMA)) {
          String[] usernameArr = username.split(COMMA);
          query.in(SysUser::getUsername, usernameArr);
        } else {
          query.like(SysUser::getUsername, username);
        }
      }
      //update-end---author:liusq ---date:20231215 for: comma-separated multi-user translate issue------------
      // update-begin---author:wangshuai ---date:20220608
      //for: [VUEN-1238]email reply shows user id as recipient------------
      if (oConvertUtils.isNotEmpty(id)) {
        query.eq(SysUser::getId, id);
      }
      // update-end---author:wangshuai ---date:20220608
      //for: [VUEN-1238]email reply shows user id as recipient------------
      //update-begin---author:wangshuai ---date:20220902 for: [VUEN-2121]temp users must not show directly------------
      query.ne(SysUser::getUsername, "_reserve_user_external");
      //update-end---author:wangshuai ---date:20220902 for: [VUEN-2121]temp users must not show directly------------

      // ------------------------------------------------------------------------------------------------
      // Whether to enable multi-tenant data isolation for the system module [SAAS multi-tenant mode]
      if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
        String tenantId = oConvertUtils.getString(TenantContext.getTenant(), "0");
        // update-begin---author:wangshuai ---date:20221223
        //for: [QQYUN-3371]tenant logic refactor to relation table------------
        List<String> userIdList = userTenantMapper.getUserIdsByTenantId(Integer.valueOf(tenantId));
        if (null != userIdList && userIdList.size() > 0) {
          query.in(SysUser::getId, userIdList);
        }
        //update-end---author:wangshuai ---date:20221223 for: [QQYUN-3371]tenant logic refactor to relation table------------
      }
      // ------------------------------------------------------------------------------------------------
      pageList = sysUserMapper.selectPage(page, query);
    } else {
      // Has department id; use custom SQL
      SysDepart sysDepart = sysDepartService.getById(departId);
      pageList =
          this.baseMapper.queryDepartUserPageList(page, sysDepart.getOrgCode(), username, realname);
    }
    List<SysUser> userList = pageList.getRecords();
    if (userList != null && userList.size() > 0) {
      List<String> userIds = userList.stream().map(SysUser::getId).collect(Collectors.toList());
      Map<String, SysUser> map = new HashMap(5);
      if (userIds != null && userIds.size() > 0) {
        // Check the department name
        Map<String, String> useDepNames = this.getDepNamesByUserIds(userIds);
        userList.forEach(
            item -> {
              // TODO Temporarily reuse this field for UI display
              item.setOrgCodeTxt(useDepNames.get(item.getId()));
              item.setSalt("");
              item.setPassword("");
              // Remove duplicates
              map.put(item.getId(), item);
            });
      }
      pageList.setRecords(new ArrayList<SysUser>(map.values()));
    }
    return pageList;
  }

  @Override
  public IPage<SysUser> getUserInformation(
      Integer tenantId, String departId, String keyword, Integer pageSize, Integer pageNo) {
    IPage<SysUser> pageList = null;
    // No department id; query user table directly
    Page<SysUser> page = new Page<>(pageNo, pageSize);
    LoginUser sysUser =  (LoginUser) StpUtil.getSession().get("loginUser");
    if (oConvertUtils.isEmpty(departId)) {
      LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
      query.eq(SysUser::getStatus, Integer.parseInt(CommonConstant.STATUS_1));
      query.ne(SysUser::getUsername, "_reserve_user_external");

      // Support tenant isolation
      if (tenantId != null) {
        List<String> userIds = userTenantMapper.getUserIdsByTenantId(tenantId);
        if (oConvertUtils.listIsNotEmpty(userIds)) {
          query.in(SysUser::getId, userIds);
        } else {
          query.eq(SysUser::getId, "User not found by tenant ID");
        }
      }

      // exclude yourself
      query.ne(SysUser::getId, sysUser.getId());
      if (StringUtils.isNotEmpty(keyword)) {
        // This syntax can wrap or in parentheses to avoid data not being found.
        query.and(
            (wrapper) ->
                wrapper
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getRealname, keyword));
      }
      pageList = sysUserMapper.selectPage(page, query);
    } else {
      // Has department id; use custom SQL
      SysDepart sysDepart = sysDepartService.getById(departId);
      //update-begin---author:wangshuai ---date:20220908 for: exclude self from departments------------
      pageList =
          this.baseMapper.getUserInformation(
              page, sysDepart.getOrgCode(), keyword, sysUser.getId());
      //update-end---author:wangshuai ---date:20220908 for: exclude self from departments--------------
    }
    return pageList;
  }

  @Override
  public IPage<SysUser> getUserInformation(
      Integer tenantId,
      String departId,
      String roleId,
      String keyword,
      Integer pageSize,
      Integer pageNo,
      String excludeUserIdList) {
    IPage<SysUser> pageList = null;
    // No department id; query user table directly
    Page<SysUser> page = new Page<>(pageNo, pageSize);
    LoginUser sysUser =  (LoginUser) StpUtil.getSession().get("loginUser");

    List<String> userIdList = new ArrayList<>();
    if (oConvertUtils.isNotEmpty(excludeUserIdList)) {
      userIdList = Arrays.asList(excludeUserIdList.split(SymbolConstant.COMMA));
    }
    if (oConvertUtils.isNotEmpty(departId)) {
      // Has department id; use custom SQL
      SysDepart sysDepart = sysDepartService.getById(departId);
      // update-begin-author:taoyan date:2023-1-3 for: user picker must filter by tenant id
      // update-begin---author:wangshuai---date:2024-02-02---for:【QQYUN-8239】user-role: add user
      // Return 2 pages of data but only show one page---
      // update-begin---author:wangshuai---date:2024-02-02---for:【QQYUN-8239】user-role: add user
      // Return 2 pages of data but only show one page---
      pageList =
          this.baseMapper.getProcessUserList(
              page, sysDepart.getOrgCode(), keyword, tenantId, userIdList);
      // update-end---author:wangshuai---date:2024-02-02---for:【QQYUN-8239】user-role: add user
      // Return 2 pages of data but only show one page---
    } else if (oConvertUtils.isNotEmpty(roleId)) {
      // update-begin---author:wangshuai---date:2024-02-02---for:【QQYUN-8239】user-role: add user
      // Return 2 pages of data but only show one page---
      pageList =
          this.sysUserMapper.selectUserListByRoleId(page, roleId, keyword, tenantId, userIdList);
      // update-end---author:wangshuai---date:2024-02-02---for:【QQYUN-8239】user-role: add user
      // Return 2 pages of data but only show one page---
      // update-end-author:taoyan date:2023-1-3 for: user picker must filter by tenant id
    } else {
      LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
      query.eq(SysUser::getStatus, Integer.parseInt(CommonConstant.STATUS_1));
      query.ne(SysUser::getUsername, "_reserve_user_external");
      // update-begin---author:wangshuai---date:2024-02-02---for:【QQYUN-8239】user-role: add user
      // Return 2 pages of data but only show one page---
      if (oConvertUtils.isNotEmpty(excludeUserIdList)) {
        query.notIn(SysUser::getId, Arrays.asList(excludeUserIdList.split(SymbolConstant.COMMA)));
      }
      // update-end---author:wangshuai---date:2024-02-02---for:【QQYUN-8239】user-role: add user
      // Return 2 pages of data but only show one page---
      // Support tenant isolation
      if (tenantId != null) {
        List<String> userIds = userTenantMapper.getUserIdsByTenantId(tenantId);
        if (oConvertUtils.listIsNotEmpty(userIds)) {
          query.in(SysUser::getId, userIds);
        } else {
          query.eq(SysUser::getId, "User not found by tenant ID");
        }
      }

      if (StringUtils.isNotEmpty(keyword)) {
        // This syntax can wrap or in parentheses to avoid data not being found.
        query.and(
            (wrapper) ->
                wrapper
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getRealname, keyword));
      }
      pageList = sysUserMapper.selectPage(page, query);
    }
    // Batch-query users' departments
    // step.1 Collect all user ids
    // step.2 Batch query department names by user ids
    List<String> userIds =
        pageList.getRecords().stream().map(SysUser::getId).collect(Collectors.toList());
    if (userIds.size() > 0) {
      Map<String, String> useDepNames = sysUserService.getDepNamesByUserIds(userIds);
      pageList.getRecords().forEach(item -> item.setOrgCodeTxt(useDepNames.get(item.getId())));
    }
    return pageList;
  }

  @Override
  public List<SysUser> getUsersByDepartTenantId(String departId, Integer tenantId) {
    return baseMapper.getUsersByDepartTenantId(departId, tenantId);
  }

  /**
 * Upgrade SpringBoot2.6.6, no circular dependencies are allowed
 *
 * @param userIds
 * @return*/
  private Map<String, String> getDepNamesByUserIds(List<String> userIds) {
    List<SysUserDepVo> list = sysUserMapper.getDepNamesByUserIds(userIds);

    Map<String, String> res = new HashMap(5);
    list.forEach(
        item -> {
          if (res.get(item.getUserId()) == null) {
            res.put(item.getUserId(), item.getDepartName());
          } else {
            res.put(item.getUserId(), res.get(item.getUserId()) + "," + item.getDepartName());
          }
        });
    return res;
  }
}
