package org.jeecg.modules.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.jeecg.common.api.dto.message.BusMessageDTO;
import org.jeecg.common.api.dto.message.MessageDTO;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.constant.enums.SysAnnmentTypeEnum;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.DateUtils;
import org.jeecg.common.util.PasswordUtil;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.aop.TenantLog;
import org.jeecg.modules.system.entity.*;
import org.jeecg.modules.system.mapper.*;
import org.jeecg.modules.system.service.ISysTenantPackService;
import org.jeecg.modules.system.service.ISysTenantService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.system.vo.tenant.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

/**
 * @Description: Tenant service implementation
 *
 * @author: jeecg-boot
 */
@Service("sysTenantServiceImpl")
@Slf4j
public class SysTenantServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant>
    implements ISysTenantService {

  @Autowired ISysUserService userService;
  @Autowired private SysUserTenantMapper userTenantMapper;
  @Autowired private SysTenantMapper tenantMapper;

  @Autowired private ISysTenantPackService sysTenantPackService;

  @Autowired private SysTenantPackUserMapper sysTenantPackUserMapper;

  @Autowired private ISysBaseAPI sysBaseApi;

  @Autowired private SysUserDepartMapper sysUserDepartMapper;

  @Autowired private SysTenantPackMapper sysTenantPackMapper;

  @Autowired private SysPackPermissionMapper sysPackPermissionMapper;

  @Override
  public List<SysTenant> queryEffectiveTenant(Collection<Integer> idList) {
    if (oConvertUtils.listIsEmpty(idList)) {
      return null;
    }

    LambdaQueryWrapper<SysTenant> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.in(SysTenant::getId, idList);
    queryWrapper.eq(SysTenant::getStatus, Integer.valueOf(CommonConstant.STATUS_1));
    // This query ignores time conditions
    return super.list(queryWrapper);
  }

  @Override
  public Long countUserLinkTenant(String id) {
    LambdaQueryWrapper<SysUserTenant> query = new LambdaQueryWrapper<>();
    query.eq(SysUserTenant::getTenantId, id);
    query.eq(SysUserTenant::getStatus, CommonConstant.STATUS_1);
    // Count already-linked users
    return userTenantMapper.selectCount(query);
  }

  @Override
  public boolean removeTenantById(String id) {
    // Count already-linked users
    return super.removeById(Integer.parseInt(id));
  }

  @Override
  @CacheEvict(
      value = {CacheConstant.SYS_USERS_CACHE},
      allEntries = true)
  public void invitationUserJoin(String ids, String phone) {
    String[] idArray = ids.split(SymbolConstant.COMMA);
    // update-begin---author:wangshuai ---date:20230313
    //for: [QQYUN-4605]invite-to-tenant could not pick non-tenant users; invite by phone------------
    SysUser userByPhone = userService.getUserByPhone(phone);
    // Means user does not exist
    if (null == userByPhone) {
      throw new JeecgBootException("Current user does not exist, please verify mobile number");
    }
    String userId = userByPhone.getId();
    // Iterate tenant ids
    for (String id : idArray) {
      //update-begin---author:wangshuai ---date:20221223 for: [QQYUN-3371]tenant logic refactor to relation table------------
      // Check whether invitee already exists
      SysUserTenant userTenant =
          userTenantMapper.getUserTenantByTenantId(userId, Integer.valueOf(id));
      if (null == userTenant) {
        SysUserTenant relation = new SysUserTenant();
        relation.setUserId(userId);
        relation.setTenantId(Integer.valueOf(id));
        relation.setStatus(CommonConstant.USER_TENANT_NORMAL);
        userTenantMapper.insert(relation);
      } else {
        // update-begin---author:wangshuai ---date:20230711
        //for: [QQYUN-5723]2, user already in tenant should say exists, not success------------
        //update-begin---author:wangshuai ---date:20230724 for: [QQYUN-5885]invite-user message inaccurate------------
        String tenantErrorInfo = getTenantErrorInfo(userTenant.getStatus());
        String errMsg =
            "Mobile number user:"
                + userByPhone.getPhone()
                + " Nickname: "
                + userByPhone.getRealname()
                + "，"
                + tenantErrorInfo;
        //update-end---author:wangshuai ---date:20230724 for: [QQYUN-5885]invite-user message inaccurate------------
        throw new JeecgBootException(errMsg);
        // update-end---author:wangshuai ---date:20230711
        //for: [QQYUN-5723]2, user already in tenant should say exists, not success------------
      }
      //update-end---author:wangshuai ---date:20221223 for: [QQYUN-3371]tenant logic refactor to relation table------------
      // update-end---author:wangshuai ---date:20230313
      //for: [QQYUN-4605]invite-to-tenant could not pick non-tenant users; invite by phone------------
    }
  }

  @Override
  @CacheEvict(
      value = {CacheConstant.SYS_USERS_CACHE},
      allEntries = true)
  public void leaveTenant(String userIds, String tenantId) {
    String[] userIdArray = userIds.split(SymbolConstant.COMMA);
    for (String userId : userIdArray) {
      //update-begin---author:wangshuai ---date:20221223 for: [QQYUN-3371]tenant logic refactor to relation table------------
      LambdaQueryWrapper<SysUserTenant> query = new LambdaQueryWrapper<>();
      query.eq(SysUserTenant::getTenantId, tenantId);
      query.eq(SysUserTenant::getUserId, userId);
      userTenantMapper.delete(query);
      //update-end---author:wangshuai ---date:20221223 for: [QQYUN-3371]tenant logic refactor to relation table------------
    }
    // Tenant removes user: delete user-tenant product packs
    sysTenantPackUserMapper.deletePackUserByTenantId(
        Integer.valueOf(tenantId), Arrays.asList(userIds.split(SymbolConstant.COMMA)));
  }

  @Override
  @CacheEvict(
      value = {CacheConstant.SYS_USERS_CACHE},
      allEntries = true)
  public Integer saveTenantJoinUser(SysTenant sysTenant, String userId) {
    // AddTenant
    this.saveTenant(sysTenant);

    // Add tenant product pack
    Integer tenantId = sysTenant.getId();
    sysTenantPackService.addDefaultTenantPack(tenantId);

    // Add tenant to relation table
    return tenantId;
  }

  @Override
  public void saveTenant(SysTenant sysTenant) {
    // Get tenant id
    sysTenant.setId(this.tenantIdGenerate());
    sysTenant.setHouseNumber(RandomUtil.randomStringUpper(6));
    sysTenant.setDelFlag(CommonConstant.DEL_FLAG_0);
    this.save(sysTenant);
    // update-begin---author:wangshuai ---date:20230710
    //for: [QQYUN-5723]1, add creator into tenant relation------------
    // Current logged-in user id
    LoginUser loginUser =  (LoginUser) StpUtil.getSession().get("loginUser");
    this.saveTenantRelation(sysTenant.getId(), loginUser.getId());
    //update-end---author:wangshuai ---date:20230710 for: [QQYUN-5723]1, add creator into tenant relation------------
  }

  @Override
  @CacheEvict(
      value = {CacheConstant.SYS_USERS_CACHE},
      allEntries = true)
  public Integer joinTenantByHouseNumber(SysTenant sysTenant, String userId) {
    LambdaQueryWrapper<SysTenant> query = new LambdaQueryWrapper<>();
    query.eq(SysTenant::getHouseNumber, sysTenant.getHouseNumber());
    SysTenant one = this.getOne(query);
    // Return tenant id for front-end cache update; 0 means house-number not found
    if (null == one) {
      return 0;
    } else {
      LambdaQueryWrapper<SysUserTenant> relationQuery = new LambdaQueryWrapper<>();
      relationQuery.eq(SysUserTenant::getTenantId, one.getId());
      relationQuery.eq(SysUserTenant::getUserId, userId);
      SysUserTenant relation = userTenantMapper.selectOne(relationQuery);
      if (relation != null) {
        String msg = "";
        if (CommonConstant.USER_TENANT_UNDER_REVIEW.equals(relation.getStatus())) {
          msg = ",status: under review";
        } else if (CommonConstant.USER_TENANT_REFUSE.equals(relation.getStatus())) {
          throw new JeecgBootBizTipException("Admin rejected your request to join tenant, please contact tenant admin");
        } else if (CommonConstant.USER_TENANT_QUIT.equals(relation.getStatus())) {
          msg = ",status: resigned";
        }
        throw new JeecgBootBizTipException("You are already a member of this tenant" + msg);
      }
      // User join house-number pending-review status
      SysUserTenant tenant = new SysUserTenant();
      tenant.setTenantId(one.getId());
      tenant.setUserId(userId);
      tenant.setStatus(CommonConstant.USER_TENANT_UNDER_REVIEW);
      userTenantMapper.insert(tenant);

      // QQYUN-4526 [app] organization join notice
      sendMsgForApplyJoinTenant(userId, one);
      return tenant.getTenantId();
    }
  }

  @Override
  public Integer countCreateTenantNum(String userId) {
    return this.userTenantMapper.countCreateTenantNum(userId);
  }

  @Override
  public IPage<SysTenant> getRecycleBinPageList(Page<SysTenant> page, SysTenant sysTenant) {
    return page.setRecords(tenantMapper.getRecycleBinPageList(page, sysTenant));
  }

  @Override
  @CacheEvict(
      value = {CacheConstant.SYS_USERS_CACHE},
      allEntries = true)
  public void deleteTenantLogic(String ids) {
    String[] idArray = ids.split(SymbolConstant.COMMA);
    List<Integer> list = new ArrayList<>();
    // Cast to int
    for (String id : idArray) {
      list.add(Integer.valueOf(id));
    }
    // DeleteTenant
    tenantMapper.deleteByTenantId(list);
    // update-begin---author:wangshuai ---date:20230710
    //for: [QQYUN-5723]3, hard-delete tenant must also remove user-tenant relations------------
    // Delete users under tenant
    userTenantMapper.deleteUserByTenantId(list);
    // update-ennd---author:wangshuai ---date:20230710
    //for: [QQYUN-5723]3, hard-delete tenant must also remove user-tenant relations------------

    // update-begin---author:wangshuai ---date:20230710
    //for: [QQYUN-5723]3, hard-delete tenant must also remove user-tenant relations------------
    // Delete product packs under tenant
    this.deleteTenantPackByTenantId(list);
    // update-ennd---author:wangshuai ---date:20230710
    //for: [QQYUN-5723]3, hard-delete tenant must also remove user-tenant relations------------
  }

  @Override
  public void revertTenantLogic(String ids) {
    String[] idArray = ids.split(SymbolConstant.COMMA);
    List<Integer> list = new ArrayList<>();
    // Cast to int
    for (String id : idArray) {
      list.add(Integer.valueOf(id));
    }
    // Restore tenant
    tenantMapper.revertTenantLogic(list);
  }

  /**
   * Add tenant to relation table
   *
   * @param tenantId
   * @param userId
   */
  @CacheEvict(
      value = {CacheConstant.SYS_USERS_CACHE},
      allEntries = true)
  public Integer saveTenantRelation(Integer tenantId, String userId) {
    SysUserTenant relation = new SysUserTenant();
    relation.setTenantId(tenantId);
    relation.setUserId(userId);
    relation.setStatus(CommonConstant.USER_TENANT_NORMAL);
    userTenantMapper.insert(relation);
    return relation.getTenantId();
  }

  /**
   * Get tenant id
   *
   * @return
   */
  public int tenantIdGenerate() {
    synchronized (this) {
      // Get max id
      // update-begin---author:wangshuai ---date:20230424
      //for: no tenant rows causes SQL return-type mismatch when empty------------
      int maxTenantId = oConvertUtils.getInt(tenantMapper.getMaxTenantId(), 0);
      // update-end---author:wangshuai ---date:20230424
      //for: no tenant rows causes SQL return-type mismatch when empty------------
      if (maxTenantId >= 1000) {
        return maxTenantId + 1;
      } else {
        return 1000;
      }
    }
  }

  @Override
  public void exitUserTenant(String userId, String username, String tenantId) {
    int tId = Integer.parseInt(tenantId);
    // Get all tenant info
    List<String> userIdsByTenantId = userTenantMapper.getUserIdsByTenantId(tId);
    // Check whether current user is tenant owner
    SysTenant sysTenant = tenantMapper.selectById(tId);
    // If is owner
    if (username.equals(sysTenant.getCreateBy())) {
      // Check current tenant info digit length
      if (null != userIdsByTenantId && userIdsByTenantId.size() > 1) {
        // Need to assign owner
        throw new JeecgBootException("assignedOwen");
      } else if (null != userIdsByTenantId && userIdsByTenantId.size() == 1) {
        // update-begin---author:wangshuai ---date:20230426
        //for: [QQYUN-5270]after leaving all tenants, re-login says all tenants frozen------------
        // Only owner needs to unregister the tenant
        throw new JeecgBootException("cancelTenant");
        // update-end---author:wangshuai ---date:20230426
        //for: [QQYUN-5270]after leaving all tenants, re-login says all tenants frozen------------
      } else {
        throw new JeecgBootException("Failed to exit tenant; tenant info no longer exists");
      }
    } else {
      // If not owner, delete directly
      this.leaveTenant(userId, tenantId);
      this.leveUserProcess(userId, tenantId);
    }
  }

  @Override
  public void changeOwenUserTenant(String userId, String tId) {
    // Check whether current user exists under tenant
    //update-begin---author:wangshuai ---date:20230705 for: tenant id must be passed in, not current tenant------------
    int tenantId = oConvertUtils.getInt(tId, 0);
    SysTenant sysTenant = tenantMapper.selectById(tenantId);
    if (null == sysTenant) {
      throw new JeecgBootException("Failed to exit tenant; tenant does not exist");
    }
    String createBy = sysTenant.getCreateBy();
    //update-end---author:wangshuai ---date:20230705 for: tenant id must be passed in, not current tenant------------
    Integer count = userTenantMapper.userTenantIzExist(userId, tenantId);
    if (count == 0) {
      throw new JeecgBootException("Failed to exit tenant; user not found under this tenant");
    }
    // GetUserInfo
    SysUser user = userService.getById(userId);
    // Change owner
    SysTenant tenant = new SysTenant();
    tenant.setCreateBy(user.getUsername());
    tenant.setId(tenantId);
    tenantMapper.updateById(tenant);
    // Delete current login user's tenant info
    //update-begin---author:wangshuai ---date:20230705 for: after old owner exits, mark old owner user-tenant as resigned------------
    // Get original creator user id
    SysUser userByName = userService.getUserByName(createBy);
    LambdaQueryWrapper<SysUserTenant> query = new LambdaQueryWrapper<>();
    query.eq(SysUserTenant::getUserId, userByName.getId());
    query.eq(SysUserTenant::getTenantId, tenantId);
    SysUserTenant userTenant = new SysUserTenant();
    userTenant.setStatus(CommonConstant.USER_TENANT_QUIT);
    userTenantMapper.update(userTenant, query);
    //update-end---author:wangshuai ---date:20230705 for: after old owner exits, mark old owner user-tenant as resigned------------
    // Resignation flow
    this.leveUserProcess(userId, String.valueOf(tenantId));
  }

  /**
   * Trigger resignation flow
   *
   * @param userId
   * @param tenantId
   * @param tenantId
   */
  private void leveUserProcess(String userId, String tenantId) {
    LoginUser userInfo = new LoginUser();
    SysUser user = userService.getById(userId);
  }

  @Override
  public Result<String> invitationUser(String phone, String departId) {
    Result<String> result = new Result<>();
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");

    //1, query user and check existence
    SysUser userByPhone = userService.getUserByPhone(phone);
    if (null == userByPhone) {
      result.setSuccess(false);
      result.setMessage(I18nUtil.getMessage("common.user_not_exist"));
      return result;
    }
    int tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);

    // 2.check whether inviter exists in tenant list
    Integer userCount = userTenantMapper.userTenantIzExist(sysUser.getId(), tenantId);
    if (userCount == 0) {
      result.setSuccess(false);
      result.setMessage(I18nUtil.getMessage("sysTenant.noInvitePermission"));
      return result;
    }

    // 3.insert tenant info; skip existing
    // Check whether invitee already exists
    SysUserTenant sysUserTenant =
        userTenantMapper.getUserTenantByTenantId(userByPhone.getId(), tenantId);
    // Useralready exists
    if (null != sysUserTenant) {
      result.setSuccess(false);
      String tenantErrorInfo = getTenantErrorInfo(sysUserTenant.getStatus());
      String msg =
          "Mobile number user:"
              + userByPhone.getPhone()
              + " Nickname: "
              + userByPhone.getRealname()
              + "，"
              + tenantErrorInfo;
      result.setMessage(msg);
      return result;
    }

    // 4.requires user manual join approval
    String status = CommonConstant.USER_TENANT_INVITE;

    // 5.if user not in tenant, add to tenant
    SysUserTenant tenant = new SysUserTenant();
    tenant.setTenantId(tenantId);
    tenant.setUserId(userByPhone.getId());
    tenant.setStatus(status);
    userTenantMapper.insert(tenant);
    result.setSuccess(true);
    result.setMessage(I18nUtil.getMessage("sysTenant.inviteMemberPendingApproval"));

    // update-begin---author:wangshuai ---date:20230329
    //for: [QQYUN-4671]dept/user phone invite not limited to current dept; currently org-wide------------
    // 6.save user-department relations
    if (oConvertUtils.isNotEmpty(departId)) {
      // save user-department relations
      this.saveUserDepart(userByPhone.getId(), departId);
    }
    // update-end---author:wangshuai ---date:20230329
    //for: [QQYUN-4671]dept/user phone invite not limited to current dept; currently org-wide------------

    //  QQYUN-4527 [app] invite members to org; send reminder
    sendMsgForInvitation(userByPhone, tenantId, sysUser.getRealname());
    return result;
  }

  @Override
  public TenantDepartAuthInfo getTenantDepartAuthInfo(Integer tenantId) {
    SysTenant sysTenant = this.getById(tenantId);
    if (sysTenant == null) {
      return null;
    }

    TenantDepartAuthInfo info = new TenantDepartAuthInfo();
    LoginUser sysUser =  (LoginUser) StpUtil.getSession().get("loginUser");
    String userId = sysUser.getId();
    boolean superAdmin = false;
    // QuerypackTable
    List<String> packCodeList = baseMapper.queryUserPackCode(tenantId, userId);
    if (packCodeList == null || packCodeList.size() == 0) {
      // If no data, check whether tenant creator is current user
      if (sysTenant.getCreateBy().equals(sysUser.getUsername())) {
        sysTenantPackService.addDefaultTenantPack(tenantId);
        superAdmin = true;
      } else {
        superAdmin = false;
      }
    }
    if (superAdmin == false) {
      List<TenantPackUserCount> packCountList = baseMapper.queryTenantPackUserCount(tenantId);
      info.setPackCountList(packCountList);
    }
    info.setSysTenant(sysTenant);
    info.setSuperAdmin(superAdmin);
    info.setPackCodes(packCodeList);
    return info;
  }

  @Override
  public List<TenantPackUserCount> queryTenantPackUserCount(Integer tenantId) {
    return baseMapper.queryTenantPackUserCount(tenantId);
  }

  @Override
  public TenantPackModel queryTenantPack(TenantPackModel model) {
    Integer tenantId = model.getTenantId();
    String packCode = model.getPackCode();

    SysTenantPack sysTenantPack = sysTenantPackService.getSysTenantPack(tenantId, packCode);
    if (sysTenantPack != null) {
      TenantPackModel tenantPackModel = new TenantPackModel();
      tenantPackModel.setPackName(sysTenantPack.getPackName());
      tenantPackModel.setPackId(sysTenantPack.getId());
      // Query user
      List<TenantPackUser> userList = getTenantPackUserList(tenantId, sysTenantPack.getId(), 1);
      tenantPackModel.setUserList(userList);
      return tenantPackModel;
    }
    return null;
  }

  @Override
  public void addBatchTenantPackUser(SysTenantPackUser sysTenantPackUser) {
    String userIds = sysTenantPackUser.getUserId();
    if (oConvertUtils.isNotEmpty(userIds)) {
      ISysTenantService currentService =
          SpringContextUtils.getApplicationContext().getBean(ISysTenantService.class);
      String realNames = sysTenantPackUser.getRealname();
      String[] userIdArray = userIds.split(",");
      String[] realNameArray = realNames.split(",");
      for (int i = 0; i < userIdArray.length; i++) {
        String userId = userIdArray[i];
        String realName = realNameArray[i];
        SysTenantPackUser entity = new SysTenantPackUser(sysTenantPackUser, userId, realName);
        LambdaQueryWrapper<SysTenantPackUser> query =
            new LambdaQueryWrapper<SysTenantPackUser>()
                .eq(SysTenantPackUser::getTenantId, entity.getTenantId())
                .eq(SysTenantPackUser::getPackId, entity.getPackId())
                .eq(SysTenantPackUser::getUserId, entity.getUserId());
        SysTenantPackUser packUser = sysTenantPackUserMapper.selectOne(query);
        if (packUser == null || packUser.getId() == null) {
          currentService.addTenantPackUser(entity);
        } else {
          if (packUser.getStatus() == 0) {
            packUser.setPackName(entity.getPackName());
            packUser.setRealname(realName);
            currentService.addTenantPackUser(packUser);
          }
        }
      }
    }
  }

  @TenantLog(2)
  @Override
  public void addTenantPackUser(SysTenantPackUser sysTenantPackUser) {
    if (sysTenantPackUser.getId() == null) {
      sysTenantPackUserMapper.insert(sysTenantPackUser);
    } else {
      sysTenantPackUser.setStatus(1);
      sysTenantPackUserMapper.updateById(sysTenantPackUser);
    }
  }

  @TenantLog(4)
  @Override
  public void deleteTenantPackUser(SysTenantPackUser sysTenantPackUser) {
    LambdaQueryWrapper<SysTenantPackUser> query =
        new LambdaQueryWrapper<SysTenantPackUser>()
            .eq(SysTenantPackUser::getUserId, sysTenantPackUser.getUserId())
            .eq(SysTenantPackUser::getPackId, sysTenantPackUser.getPackId());
    sysTenantPackUserMapper.delete(query);

    // QQYUN-4525 [org mgmt] admin 2. when admin permission removed, notify the user
    sendMsgForDelete(sysTenantPackUser);
  }

  @Override
  public List<TenantPackUser> getTenantPackApplyUsers(Integer tenantId) {
    return getTenantPackUserList(tenantId, null, 0);
  }

  /**
   * Get users of a tenant product pack or pack applicants
   *
   * @param tenantId
   * @param packId
   * @param packUserStatus
   * @return
   */
  private List<TenantPackUser> getTenantPackUserList(
      Integer tenantId, String packId, Integer packUserStatus) {
    // Query user
    List<TenantPackUser> userList = baseMapper.queryPackUserList(tenantId, packId, packUserStatus);
    if (userList != null && userList.size() > 0) {
      List<String> userIdList = userList.stream().map(i -> i.getId()).collect(Collectors.toList());
      // Department
      List<UserDepart> depList = baseMapper.queryUserDepartList(userIdList);
      // Position TODO
      // Iterate users and attach department/position info
      for (TenantPackUser user : userList) {
        for (UserDepart dep : depList) {
          if (user.getId().equals(dep.getUserId())) {
            user.addDepart(dep.getDepartName());
          }
        }
      }
    }
    return userList;
  }

  @Override
  public void doApplyTenantPackUser(SysTenantPackUser sysTenantPackUser) {
    LambdaQueryWrapper<SysTenantPack> query1 =
        new LambdaQueryWrapper<SysTenantPack>()
            .eq(SysTenantPack::getTenantId, sysTenantPackUser.getTenantId())
            .eq(SysTenantPack::getPackCode, sysTenantPackUser.getPackCode());
    SysTenantPack pack = sysTenantPackService.getOne(query1);
    if (pack != null) {
      sysTenantPackUser.setStatus(0);
      sysTenantPackUser.setPackId(pack.getId());
      LambdaQueryWrapper<SysTenantPackUser> query =
          new LambdaQueryWrapper<SysTenantPackUser>()
              .eq(SysTenantPackUser::getTenantId, sysTenantPackUser.getTenantId())
              .eq(SysTenantPackUser::getPackId, sysTenantPackUser.getPackId())
              .eq(SysTenantPackUser::getUserId, sysTenantPackUser.getUserId());
      Long count = sysTenantPackUserMapper.selectCount(query);
      if (count == null || count == 0) {
        sysTenantPackUserMapper.insert(sysTenantPackUser);
      }
      // QQYUN-4524 [org link] admin 1. permission request -> notify related admins
      sendMsgForApply(sysTenantPackUser.getUserId(), pack);
    }
  }

  /**
   * Admin-permission application: send message
   *
   * @param userId
   * @param pack
   */
  private void sendMsgForApply(String userId, SysTenantPack pack) {
    // Send message
    SysUser user = userService.getById(userId);
    Integer tenantId = pack.getTenantId();
    SysTenant sysTenant = this.baseMapper.querySysTenant(tenantId);
    String packCode = pack.getPackCode();

    List<String> packCodeList = Arrays.asList(packCode.split(","));
    List<String> userList =
        sysTenantPackUserMapper.queryTenantPackUserNameList(tenantId, packCodeList);
    MessageDTO messageDTO = new MessageDTO();
    messageDTO.setToAll(false);
    messageDTO.setToUser(String.join(",", userList));
    messageDTO.setFromUser("system");
    String title =
        user.getRealname() + " Apply to join " + sysTenant.getName() + " 's " + pack.getPackName() + " members.";
    messageDTO.setTitle(title);
    Map<String, Object> data = new HashMap<>();
    messageDTO.setData(data);
    messageDTO.setContent(title);
    messageDTO.setType("system");
    sysBaseApi.sendTemplateMessage(messageDTO);
  }

  /**
   * Remove admin permission: send message
   *
   * @param sysTenantPackUser
   */
  private void sendMsgForDelete(SysTenantPackUser sysTenantPackUser) {
    // Send message
    SysUser user = userService.getById(sysTenantPackUser.getUserId());
    SysTenant sysTenant = this.baseMapper.querySysTenant(sysTenantPackUser.getTenantId());
    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
    MessageDTO messageDTO = new MessageDTO();
    messageDTO.setToAll(false);
    messageDTO.setToUser(user.getUsername());
    String title =
        "You have been "
            + loginUser.getRealname()
            + " from "
            + sysTenant.getName()
            + "'s "
            + sysTenantPackUser.getPackName()
            + " removed.";
    messageDTO.setTitle(title);
    messageDTO.setFromUser("system");
    Map<String, Object> data = new HashMap<>();
    data.put("realname", loginUser.getRealname());
    data.put("tenantName", sysTenant.getName());
    data.put("packName", sysTenantPackUser.getPackName());
    messageDTO.setData(data);
    messageDTO.setType("system");
    messageDTO.setContent(title);
    sysBaseApi.sendTemplateMessage(messageDTO);
  }

  /**
   * Org-join application: send message
   *
   * @param userId
   * @param sysTenant
   */
  private void sendMsgForApplyJoinTenant(String userId, SysTenant sysTenant) {
    // Send message
    SysUser user = userService.getById(userId);
    // Send message to super admin and org admins
    String codes = "superAdmin,accountAdmin";
    List<String> packCodeList = Arrays.asList(codes.split(","));
    List<String> userList =
        sysTenantPackUserMapper.queryTenantPackUserNameList(sysTenant.getId(), packCodeList);
    MessageDTO messageDTO = new MessageDTO();
    messageDTO.setToAll(false);
    messageDTO.setToUser(String.join(",", userList));
    messageDTO.setFromUser("system");
    String title = user.getRealname() + " Apply to join " + sysTenant.getName() + "。";
    messageDTO.setTitle(title);
    Map<String, Object> data = new HashMap<>();
    messageDTO.setData(data);
    messageDTO.setType("system");
    messageDTO.setContent(title);
    sysBaseApi.sendTemplateMessage(messageDTO);
  }

  /**
   * Invite members: send message
   *
   * @param user
   * @param tenantId
   * @param realname
   */
  private void sendMsgForInvitation(SysUser user, Integer tenantId, String realname) {
    // Send message
    SysTenant sysTenant = this.baseMapper.querySysTenant(tenantId);
    BusMessageDTO messageDTO = new BusMessageDTO();
    messageDTO.setToAll(false);
    messageDTO.setToUser(user.getUsername());
    messageDTO.setFromUser("system");
    // update-begin---author:wangshuai ---date:20230706
    //for: [QQYUN-5730]tenant invite notice should show inviter name------------
    String title = realname + " invites you to join " + sysTenant.getName() + "。";
    // update-end---author:wangshuai ---date:20230706
    //for: [QQYUN-5730]tenant invite notice should show inviter name------------
    messageDTO.setTitle(title);
    Map<String, Object> data = new HashMap<>();
    messageDTO.setData(data);
    messageDTO.setContent(title);
    messageDTO.setType("system");
    // update-begin---author:wangshuai---date:2023-11-24---for:【QQYUN-7168】invite member errors even though invite succeeded---
    messageDTO.setCategory(CommonConstant.MSG_CATEGORY_1);
    // update-end---author:wangshuai---date:2023-11-24---for:【QQYUN-7168】invite member errors even though invite succeeded---
    // update-begin---author:wangshuai ---date:20230721
    //for: [QQYUN-5726]add button to jump when inviting to tenant------------
    messageDTO.setBusType(SysAnnmentTypeEnum.TENANT_INVITE.getType());
    sysBaseApi.sendBusAnnouncement(messageDTO);
    //update-end---author:wangshuai ---date:20230721 for: [QQYUN-5726]add button to jump when inviting to tenant------------
  }

  @Override
  public void passApply(SysTenantPackUser sysTenantPackUser) {
    LambdaQueryWrapper<SysTenantPackUser> query =
        new LambdaQueryWrapper<SysTenantPackUser>()
            .eq(SysTenantPackUser::getTenantId, sysTenantPackUser.getTenantId())
            .eq(SysTenantPackUser::getPackId, sysTenantPackUser.getPackId())
            .eq(SysTenantPackUser::getUserId, sysTenantPackUser.getUserId());
    SysTenantPackUser packUser = sysTenantPackUserMapper.selectOne(query);
    if (packUser != null && packUser.getId() != null && packUser.getStatus() == 0) {
      ISysTenantService currentService =
          SpringContextUtils.getApplicationContext().getBean(ISysTenantService.class);
      packUser.setPackName(sysTenantPackUser.getPackName());
      packUser.setRealname(sysTenantPackUser.getRealname());
      currentService.addTenantPackUser(packUser);

      // update-begin---author:wangshuai ---date:20230328
      //for: [QQYUN-4674]tenant admin approve/reject lacked system notice------------
      // Super-admin join approved: send system message
      SysTenant sysTenant = tenantMapper.selectById(sysTenantPackUser.getTenantId());
      String content = " You have successfully joined" + sysTenant.getName() + " super admin members.";
      SysUser sysUser = userService.getById(sysTenantPackUser.getUserId());
      this.sendMsgForAgreeAndRefuseJoin(sysUser, content);
      // update-end---author:wangshuai ---date:20230328
      //for: [QQYUN-4674]tenant admin approve/reject lacked system notice------------
    }
  }

  @Override
  public void deleteApply(SysTenantPackUser sysTenantPackUser) {
    LambdaQueryWrapper<SysTenantPackUser> query =
        new LambdaQueryWrapper<SysTenantPackUser>()
            .eq(SysTenantPackUser::getTenantId, sysTenantPackUser.getTenantId())
            .eq(SysTenantPackUser::getPackId, sysTenantPackUser.getPackId())
            .eq(SysTenantPackUser::getUserId, sysTenantPackUser.getUserId());
    SysTenantPackUser packUser = sysTenantPackUserMapper.selectOne(query);
    if (packUser != null && packUser.getId() != null && packUser.getStatus() == 0) {
      sysTenantPackUserMapper.deleteById(packUser.getId());
      // update-begin---author:wangshuai ---date:20230328
      //for: [QQYUN-4674]tenant admin approve/reject lacked system notice------------
      // Super-admin join rejected: send system message
      SysTenant sysTenant = tenantMapper.selectById(sysTenantPackUser.getTenantId());
      String content = " Administrator has rejected your request to join" + sysTenant.getName() + " super admin member request.";
      SysUser sysUser = userService.getById(sysTenantPackUser.getUserId());
      this.sendMsgForAgreeAndRefuseJoin(sysUser, content);
      // update-end---author:wangshuai ---date:20230328
      //for: [QQYUN-4674]tenant admin approve/reject lacked system notice------------
    }
  }

  @Override
  public IPage<TenantPackUser> queryTenantPackUserList(
      String tenantId, String packId, Integer status, Page<TenantPackUser> page) {
    // Query user
    List<TenantPackUser> userList =
        baseMapper.queryTenantPackUserList(page, tenantId, packId, status);
    // Get user departments and positions under product pack
    userList = getPackUserPositionAndDepart(userList);
    return page.setRecords(userList);
  }

  /**
   * Get user positions and departments
   *
   * @param userList
   * @return
   */
  private List<TenantPackUser> getPackUserPositionAndDepart(List<TenantPackUser> userList) {
    if (userList != null && userList.size() > 0) {
      List<String> userIdList = userList.stream().map(i -> i.getId()).collect(Collectors.toList());
      // Department
      List<UserDepart> depList = baseMapper.queryUserDepartList(userIdList);
      // Position
      List<UserPosition> userPositions = baseMapper.queryUserPositionList(userIdList);
      // Iterate users and attach department/position info
      for (TenantPackUser user : userList) {
        // Add department
        for (UserDepart dep : depList) {
          if (user.getId().equals(dep.getUserId())) {
            user.addDepart(dep.getDepartName());
          }
        }
        // Add position
        for (UserPosition userPosition : userPositions) {
          if (user.getId().equals(userPosition.getUserId())) {
            user.addPosition(userPosition.getPositionName());
          }
        }
      }
    }
    return userList;
  }

  /**
   * save user-department relations
   *
   * @param userId
   * @param departId
   */
  private void saveUserDepart(String userId, String departId) {
    // Count by userId+deptId to see if user-dept relation exists
    Long count = sysUserDepartMapper.getCountByDepartIdAndUserId(userId, departId);
    if (count == 0) {
      SysUserDepart sysUserDepart = new SysUserDepart(userId, departId);
      sysUserDepartMapper.insert(sysUserDepart);
    }
  }

  @Override
  public Long getApplySuperAdminCount() {
    LoginUser sysUser =  (LoginUser) StpUtil.getSession().get("loginUser");
    int tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
    return baseMapper.getApplySuperAdminCount(sysUser.getId(), tenantId);
  }

  /**
   * Approve/reject super-admin join: send message
   *
   * @param user
   * @param content
   */
  public void sendMsgForAgreeAndRefuseJoin(SysUser user, String content) {
    // Send message
    MessageDTO messageDTO = new MessageDTO();
    messageDTO.setToAll(false);
    messageDTO.setToUser(user.getUsername());
    messageDTO.setFromUser("system");
    messageDTO.setTitle(content);
    Map<String, Object> data = new HashMap<>();
    messageDTO.setData(data);
    messageDTO.setContent(content);
    messageDTO.setType("system");
    sysBaseApi.sendTemplateMessage(messageDTO);
  }

  /**
   * Get tenant error message
   *
   * @param status
   * @return
   */
  private String getTenantErrorInfo(String status) {
    String content = "Already in tenant, no invitation needed!";
    if (CommonConstant.USER_TENANT_QUIT.equals(status)) {
      content = "Resigned!";
    } else if (CommonConstant.USER_TENANT_UNDER_REVIEW.equals(status)) {
      content = "Tenant admin review in progress!";
    } else if (CommonConstant.USER_TENANT_REFUSE.equals(status)) {
      content = "Tenant admin rejected!";
    } else if (CommonConstant.USER_TENANT_INVITE.equals(status)) {
      content = "Already invited, waiting for user to accept!";
    }
    return content;
  }

  /**
   * Delete product packs under tenant
   *
   * @param tenantIdList
   */
  private void deleteTenantPackByTenantId(List<Integer> tenantIdList) {
    // 1.delete users under product pack
    sysTenantPackUserMapper.deletePackUserByTenantIds(tenantIdList);
    // 2.Delete the menu permission corresponding to the product package
    sysPackPermissionMapper.deletePackPermByTenantIds(tenantIdList);
    // 3.delete product pack
    sysTenantPackMapper.deletePackByTenantIds(tenantIdList);
  }

  @Override
  public void deleteUserByPassword(SysUser sysUser, Integer tenantId) {
    // Deleted user's user id
    String userId = sysUser.getId();
    // Deleted user's password
    String password = sysUser.getPassword();
    // Currentloginuser
    LoginUser user =  (LoginUser) StpUtil.getSession().get("loginUser");
    // step1 check tenant admin (only super/account admin may delete)
    Long isHaveAdmin = sysTenantPackUserMapper.izHaveBuyAuth(user.getId(), tenantId);
    if (null == isHaveAdmin || 0 == isHaveAdmin) {
      throw new JeecgBootException("You are not an administrator of the current organization, cannot delete user!");
    }
    // step2 may delete when resigned and no other orgs
    SysUserTenant sysUserTenant = userTenantMapper.getUserTenantByTenantId(userId, tenantId);
    if (null == sysUserTenant
        || !CommonConstant.USER_TENANT_QUIT.equals(sysUserTenant.getStatus())) {
      throw new JeecgBootException("User has not resigned, deletion not allowed!");
    }
    List<Integer> tenantIdsByUserId = userTenantMapper.getTenantIdsByUserId(userId);
    if (CollectionUtils.isNotEmpty(tenantIdsByUserId) && tenantIdsByUserId.size() > 0) {
      throw new JeecgBootException("User still has organizations that have not been exited, cannot delete!");
    }
    // step3 creator may delete same-day created users
    // P4: password is @TableField(select=false) — select credential + audit columns explicitly.
    SysUser sysUserData = userService.getOne(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
            .select(SysUser::getId, SysUser::getCreateBy, SysUser::getCreateTime, SysUser::getPassword)
            .eq(SysUser::getId, userId));
    if (!sysUserData.getCreateBy().equals(user.getUsername())) {
      throw new JeecgBootException("You are not the creator of this user, cannot delete!");
    }
    Date createTime = sysUserData.getCreateTime();
    boolean sameDay = DateUtils.isSameDay(createTime, new Date());
    if (!sameDay) {
      throw new JeecgBootException("User was not created today and cannot be deleted!");
    }
    // step4 verify password (reject null/empty)
    if (!org.jeecg.common.util.CredentialGuard.isPresentLoginPassword(password)
        || !org.jeecg.common.util.CredentialGuard.hasStoredPasswordHash(sysUserData.getPassword())
        || !org.jeecg.common.util.crypto.SmCryptoUtil.checkPassword(
            password, sysUserData.getPassword())) {
      throw new JeecgBootException("Incorrect password entered, cannot delete this user!");
    }
    // step5 soft-delete user
    userService.deleteUser(userId);
    // step6 hard-delete user
    userService.removeLogicDeleted(Collections.singletonList(userId));
  }

  @Override
  public List<SysTenant> getTenantListByUserId(String userId) {
    return tenantMapper.getTenantListByUserId(userId);
  }
}
