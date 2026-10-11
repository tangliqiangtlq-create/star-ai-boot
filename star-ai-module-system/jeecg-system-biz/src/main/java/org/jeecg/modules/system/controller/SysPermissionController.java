package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.CurrentBizUserResolver;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.LocaleResolver;
import org.jeecg.common.util.Md5Util;
import org.jeecg.common.util.RoleConfigUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.system.constant.DefIndexConst;
import org.jeecg.modules.system.entity.*;
import org.jeecg.modules.system.model.SysPermissionTree;
import org.jeecg.modules.system.model.TreeModel;
import org.jeecg.modules.system.service.*;
import org.jeecg.modules.system.util.PermissionDataUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Menu permission table front-end controller @Author scott
 *
 * @since 2018-12-21*/
@Slf4j
@RestController
@RequestMapping("/sys/permission")
public class SysPermissionController {

  private static final String TEAM_MENU_PREFIX = "/team";
  private static final String TEAM_PERMISSION_PREFIX = "team:";

  @Autowired private ISysPermissionService sysPermissionService;

  @Autowired private CommonAPI commonAPI;

  @Autowired private ISysRolePermissionService sysRolePermissionService;

  @Autowired private ISysPermissionDataRuleService sysPermissionDataRuleService;

  @Autowired private ISysUserService sysUserService;

  @Autowired private JeecgBaseConfig jeecgBaseConfig;

  @Autowired private BaseCommonService baseCommonService;


  @Autowired private ISysRoleService sysRoleService;

  /** Sub-menu*/
  private static final String CHILDREN = "children";

  /**
 * Load data nodes
 *
 * @return*/
  // @SaCheckPermission("system:permission:list")
  @RequestMapping(value = "/list", method = RequestMethod.POST)
  public Result<List<SysPermissionTree>> list(SysPermission sysPermission, HttpServletRequest req) {
    long start = System.currentTimeMillis();
    Result<List<SysPermissionTree>> result = new Result<>();
    try {
      LambdaQueryWrapper<SysPermission> query = new LambdaQueryWrapper<SysPermission>();
      query.eq(SysPermission::getDelFlag, CommonConstant.DEL_FLAG_0);
      query.orderByAsc(SysPermission::getSortNo);

      // Support fuzzy search by menu name
      if (oConvertUtils.isNotEmpty(sysPermission.getName())) {
        query.like(SysPermission::getName, sysPermission.getName());
      }
      List<SysPermission> list = sysPermissionService.list(query);
      List<SysPermissionTree> treeList = new ArrayList<>();

      // With menu name filter, flatten data without hierarchy
      if (oConvertUtils.isNotEmpty(sysPermission.getName())) {
        if (list != null && list.size() > 0) {
          treeList =
              list.stream()
                  .map(
                      e -> {
                        e.setLeaf(true);
                        return new SysPermissionTree(e);
                      })
                  .collect(Collectors.toList());
        }
      } else {
        getTreeList(treeList, list, null);
      }
      result.setResult(treeList);
      result.setSuccess(true);
      log.info("======GetAllMenuData=====Elapsed:" + (System.currentTimeMillis() - start) + "ms");
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return result;
  }

  /*update_begin author:wuxianquan date:20190908 for: query top-level menus first; load child menus when user expands */
  /**
   * System menu list (top-level menus)
   *
   * @return
   */
  @RequestMapping(value = "/getSystemMenuList", method = RequestMethod.POST)
  public Result<List<SysPermissionTree>> getSystemMenuList() {
    long start = System.currentTimeMillis();
    Result<List<SysPermissionTree>> result = new Result<>();
    try {
      LambdaQueryWrapper<SysPermission> query = new LambdaQueryWrapper<SysPermission>();
      query.eq(SysPermission::getMenuType, CommonConstant.MENU_TYPE_0);
      query.eq(SysPermission::getDelFlag, CommonConstant.DEL_FLAG_0);
      query.orderByAsc(SysPermission::getSortNo);
      List<SysPermission> list = sysPermissionService.list(query);
      List<SysPermissionTree> sysPermissionTreeList = new ArrayList<SysPermissionTree>();
      for (SysPermission sysPermission : list) {
        SysPermissionTree sysPermissionTree = new SysPermissionTree(sysPermission);
        sysPermissionTreeList.add(sysPermissionTree);
      }
      result.setResult(sysPermissionTreeList);
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    log.info("======Fetch top-level menu data=====elapsed:" + (System.currentTimeMillis() - start) + "ms");
    return result;
  }

  /**
   * Query child menus
   *
   * @param parentId
   * @return
   */
  @RequestMapping(value = "/getSystemSubmenu", method = RequestMethod.POST)
  public Result<List<SysPermissionTree>> getSystemSubmenu(
      @RequestParam("parentId") String parentId) {
    Result<List<SysPermissionTree>> result = new Result<>();
    try {
      LambdaQueryWrapper<SysPermission> query = new LambdaQueryWrapper<SysPermission>();
      query.eq(SysPermission::getParentId, parentId);
      query.eq(SysPermission::getDelFlag, CommonConstant.DEL_FLAG_0);
      query.orderByAsc(SysPermission::getSortNo);
      List<SysPermission> list = sysPermissionService.list(query);
      List<SysPermissionTree> sysPermissionTreeList = new ArrayList<SysPermissionTree>();
      for (SysPermission sysPermission : list) {
        SysPermissionTree sysPermissionTree = new SysPermissionTree(sysPermission);
        sysPermissionTreeList.add(sysPermissionTree);
      }
      result.setResult(sysPermissionTreeList);
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return result;
  }

  /*update_end author:wuxianquan date:20190908 for: query top-level menus first; load child menus when user expands */

  // update_begin author:sunjianlei date:20200108 for: Add batch API to query child menus by parent ID -------------
  /**
   * Query child menus
   *
   * @param parentIds parent IDs (comma-separated)
   * @return key-value Map
   */
  @PostMapping("/getSystemSubmenuBatch")
  public Result getSystemSubmenuBatch(@RequestParam("parentIds") String parentIds) {
    try {
      LambdaQueryWrapper<SysPermission> query = new LambdaQueryWrapper<>();
      List<String> parentIdList = Arrays.asList(parentIds.split(","));
      query.in(SysPermission::getParentId, parentIdList);
      query.eq(SysPermission::getDelFlag, CommonConstant.DEL_FLAG_0);
      query.orderByAsc(SysPermission::getSortNo);
      List<SysPermission> list = sysPermissionService.list(query);
      Map<String, List<SysPermissionTree>> listMap = new HashMap(5);
      for (SysPermission item : list) {
        String pid = item.getParentId();
        if (parentIdList.contains(pid)) {
          List<SysPermissionTree> mapList = listMap.get(pid);
          if (mapList == null) {
            mapList = new ArrayList<>();
          }
          mapList.add(new SysPermissionTree(item));
          listMap.put(pid, mapList);
        }
      }
      return Result.ok(listMap);
    } catch (Exception e) {
      log.error("operation failed", e);
      return Result.error(I18nUtil.getMessage("sysPermission.batchQuerySubMenuFailed", new Object[]{e.getMessage()}));
    }
  }

  // update_end author:sunjianlei date:20200108 for: Add batch API to query child menus by parent ID -------------

  //	/**
  //	 * Query menu and button permissions for a user (by account)
  //	 *
  //	 * @return
  //	 */
  //	@RequestMapping(value = "/queryByUser", method = RequestMethod.POST)
  //	public Result<JSONArray> queryByUser(HttpServletRequest req) {
  //		Result<JSONArray> result = new Result<>();
  //		try {
  //			String username = req.getParameter("username");
  //			List<SysPermission> metaList = sysPermissionService.queryByUser(username);
  //			JSONArray jsonArray = new JSONArray();
  //			this.getPermissionJsonArray(jsonArray, metaList, null);
  //			result.setResult(jsonArray);
  //			result.success("Query successful");
  //		} catch (Exception e) {
  //			result.error500("Query failed:" + e.getMessage());
  //			log.error("operation failed", e);
  //		}
  //		return result;
  //	}

  /**
   * Query menu and button permissions owned by user
   *
   * @return
   */
  @RequestMapping(value = "/getUserPermissionByToken", method = RequestMethod.POST)
  // @DynamicTable(value = DynamicTableConstant.SYS_ROLE_INDEX)
  public Result<?> getUserPermissionByToken(HttpServletRequest request) {
    Result<JSONObject> result = new Result<JSONObject>();
    try {
      // Get current user
      LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
      if (oConvertUtils.isEmpty(loginUser)) {
        return Result.error(I18nUtil.getMessage("sysPermission.pleaseLogin"));
      }
      List<SysPermission> metaList = sysPermissionService.queryByUser(loginUser.getId());
      if (isTeamMember(loginUser)) {
        metaList = filterTeamPermissions(metaList);
      }
      // Add home route
      // update-begin-author:taoyan date:20200211 for: TASK #3368
      // [Route cache] Home page cache settings are incorrect; should follow backend route config for caching

      // If role home not authorized, auto-add home route
      if (!PermissionDataUtil.hasIndexPage(metaList)) {
        LambdaQueryWrapper<SysPermission> indexQueryWrapper = new LambdaQueryWrapper<>();
        indexQueryWrapper.eq(SysPermission::getUrl, DefIndexConst.DEF_INDEX_URL);
        SysPermission indexMenu = sysPermissionService.getOne(indexQueryWrapper);
        if (indexMenu == null) {
          indexMenu = new SysPermission();
          indexMenu.setUrl(DefIndexConst.DEF_INDEX_URL);
          indexMenu.setComponent(DefIndexConst.DEF_INDEX_COMPONENT);
          indexMenu.setRoute(true);
          indexMenu.setName(DefIndexConst.DEF_INDEX_NAME);
          indexMenu.setMenuType(0);
        }
        if (indexMenu.getParentId() != null
            && !PermissionDataUtil.hasMenuById(metaList, indexMenu.getParentId())) {
          indexMenu.setMenuType(0);
          indexMenu.setParentId(null);
        }
        if (oConvertUtils.isEmpty(indexMenu.getIcon())) {
          indexMenu.setIcon("ant-design:home");
        }
        metaList.add(0, indexMenu);
      }
      // update-end-author:taoyan date:20200211 for: TASK #3368 [Route cache] Home page cache settings are incorrect; should follow backend route config for caching

      JSONObject json = new JSONObject();
      JSONArray menujsonArray = new JSONArray();
      this.getPermissionJsonArray(menujsonArray, metaList, null);
      // If all submenus under a top-level menu are hidden routes, hide the top-level menu
      this.handleFirstLevelMenuHidden(menujsonArray);

      JSONArray authjsonArray = new JSONArray();
      this.getAuthJsonArray(authjsonArray, metaList);
      // Query all permissions
      LambdaQueryWrapper<SysPermission> query =
          new LambdaQueryWrapper<SysPermission>()
              .select(
                  SysPermission::getName,
                  SysPermission::getPermsType,
                  SysPermission::getPerms,
                  SysPermission::getStatus);
      query.eq(SysPermission::getDelFlag, CommonConstant.DEL_FLAG_0);
      query.eq(SysPermission::getMenuType, CommonConstant.MENU_TYPE_2);
      // query.eq(SysPermission::getStatus, "1");
      List<SysPermission> allAuthList = sysPermissionService.list(query);
      JSONArray allauthjsonArray = new JSONArray();
      this.getAllAuthJsonArray(allauthjsonArray, allAuthList);
      // Route menu
      json.put("menu", menujsonArray);
      // Button permissions (user permission set)
      json.put("auth", authjsonArray);
      // Button permissions (user permission set)
      List<String> codeList =
          metaList.stream()
              .filter(
                  (permission) ->
                      CommonConstant.MENU_TYPE_2.equals(permission.getMenuType())
                          && CommonConstant.STATUS_1.equals(permission.getStatus()))
              .collect(
                  ArrayList::new,
                  (list, permission) -> list.add(permission.getPerms()),
                  ArrayList::addAll);
      // Owned permission codes (Vue3 only)
      json.put("codeList", codeList);
      // Full permission config set (button permissions, access permissions)
      json.put("allAuth", allauthjsonArray);
      // Data source safe mode
      json.put(
          "sysSafeMode",
          jeecgBaseConfig.getFirewall() != null
              ? jeecgBaseConfig.getFirewall().getDataSourceSafe()
              : false);
      result.setResult(json);
    } catch (Exception e) {
      result.error500(I18nUtil.getMessage("sysPermission.queryFailed", new Object[]{e.getMessage()}));
      log.error("operation failed", e);
    }
    return result;
  }

  /** [vue3 only] Get 1) user's button/form access permissions 2) all permissions (menu permission config) 3) system security mode (when enabled, ONLINE report data source is required) */
  @RequestMapping(value = "/getPermCode", method = RequestMethod.POST)
  public Result<?> getPermCode() {
    try {
      // Check login status; NotLoginException is thrown when token is kicked off
      StpUtil.checkLogin();
      // Get current user directly
      LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
      if (oConvertUtils.isEmpty(loginUser)) {
        return Result.error(I18nUtil.getMessage("sysPermission.pleaseLogin"));
      }
      // Get current user's permission set
      List<SysPermission> metaList = sysPermissionService.queryByUser(loginUser.getId());
      // Button permissions (user permission set)
      List<String> codeList =
          metaList.stream()
              .filter(
                  (permission) ->
                      CommonConstant.MENU_TYPE_2.equals(permission.getMenuType())
                          && CommonConstant.STATUS_1.equals(permission.getStatus()))
              .collect(
                  ArrayList::new,
                  (list, permission) -> list.add(permission.getPerms()),
                  ArrayList::addAll);
      //
      JSONArray authArray = new JSONArray();
      this.getAuthJsonArray(authArray, metaList);
      // Query all permissions
      LambdaQueryWrapper<SysPermission> query =
          new LambdaQueryWrapper<SysPermission>()
              .select(
                  SysPermission::getName,
                  SysPermission::getPermsType,
                  SysPermission::getPerms,
                  SysPermission::getStatus);
      query.eq(SysPermission::getDelFlag, CommonConstant.DEL_FLAG_0);
      query.eq(SysPermission::getMenuType, CommonConstant.MENU_TYPE_2);
      List<SysPermission> allAuthList = sysPermissionService.list(query);
      JSONArray allAuthArray = new JSONArray();
      this.getAllAuthJsonArray(allAuthArray, allAuthList);
      JSONObject result = new JSONObject();
      // Owned permission codes
      result.put("codeList", codeList);
      // Button permissions (user permission set)
      result.put("auth", authArray);
      // Full permission config set (button permissions, access permissions)
      result.put("allAuth", allAuthArray);
      // Data source safe mode
      result.put(
          "sysSafeMode",
          jeecgBaseConfig.getFirewall() != null
              ? jeecgBaseConfig.getFirewall().getDataSourceSafe()
              : null);
      return Result.OK(result);
    } catch (Exception e) {
      log.error("operation failed", e);
      return Result.error(I18nUtil.getMessage("sysPermission.queryFailed", new Object[]{e.getMessage()}));
    }
  }

  /**
   * AddMenu
   *
   * @param permission
   * @return
   */
  @SaCheckPermission("system:permission:add")
  @RequestMapping(value = "/add", method = RequestMethod.POST)
  public Result<SysPermission> add(@RequestBody SysPermission permission) {
    Result<SysPermission> result = new Result<SysPermission>();
    try {
      permission = PermissionDataUtil.intelligentProcessData(permission);
      sysPermissionService.addPermission(permission);
      result.success(I18nUtil.getMessage("sysPermission.addSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * EditMenu
   *
   * @param permission
   * @return
   */
  @SaCheckPermission("system:permission:edit")
  @RequestMapping(
      value = "/edit",
      method = {RequestMethod.PUT, RequestMethod.POST})
  public Result<SysPermission> edit(@RequestBody SysPermission permission) {
    Result<SysPermission> result = new Result<>();
    try {
      permission = PermissionDataUtil.intelligentProcessData(permission);
      sysPermissionService.editPermission(permission);
      result.success(I18nUtil.getMessage("sysPermission.modifySuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * Check whether menu path exists
   *
   * @param id
   * @param url
   * @return
   */
  @PostMapping(value = "/checkPermDuplication")
  public Result<String> checkPermDuplication(
      @RequestParam(name = "id", required = false) String id,
      @RequestParam(name = "url") String url,
      @RequestParam(name = "alwaysShow") Boolean alwaysShow) {
    Result<String> result = new Result<>();
    try {
      boolean check = sysPermissionService.checkPermDuplication(id, url, alwaysShow);
      if (check) {
        return Result.ok(I18nUtil.getMessage("sysPermission.valueAvailable"));
      }
      return Result.error(I18nUtil.getMessage("sysPermission.pathDuplicate"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * DeleteMenu
   *
   * @param id
   * @return
   */
  @SaCheckPermission("system:permission:delete")
  @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
  public Result<SysPermission> delete(@RequestParam(name = "id", required = true) String id) {
    Result<SysPermission> result = new Result<>();
    try {
      sysPermissionService.deletePermission(id);
      result.success(I18nUtil.getMessage("sysPermission.deleteSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(e.getMessage());
    }
    return result;
  }

  /**
   * Batch deleteMenu
   *
   * @param ids
   * @return
   */
  @SaCheckPermission("system:permission:deleteBatch")
  @RequestMapping(value = "/deleteBatch", method = RequestMethod.DELETE)
  public Result<SysPermission> deleteBatch(
      @RequestParam(name = "ids", required = true) String ids) {
    Result<SysPermission> result = new Result<>();
    try {
      String[] arr = ids.split(",");
      for (String id : arr) {
        if (oConvertUtils.isNotEmpty(id)) {
          try {
            sysPermissionService.deletePermission(id);
          } catch (JeecgBootException e) {
            if (e.getMessage() != null && e.getMessage().contains("Menu info not found")) {
              log.warn("operation failed");
            } else {
              throw e;
            }
          }
        }
      }
      result.success(I18nUtil.getMessage("sysPermission.deleteSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysPermission.deleteFailed"));
    }
    return result;
  }
  /**
   * Get full permission tree
   *
   * @return
   */
  @RequestMapping(value = "/queryTreeList", method = RequestMethod.POST)
  public Result<Map<String, Object>> queryTreeList() {
    Result<Map<String, Object>> result = new Result<>();
    // All permission ids
    List<String> ids = new ArrayList<>();
    try {
      LambdaQueryWrapper<SysPermission> query = new LambdaQueryWrapper<SysPermission>();
      query.eq(SysPermission::getDelFlag, CommonConstant.DEL_FLAG_0);
      query.orderByAsc(SysPermission::getSortNo);
      List<SysPermission> list = sysPermissionService.list(query);
      for (SysPermission sysPer : list) {
        ids.add(sysPer.getId());
      }
      // Process multilingual name field by current locale
      String currentLang = LocaleResolver.getCurrLang();
      if (oConvertUtils.isEmpty(currentLang)) {
        currentLang = "zh_CN"; // Default to Chinese
      }
      for (SysPermission permission : list) {
        String localizedName = getLocalizedName(permission.getName(), currentLang);
        if (oConvertUtils.isNotEmpty(localizedName)) {
          permission.setName(localizedName);
        }
      }
      List<TreeModel> treeList = new ArrayList<>();
      getTreeModelList(treeList, list, null);

      Map<String, Object> resMap = new HashMap<String, Object>(5);
      // All tree node data
      resMap.put("treeList", treeList);
      // All tree ids
      resMap.put("ids", ids);
      result.setResult(resMap);
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return result;
  }

  /**
   * Async load data node [deprecated API, unused]
   *
   * @return
   */
  @RequestMapping(value = "/queryListAsync", method = RequestMethod.POST)
  public Result<List<TreeModel>> queryAsync(
      @RequestParam(name = "pid", required = false) String parentId) {
    Result<List<TreeModel>> result = new Result<>();
    try {
      List<TreeModel> list = sysPermissionService.queryListByParentId(parentId);
      if (list == null || list.size() <= 0) {
        result.error500(I18nUtil.getMessage("sysPermission.roleInfoNotFound"));
      } else {
        result.setResult(list);
        result.setSuccess(true);
      }
    } catch (Exception e) {
      log.error("operation failed", e);
    }

    return result;
  }

  /**
   * Query role authorization
   *
   * @return
   */
  @RequestMapping(value = "/queryRolePermission", method = RequestMethod.POST)
  public Result<List<String>> queryRolePermission(
      @RequestParam(name = "roleId", required = true) String roleId) {
    Result<List<String>> result = new Result<>();
    try {
      List<SysRolePermission> list =
          sysRolePermissionService.list(
              new QueryWrapper<SysRolePermission>()
                  .lambda()
                  .eq(SysRolePermission::getRoleId, roleId));
      result.setResult(
          list.stream()
              .map(sysRolePermission -> String.valueOf(sysRolePermission.getPermissionId()))
              .collect(Collectors.toList()));
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return result;
  }

  /**
   * Save role authorization
   *
   * @return
   */
  @RequestMapping(value = "/saveRolePermission", method = RequestMethod.POST)
  @SaCheckPermission("system:permission:saveRole")
  public Result<String> saveRolePermission(@RequestBody JSONObject json) {
    long start = System.currentTimeMillis();
    Result<String> result = new Result<>();
    try {
      String roleId = json.getString("roleId");
      
      // Permission check: operations_admin may only configure roles other than operations_admin and admin
      List<String> currentUserRoles = StpUtil.getRoleList();
      if (currentUserRoles != null && currentUserRoles.contains(SecurityRoleConstants.ROLE_OPS_ADMIN) && !currentUserRoles.contains("admin")) {
        // Get role info to operate on
        SysRole targetRole = sysRoleService.getById(roleId);
        if (targetRole != null) {
          String targetRoleCode = targetRole.getRoleCode();
          if (RoleConfigUtil.isConfiguredAdminRole(commonAPI, targetRoleCode)) {
            result.setSuccess(false);
            result.setMessage(I18nUtil.getMessage("sysUser.operationsAdminRoleRestricted"));
            return result;
          }
        }
      }
      
      String permissionIds = json.getString("permissionIds");
      String lastPermissionIds = json.getString("lastpermissionIds");
      this.sysRolePermissionService.saveRolePermission(roleId, permissionIds, lastPermissionIds);
      // update-begin---author:wangshuai ---date:20220316  for: [VUEN-234] User management role authorization adds sensitive audit log ------------
      LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
      baseCommonService.addLog(
          "UpdateRoleID: " + roleId + " 's permission config, operator: " + loginUser.getUsername(),
          CommonConstant.LOG_TYPE_2,
          2);
      // update-end---author:wangshuai ---date:20220316  for: [VUEN-234] User management role authorization adds sensitive audit log ------------
      result.success(I18nUtil.getMessage("sysPermission.saveSuccess"));
      log.info("======Role authorization succeeded=====elapsed:" + (System.currentTimeMillis() - start) + "ms");

      // update-end---author:scott ---date::2024-06-18  for: [TV360X-1320] Permission assignment requires logout/re-login to take effect, causing user confusion ---

    } catch (Exception e) {
      result.error500(I18nUtil.getMessage("sysPermission.authorizeFailed"));
      log.error("operation failed", e);
    }
    return result;
  }

  private void getTreeList(
      List<SysPermissionTree> treeList, List<SysPermission> metaList, SysPermissionTree temp) {
    for (SysPermission permission : metaList) {
      String tempPid = permission.getParentId();
      SysPermissionTree tree = new SysPermissionTree(permission);
      if (temp == null && oConvertUtils.isEmpty(tempPid)) {
        treeList.add(tree);
        if (!tree.getIsLeaf()) {
          getTreeList(treeList, metaList, tree);
        }
      } else if (temp != null && tempPid != null && tempPid.equals(temp.getId())) {
        temp.getChildren().add(tree);
        if (!tree.getIsLeaf()) {
          getTreeList(treeList, metaList, tree);
        }
      }
    }
  }

  private void getTreeModelList(
      List<TreeModel> treeList, List<SysPermission> metaList, TreeModel temp) {
    for (SysPermission permission : metaList) {
      String tempPid = permission.getParentId();
      TreeModel tree = new TreeModel(permission);
      if (temp == null && oConvertUtils.isEmpty(tempPid)) {
        treeList.add(tree);
        if (!tree.getIsLeaf()) {
          getTreeModelList(treeList, metaList, tree);
        }
      } else if (temp != null && tempPid != null && tempPid.equals(temp.getKey())) {
        temp.getChildren().add(tree);
        if (!tree.getIsLeaf()) {
          getTreeModelList(treeList, metaList, tree);
        }
      }
    }
  }

  /**
   * If all submenus of a top-level menu are hidden routes, hide the top-level menu
   *
   * @param jsonArray
   */
  private void handleFirstLevelMenuHidden(JSONArray jsonArray) {
    jsonArray =
        jsonArray.stream()
            .map(
                obj -> {
                  JSONObject returnObj = new JSONObject();
                  JSONObject jsonObj = (JSONObject) obj;
                  if (jsonObj.containsKey(CHILDREN)) {
                    JSONArray childrens = jsonObj.getJSONArray(CHILDREN);
                    childrens =
                        childrens.stream()
                            .filter(
                                arrObj -> !"true".equals(((JSONObject) arrObj).getString("hidden")))
                            .collect(Collectors.toCollection(JSONArray::new));
                    if (childrens == null || childrens.size() == 0) {
                      jsonObj.put("hidden", true);

                      // vue3 version compatibility code
                      JSONObject meta = new JSONObject();
                      meta.put("hideMenu", true);
                      jsonObj.put("meta", meta);
                    }
                  }
                  return returnObj;
                })
            .collect(Collectors.toCollection(JSONArray::new));
  }

  /**
   * Build permission JSON array
   *
   * @param jsonArray
   * @param allList
   */
  private void getAllAuthJsonArray(JSONArray jsonArray, List<SysPermission> allList) {
    JSONObject json = null;
    for (SysPermission permission : allList) {
      json = new JSONObject();
      json.put("action", permission.getPerms());
      json.put("status", permission.getStatus());
      // 1 enabled, 2 disabled
      json.put("type", permission.getPermsType());
      json.put("describe", permission.getName());
      jsonArray.add(json);
    }
  }

  /**
   * Build permission JSON array
   *
   * @param jsonArray
   * @param metaList
   */
  private void getAuthJsonArray(JSONArray jsonArray, List<SysPermission> metaList) {
    for (SysPermission permission : metaList) {
      if (permission.getMenuType() == null) {
        continue;
      }
      JSONObject json = null;
      if (permission.getMenuType().equals(CommonConstant.MENU_TYPE_2)
          && CommonConstant.STATUS_1.equals(permission.getStatus())) {
        json = new JSONObject();
        json.put("action", permission.getPerms());
        json.put("type", permission.getPermsType());
        json.put("describe", permission.getName());
        jsonArray.add(json);
      }
    }
  }

  /**
   * Build menu JSON array
   *
   * @param jsonArray
   * @param metaList
   * @param parentJson
   */
  private void getPermissionJsonArray(
      JSONArray jsonArray, List<SysPermission> metaList, JSONObject parentJson) {
    for (SysPermission permission : metaList) {
      if (permission.getMenuType() == null) {
        continue;
      }
      String tempPid = permission.getParentId();
      JSONObject json = getPermissionJsonObject(permission);
      if (json == null) {
        continue;
      }
      if (parentJson == null && oConvertUtils.isEmpty(tempPid)) {
        jsonArray.add(json);
        if (!permission.isLeaf()) {
          getPermissionJsonArray(jsonArray, metaList, json);
        }
      } else if (parentJson != null
          && oConvertUtils.isNotEmpty(tempPid)
          && tempPid.equals(parentJson.getString("id"))) {
        // Type (0: top-level menu 1: submenu 2: button)
        if (permission.getMenuType().equals(CommonConstant.MENU_TYPE_2)) {
          JSONObject metaJson = parentJson.getJSONObject("meta");
          if (metaJson.containsKey("permissionList")) {
            metaJson.getJSONArray("permissionList").add(json);
          } else {
            JSONArray permissionList = new JSONArray();
            permissionList.add(json);
            metaJson.put("permissionList", permissionList);
          }
          // Type (0: top-level menu 1: submenu 2: button)
        } else if (permission.getMenuType().equals(CommonConstant.MENU_TYPE_1)
            || permission.getMenuType().equals(CommonConstant.MENU_TYPE_0)) {
          if (parentJson.containsKey("children")) {
            parentJson.getJSONArray("children").add(json);
          } else {
            JSONArray children = new JSONArray();
            children.add(json);
            parentJson.put("children", children);
          }

          if (!permission.isLeaf()) {
            getPermissionJsonArray(jsonArray, metaList, json);
          }
        }
      }
    }
  }

  /**
   * Generate route JSON from menu config
   *
   * @param permission
   * @return
   */
  private JSONObject getPermissionJsonObject(SysPermission permission) {
    JSONObject json = new JSONObject();
    // Type (0: top-level menu 1: submenu 2: button)
    if (permission.getMenuType().equals(CommonConstant.MENU_TYPE_2)) {
      // json.put("action", permission.getPerms());
      // json.put("type", permission.getPermsType());
      // json.put("describe", permission.getName());
      return null;
    } else if (permission.getMenuType().equals(CommonConstant.MENU_TYPE_0)
        || permission.getMenuType().equals(CommonConstant.MENU_TYPE_1)) {
      json.put("id", permission.getId());
      if (permission.isRoute()) {
        // Means generate route
        json.put("route", "1");
      } else {
        // Means do not generate route
        json.put("route", "0");
      }

      if (isWwwHttpUrl(permission.getUrl())) {
        json.put("path", Md5Util.md5Encode(permission.getUrl(), "utf-8"));
      } else {
        json.put("path", permission.getUrl());
      }

      // Important rule: route name (route name generated from URL; used by front-end for navigation)
      if (oConvertUtils.isNotEmpty(permission.getComponentName())) {
        json.put("name", permission.getComponentName());
      } else {
        json.put("name", urlToRouteName(permission.getUrl()));
      }

      JSONObject meta = new JSONObject();
      // Hide route; visible by default
      if (permission.isHidden()) {
        json.put("hidden", true);
        // vue3 version compatibility code
        meta.put("hideMenu", true);
      }
      // Aggregate routes
      if (permission.isAlwaysShow()) {
        json.put("alwaysShow", true);
      }
      json.put("component", permission.getComponent());
      // User sets whether to cache page (boolean)
      if (permission.isKeepAlive()) {
        meta.put("keepAlive", true);
      } else {
        meta.put("keepAlive", false);
      }

      /*update_begin author:wuxianquan date:20190908 for: add external link open mode to menu info */
      // External link menu open mode
      if (permission.isInternalOrExternal()) {
        meta.put("internalOrExternal", true);
      } else {
        meta.put("internalOrExternal", false);
      }
      /* update_end author:wuxianquan date:20190908 for: Add external link open mode to menu info*/

      meta.put("title", permission.getName());

      // update-begin--Author:scott  Date:20201015 for: Route cache issue: reopening a closed tab does not refresh #842
      String component = permission.getComponent();
      if (oConvertUtils.isNotEmpty(permission.getComponentName())
          || oConvertUtils.isNotEmpty(component)) {
        meta.put(
            "componentName",
            oConvertUtils.getString(
                permission.getComponentName(),
                component.substring(component.lastIndexOf("/") + 1)));
      }
      // update-end--Author:scott  Date:20201015 for: Route cache issue: reopening a closed tab does not refresh #842

      if (oConvertUtils.isEmpty(permission.getParentId())) {
        // Top-level menu redirect URL
        json.put("redirect", permission.getRedirect());
        if (oConvertUtils.isNotEmpty(permission.getIcon())) {
          meta.put("icon", permission.getIcon());
        }
      } else {
        if (oConvertUtils.isNotEmpty(permission.getIcon())) {
          meta.put("icon", permission.getIcon());
        }
      }
      if (isWwwHttpUrl(permission.getUrl())) {
        meta.put("url", permission.getUrl());
      }
      // update-begin--Author:sunjianlei  Date:20210918 for: Add hidden tab support for vue3 project
      if (permission.isHideTab()) {
        meta.put("hideTab", true);
      }
      // update-end--Author:sunjianlei  Date:20210918 for: Add hidden tab support for vue3 project
      json.put("meta", meta);
    }

    return json;
  }

  /**
   * Check whether URL is external e.g. http://localhost:8080/jeecg-boot/swagger-ui.html#/ Supports special format: {{
   * window._CONFIG['domianURL'] }}/druid/ {{ JS snippet }}; frontend parser auto-executes JS snippet
   *
   * @return
   */
  private boolean isWwwHttpUrl(String url) {
    boolean flag =
        url != null
            && (url.startsWith(CommonConstant.HTTP_PROTOCOL)
                || url.startsWith(CommonConstant.HTTPS_PROTOCOL)
                || url.startsWith(SymbolConstant.DOUBLE_LEFT_CURLY_BRACKET));
    if (flag) {
      return true;
    }
    return false;
  }

  /**
   * Generate route name from URL (strip leading slash; replace '/' in the path with '-'). Example: URL = /isystem/role RouteName = isystem-role
   *
   * @return
   */
  private String urlToRouteName(String url) {
    if (oConvertUtils.isNotEmpty(url)) {
      if (url.startsWith(SymbolConstant.SINGLE_SLASH)) {
        url = url.substring(1);
      }
      url = url.replace("/", "-");

      // Special flag
      url = url.replace(":", "@");
      return url;
    } else {
      return null;
    }
  }

  /**
   * Get permission data for menu id
   *
   * @param sysPermissionDataRule
   * @return
   */
  @RequestMapping(value = "/getPermRuleListByPermId", method = RequestMethod.POST)
  public Result<List<SysPermissionDataRule>> getPermRuleListByPermId(
      SysPermissionDataRule sysPermissionDataRule) {
    List<SysPermissionDataRule> permRuleList =
        sysPermissionDataRuleService.getPermRuleListByPermId(
            sysPermissionDataRule.getPermissionId());
    Result<List<SysPermissionDataRule>> result = new Result<>();
    result.setSuccess(true);
    result.setResult(permRuleList);
    return result;
  }

  /**
   * AddMenuPermissionData
   *
   * @param sysPermissionDataRule
   * @return
   */
  @SaCheckPermission("system:permission:addRule")
  @RequestMapping(value = "/addPermissionRule", method = RequestMethod.POST)
  public Result<SysPermissionDataRule> addPermissionRule(
      @RequestBody SysPermissionDataRule sysPermissionDataRule) {
    Result<SysPermissionDataRule> result = new Result<SysPermissionDataRule>();
    try {
      sysPermissionDataRule.setCreateTime(new Date());
      sysPermissionDataRuleService.savePermissionDataRule(sysPermissionDataRule);
      result.success(I18nUtil.getMessage("sysPermission.addSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  @SaCheckPermission("system:permission:editRule")
  @RequestMapping(
      value = "/editPermissionRule",
      method = {RequestMethod.PUT, RequestMethod.POST})
  public Result<SysPermissionDataRule> editPermissionRule(
      @RequestBody SysPermissionDataRule sysPermissionDataRule) {
    Result<SysPermissionDataRule> result = new Result<SysPermissionDataRule>();
    try {
      sysPermissionDataRuleService.saveOrUpdate(sysPermissionDataRule);
      result.success(I18nUtil.getMessage("sysPermission.updateSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * DeleteMenuPermissionData
   *
   * @param id
   * @return
   */
  @SaCheckPermission("system:permission:deleteRule")
  @RequestMapping(value = "/deletePermissionRule", method = RequestMethod.DELETE)
  public Result<SysPermissionDataRule> deletePermissionRule(
      @RequestParam(name = "id", required = true) String id) {
    Result<SysPermissionDataRule> result = new Result<SysPermissionDataRule>();
    try {
      sysPermissionDataRuleService.deletePermissionDataRule(id);
      result.success(I18nUtil.getMessage("sysPermission.deleteSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * QueryMenuPermissionData
   *
   * @param sysPermissionDataRule
   * @return
   */
  @RequestMapping(value = "/queryPermissionRule", method = RequestMethod.POST)
  public Result<List<SysPermissionDataRule>> queryPermissionRule(
      SysPermissionDataRule sysPermissionDataRule) {
    Result<List<SysPermissionDataRule>> result = new Result<>();
    try {
      List<SysPermissionDataRule> permRuleList =
          sysPermissionDataRuleService.queryPermissionRule(sysPermissionDataRule);
      result.setResult(permRuleList);
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * DepartmentPermissionTable
   *
   * @param departId
   * @return
   */
  @RequestMapping(value = "/queryDepartPermission", method = RequestMethod.POST)
  public Result<List<String>> queryDepartPermission(
      @RequestParam(name = "departId", required = true) String departId) {
    Result<List<String>> result = new Result<>();
    // NOTE: SysDepartPermission cleaned, stub
    result.setResult(new ArrayList<>());
    result.setSuccess(true);
    return result;
  }

  /**
   * Save department authorization
   *
   * @return
   */
  @RequestMapping(value = "/saveDepartPermission", method = RequestMethod.POST)
  @SaCheckPermission("system:permission:saveDepart")
  public Result<String> saveDepartPermission(@RequestBody JSONObject json) {
    // NOTE: SysDepartPermission cleaned, stub
    return Result.error("功能已停用");
  }

  private boolean isTeamMember(LoginUser loginUser) {
    if (loginUser == null || oConvertUtils.isEmpty(loginUser.getUsername())) {
      return false;
    }
    if (oConvertUtils.isEmpty(loginUser.getDlygx())) {
      return false;
    }
    List<String> roleCodes = sysUserService.getRole(loginUser.getUsername());
    return roleCodes != null && !roleCodes.isEmpty();
  }

  private List<SysPermission> filterTeamPermissions(List<SysPermission> permissionList) {
    if (permissionList == null || permissionList.isEmpty()) {
      return permissionList;
    }
    return permissionList.stream()
        .filter(
            permission ->
                (oConvertUtils.isNotEmpty(permission.getUrl())
                        && permission.getUrl().startsWith(TEAM_MENU_PREFIX))
                    || (oConvertUtils.isNotEmpty(permission.getPerms())
                        && permission.getPerms().startsWith(TEAM_PERMISSION_PREFIX)))
        .collect(Collectors.toList());
  }


  /**
   * Resolve multilingual name field by current locale
   * 
   * @param nameJson multilingual JSON, e.g. {"zh_CN": "Menu Management", "en": "Menu Management"}
   * @param currentLang current locale, e.g. "zh_CN" or "en"
   * @return localized text; returns original value if parse fails
   */
  private String getLocalizedName(String nameJson, String currentLang) {
    if (oConvertUtils.isEmpty(nameJson)) {
      return nameJson;
    }
    try {
      // Try parsing multilingual content as JSON
      JSONObject nameObj = JSONObject.parseObject(nameJson);
      if (nameObj != null) {
        // Prefer current language
        if (nameObj.containsKey(currentLang)) {
          String localizedName = nameObj.getString(currentLang);
          if (oConvertUtils.isNotEmpty(localizedName)) {
            return localizedName;
          }
        }
        // If current language unavailable, try fallback language
        String fallbackLang = "zh_CN".equals(currentLang) ? "en" : "zh_CN";
        if (nameObj.containsKey(fallbackLang)) {
          String fallbackName = nameObj.getString(fallbackLang);
          if (oConvertUtils.isNotEmpty(fallbackName)) {
            return fallbackName;
          }
        }
        // If neither language exists, try first available value
        for (String key : nameObj.keySet()) {
          String value = nameObj.getString(key);
          if (oConvertUtils.isNotEmpty(value)) {
            return value;
          }
        }
      }
    } catch (Exception e) {
      // If not JSON format, return original value
      log.debug("name field is not JSON format, returning raw value: {}", nameJson);
    }
    // If parse fails or not JSON, return original value
    return nameJson;
  }
}
