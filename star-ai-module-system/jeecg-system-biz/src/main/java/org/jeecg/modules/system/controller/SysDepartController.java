package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.IOException;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.ImportExcelUtil;
import org.jeecg.common.util.ETagResponseSupport;
import org.jeecg.common.util.RedisScanSupport;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.system.entity.SysDepart;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.model.DepartIdModel;
import org.jeecg.modules.system.model.SysDepartTreeModel;
import org.jeecg.modules.system.service.ISysDepartService;
import org.jeecg.modules.system.service.ISysUserDepartService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.system.vo.SysDepartExportVo;
import org.jeecg.modules.system.vo.lowapp.ExportDepartVo;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

/**
 * Department table front-end controller
 *
 * <p>@Author: Steve @Since： 2019-01-22*/
@RestController
@RequestMapping("/sys/sysDepart")
@Slf4j
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class SysDepartController {

  @Autowired private ISysDepartService sysDepartService;
  @Autowired public RedisTemplate<String, Object> redisTemplate;
  @Autowired private ISysUserService sysUserService;
  @Autowired private ISysUserDepartService sysUserDepartService;

  /**
   * Query my department and return tree structure to frontend
   *
   * @return
   */
  @RequestMapping(value = "/queryMyDeptTreeList", method = RequestMethod.POST)
  public Result<List<SysDepartTreeModel>> queryMyDeptTreeList() {
    Result<List<SysDepartTreeModel>> result = new Result<>();
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    try {
      if (oConvertUtils.isNotEmpty(user.getUserIdentity())
          && user.getUserIdentity().equals(CommonConstant.USER_IDENTITY_2)) {
        // update-begin--Author:liusq  Date:20210624  for: Front-end display issue when department query ids is empty issues/I3UD06
        String departIds = user.getDepartIds();
        if (StringUtils.isNotBlank(departIds)) {
          List<SysDepartTreeModel> list = sysDepartService.queryMyDeptTreeList(departIds);
          result.setResult(list);
        }
        // update-end--Author:liusq  Date:20210624  for: Front-end display issue when department query ids is empty issues/I3UD06
        result.setMessage(CommonConstant.USER_IDENTITY_2.toString());
        result.setSuccess(true);
      } else {
        result.setMessage(CommonConstant.USER_IDENTITY_1.toString());
        result.setSuccess(true);
      }
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return result;
  }

  /**
   * Query all departments and return tree structure to frontend
   *
   * @return
   */
  @RequestMapping(value = "/queryTreeList", method = RequestMethod.POST)
  public Result<List<SysDepartTreeModel>> queryTreeList(
      @RequestParam(name = "ids", required = false) String ids) {
    Result<List<SysDepartTreeModel>> result = new Result<>();
    try {
      // Read from memory
      //			List<SysDepartTreeModel> list =FindsDepartsChildrenUtil.getSysDepartTreeList();
      //			if (CollectionUtils.isEmpty(list)) {
      //				list = sysDepartService.queryTreeList();
      //			}
      if (oConvertUtils.isNotEmpty(ids)) {
        List<SysDepartTreeModel> departList = sysDepartService.queryTreeList(ids);
        result.setResult(departList);
      } else {
        List<SysDepartTreeModel> list = sysDepartService.queryTreeList();
        result.setResult(list);
      }
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return result;
  }

  /**
   * Asynchronously query department list
   *
   * @param parentId parent node; passed on async load
   * @param ids passed from frontend for display echo
   * @param primaryKey primary key field (id or orgCode)
   * @return
   */
  @RequestMapping(value = "/queryDepartTreeSync", method = RequestMethod.POST)
  public Result<List<SysDepartTreeModel>> queryDepartTreeSync(
      @RequestParam(name = "pid", required = false) String parentId,
      @RequestParam(name = "ids", required = false) String ids,
      @RequestParam(name = "primaryKey", required = false) String primaryKey) {
    Result<List<SysDepartTreeModel>> result = new Result<>();
    try {
      List<SysDepartTreeModel> list =
          sysDepartService.queryTreeListByPid(parentId, ids, primaryKey);
      result.setResult(list);
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
      result.setSuccess(false);
      result.setMessage(I18nUtil.getMessage("sysDepart.queryFailed"));
    }
    return result;
  }

  /**
   * Get all parent department ids for a department
   *
   * @param departId query by departId
   * @param orgCode query by orgCode; either departId or orgCode must be non-empty
   */
  @PostMapping("/queryAllParentId")
  public Result queryParentIds(
      @RequestParam(name = "departId", required = false) String departId,
      @RequestParam(name = "orgCode", required = false) String orgCode) {
    try {
      JSONObject data;
      if (oConvertUtils.isNotEmpty(departId)) {
        data = sysDepartService.queryAllParentIdByDepartId(departId);
      } else if (oConvertUtils.isNotEmpty(orgCode)) {
        data = sysDepartService.queryAllParentIdByOrgCode(orgCode);
      } else {
        return Result.error(I18nUtil.getMessage("sysDepart.departIdAndOrgCodeEmpty"));
      }
      return Result.OK(data);
    } catch (Exception e) {
      log.error("operation failed", e);
      return Result.error(e.getMessage());
    }
  }

  /**
   * Add new data: persist user-created department
   *
   * @param sysDepart
   * @return
   */
  @SaCheckPermission("system:depart:add")
  @RequestMapping(value = "/add", method = RequestMethod.POST)
  @CacheEvict(
      value = {CacheConstant.SYS_DEPARTS_CACHE, CacheConstant.SYS_DEPART_IDS_CACHE},
      allEntries = true)
  public Result<SysDepart> add(@RequestBody SysDepart sysDepart, HttpServletRequest request) {
    Result<SysDepart> result = new Result<SysDepart>();

    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
    String username = loginUser.getUsername();
    try {
      sysDepart.setCreateBy(username);
      sysDepartService.saveDepartData(sysDepart, username);
      // Clear department tree in memory
      // FindsDepartsChildrenUtil.clearSysDepartTreeList();
      // FindsDepartsChildrenUtil.clearDepartIdModel();
      result.success(I18nUtil.getMessage("sysDepart.addSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * Edit data: update partial department fields and persist
   *
   * @param sysDepart
   * @return
   */
  @SaCheckPermission("system:depart:edit")
  @PostMapping(
      value = "/edit",
      consumes = "application/json")
  @CacheEvict(
      value = {CacheConstant.SYS_DEPARTS_CACHE, CacheConstant.SYS_DEPART_IDS_CACHE},
      allEntries = true)
  public Result<SysDepart> edit(@RequestBody SysDepart sysDepart, HttpServletRequest request) {

    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");

    String username = loginUser.getUsername();
    sysDepart.setUpdateBy(username);
    Result<SysDepart> result = new Result<SysDepart>();
    SysDepart sysDepartEntity = sysDepartService.getById(sysDepart.getId());
    if (sysDepartEntity == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      boolean ok = sysDepartService.updateDepartDataById(sysDepart, username);
      // TODO What does returning false mean?
      if (ok) {
        // Clear department tree in memory
        // FindsDepartsChildrenUtil.clearSysDepartTreeList();
        // FindsDepartsChildrenUtil.clearDepartIdModel();
        result.success(I18nUtil.getMessage("sysDepart.modifySuccess"));
      }
    }
    return result;
  }

  /**
   * Delete by id
   *
   * @param id
   * @return
   */
  @SaCheckPermission("system:depart:delete")
  @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
  @CacheEvict(
      value = {CacheConstant.SYS_DEPARTS_CACHE, CacheConstant.SYS_DEPART_IDS_CACHE},
      allEntries = true)
  public Result<SysDepart> delete(@RequestParam(name = "id", required = true) String id) {

    Result<SysDepart> result = new Result<SysDepart>();
    SysDepart sysDepart = sysDepartService.getById(id);
    if (sysDepart == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      sysDepartService.deleteDepart(id);
      // Clear department tree in memory
      // FindsDepartsChildrenUtil.clearSysDepartTreeList();
      // FindsDepartsChildrenUtil.clearDepartIdModel();
      result.success(I18nUtil.getMessage("sysDepart.deleteSuccess"));
    }
    return result;
  }

  /**
   * Batch delete: delete related department data by multiple IDs from frontend request
   *
   * @param ids
   * @return
   */
  @SaCheckPermission("system:depart:deleteBatch")
  @RequestMapping(value = "/deleteBatch", method = RequestMethod.DELETE)
  @CacheEvict(
      value = {CacheConstant.SYS_DEPARTS_CACHE, CacheConstant.SYS_DEPART_IDS_CACHE},
      allEntries = true)
  public Result<SysDepart> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {

    Result<SysDepart> result = new Result<SysDepart>();
    if (ids == null || "".equals(ids.trim())) {
      return Result.error(400, I18nUtil.getMessage("sysDepart.paramNotRecognized"));
    } else {
      this.sysDepartService.deleteBatchWithChildren(Arrays.asList(ids.split(",")));
      result.success(I18nUtil.getMessage("sysDepart.deleteSuccess"));
    }
    return result;
  }

  /**
   * Load all department names as tree for add/edit page requests
   *
   * @return
   */
  @RequestMapping(value = "/queryIdTree", method = RequestMethod.POST)
  public Result<List<DepartIdModel>> queryIdTree() {
    //		Result<List<DepartIdModel>> result = new Result<List<DepartIdModel>>();
    //		List<DepartIdModel> idList;
    //		try {
    //			idList = FindsDepartsChildrenUtil.wrapDepartIdModel();
    //			if (idList != null && idList.size() > 0) {
    //				result.setResult(idList);
    //				result.setSuccess(true);
    //			} else {
    //				sysDepartService.queryTreeList();
    //				idList = FindsDepartsChildrenUtil.wrapDepartIdModel();
    //				result.setResult(idList);
    //				result.setSuccess(true);
    //			}
    //			return result;
    //		} catch (Exception e) {
    //			log.error("operation failed", e);
    //			result.setSuccess(false);
    //			return result;
    //		}
    Result<List<DepartIdModel>> result = new Result<>();
    try {
      List<DepartIdModel> list = sysDepartService.queryDepartIdTreeList();
      result.setResult(list);
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return result;
  }

  /**
   * Department search: fuzzy search departments by keyword
   *
   * @param keyWord
   * @return
   */
  @RequestMapping(value = "/searchBy", method = RequestMethod.POST)
  public Result<List<SysDepartTreeModel>> searchBy(
      @RequestParam(name = "keyWord", required = true) String keyWord,
      @RequestParam(name = "myDeptSearch", required = false) String myDeptSearch) {
    Result<List<SysDepartTreeModel>> result = new Result<List<SysDepartTreeModel>>();
    // Department query; when myDeptSearch=1, query my department; when login user is a superior, only data under departments they manage
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    String departIds = null;
    if (oConvertUtils.isNotEmpty(user.getUserIdentity())
        && user.getUserIdentity().equals(CommonConstant.USER_IDENTITY_2)) {
      departIds = user.getDepartIds();
    }
    List<SysDepartTreeModel> treeList =
        this.sysDepartService.searchByKeyWord(keyWord, myDeptSearch, departIds);
    if (treeList == null || treeList.size() == 0) {
      result.setSuccess(false);
      result.setMessage(I18nUtil.getMessage("sysDepart.noMatchingData"));
      return result;
    }
    result.setResult(treeList);
    return result;
  }

  /**
   * Export excel
   *
   * @param request
   */
  @PostMapping(value = "/exportXls")
  public ModelAndView exportXls(SysDepart sysDepart, HttpServletRequest request) {
    // ------------------------------------------------------------------------------------------------
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      sysDepart.setTenantId(oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------

    // update-begin---author:wangshuai---date:2023-10-19---for: [QQYUN-5482] Department import/export can use QiaoQiaoYun-style department path ---
    //// Step.1 Build query conditions
    // QueryWrapper<SysDepart> queryWrapper = QueryGenerator.initQueryWrapper(sysDepart,
    // request.getParameterMap());
    // Step.1 AutoPoi Export Excel
    ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
    // List<SysDepart> pageList = sysDepartService.list(queryWrapper);
    // Sort by dictionary order
    // Collections.sort(pageList, new Comparator<SysDepart>() {
    // @Override
    // public int compare(SysDepart arg0, SysDepart arg1) {
    // return arg0.getOrgCode().compareTo(arg1.getOrgCode());
    // }
    // });
    // step.2 assemble export data
    Integer tenantId = sysDepart == null ? null : sysDepart.getTenantId();
    List<SysDepartExportVo> sysDepartExportVos = sysDepartService.getExportDepart(tenantId);
    // Export file name
    mv.addObject(NormalExcelConstants.FILE_NAME, "DepartmentList");
    mv.addObject(NormalExcelConstants.CLASS, SysDepartExportVo.class);
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    mv.addObject(
        NormalExcelConstants.PARAMS,
        new ExportParams(
            "Import rules:\n"
                + "1. Title is row 3; department path and department name titles must not be modified or matching will fail; row 4 is data entry range;\n"
                + "2. Department path uses / separator; department name is the last segment of department path;\n"
                + "3. Create departments from top-level name; add extra row for siblings, e.g. R&D/R&D Dept I;R&D/R&D Dept II;\n"
                + "4. Custom department codes must follow rules to import. e.g. top-level code A01, child A01A01, sibling A01A02; fixed 3 chars, first A-Z, last two digits 0-99, incrementing;",
            "Exported by:" + user.getRealname(),
            "Export information"));
    mv.addObject(NormalExcelConstants.DATA_LIST, sysDepartExportVos);
    // update-end---author:wangshuai---date:2023-10-19---for: [QQYUN-5482] Department import/export can use QiaoQiaoYun-style department path ---

    return mv;
  }

  /**
   * Import data via Excel Department import option 1: compute parent department ID from org code to maintain hierarchy; Department import option 2:
   * You may also modify the program to import org codes directly without setting parent IDs first; after all rows are imported, run SQL to backfill parent IDs;
   *
   * @param request
   * @param response
   * @return
   */
  @SaCheckPermission("system:depart:importExcel")
  @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
  @CacheEvict(
      value = {CacheConstant.SYS_DEPARTS_CACHE, CacheConstant.SYS_DEPART_IDS_CACHE},
      allEntries = true)
  public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
    List<String> errorMessageList = new ArrayList<>();
    // List<SysDepart> listSysDeparts = null;
    List<SysDepartExportVo> listSysDeparts = null;
    Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
    for (Map.Entry<String, MultipartFile> entity : fileMap.entrySet()) {
      // Get uploaded file object
      MultipartFile file = entity.getValue();
      ImportParams params = new ImportParams();
      params.setTitleRows(2);
      params.setHeadRows(1);
      params.setNeedSave(true);
      try {
        // update-begin---author:wangshuai---date:2023-10-20---for: Comment out original department import logic ---
        //            	// orgCode encoding length
        //            	int codeLength = YouBianCodeUtil.ZHANWEI_LENGTH;
        //                listSysDeparts = ExcelImportUtil.importExcel(file.getInputStream(),
        // SysDepart.class, params);
        //                // Sort by length
        //                Collections.sort(listSysDeparts, new Comparator<SysDepart>() {
        //                    @Override
        //					public int compare(SysDepart arg0, SysDepart arg1) {
        //                    	return arg0.getOrgCode().length() - arg1.getOrgCode().length();
        //                    }
        //                });
        //
        //                int num = 0;
        //                for (SysDepart sysDepart : listSysDeparts) {
        //                	String orgCode = sysDepart.getOrgCode();
        //                	if(orgCode.length() > codeLength) {
        //                		String parentCode = orgCode.substring(0, orgCode.length()-codeLength);
        //                		QueryWrapper<SysDepart> queryWrapper = new QueryWrapper<SysDepart>();
        //                		queryWrapper.eq("org_code", parentCode);
        //                		try {
        //                		SysDepart parentDept = sysDepartService.getOne(queryWrapper);
        //                		if(!parentDept.equals(null)) {
        //							sysDepart.setParentId(parentDept.getId());
        //							//Mark parent department as non-leaf
        //							sysDepartService.updateIzLeaf(parentDept.getId(),CommonConstant.NOT_LEAF);
        //						} else {
        //							sysDepart.setParentId("");
        //						}
        //                		}catch (Exception e) {
        //                			// parentDept not found
        //                		}
        //                	}else{
        //                		sysDepart.setParentId("");
        //					}
        //                    //update-begin---author:liusq   Date:20210223  for: after batch department import, cannot append next-level departments
        // #2245------------
        //					sysDepart.setOrgType(sysDepart.getOrgCode().length()/codeLength+"");
        //                    //update-end---author:liusq   Date:20210223  for: after batch department import, cannot append next-level departments
        // #2245------------
        //					sysDepart.setDelFlag(CommonConstant.DEL_FLAG_0.toString());
        //                    //update-begin---author:wangshuai ---date:20220105  for: [JTC-363] department import
        // Import fails without org category; assign default ------------
        //					if(oConvertUtils.isEmpty(sysDepart.getOrgCategory())){
        //					    sysDepart.setOrgCategory("1");
        //                    }
        //                    //update-end---author:wangshuai ---date:20220105  for: [JTC-363] department import
        // Import fails without org category; assign default ------------
        //					ImportExcelUtil.importDateSaveOne(sysDepart, ISysDepartService.class,
        // errorMessageList, num, CommonConstant.SQL_INDEX_UNIQ_DEPART_ORG_CODE);
        //					num++;
        //                }
        // update-end---author:wangshuai---date:2023-10-20---for: Comment out original department import logic ---

        // update-begin---author:wangshuai---date:2023-10-19---for: [QQYUN-5482] Department import/export can use QiaoQiaoYun-style department path ---
        listSysDeparts =
            ExcelImportUtil.importExcel(file.getInputStream(), SysDepartExportVo.class, params);
        sysDepartService.importSysDepart(listSysDeparts, errorMessageList);
        // update-end---author:wangshuai---date:2023-10-19---for: [QQYUN-5482] Department import/export can use QiaoQiaoYun-style department path ---

        // Clear department cache
        Set<String> keys3 = RedisScanSupport.scanKeys(redisTemplate, CacheConstant.SYS_DEPARTS_CACHE + "*");
        Set<String> keys4 = RedisScanSupport.scanKeys(redisTemplate, CacheConstant.SYS_DEPART_IDS_CACHE + "*");
        // Cluster-safe: per-key DEL (a multi-key DEL across slots is rejected with CROSSSLOT)
        RedisScanSupport.deleteEach(redisTemplate, keys3);
        RedisScanSupport.deleteEach(redisTemplate, keys4);
        return ImportExcelUtil.imporReturnRes(
            errorMessageList.size(),
            listSysDeparts.size() - errorMessageList.size(),
            errorMessageList);
      } catch (Exception e) {
        log.error("operation failed", e);
        return Result.error(I18nUtil.getMessage("sysDepart.importFailed", new Object[]{e.getMessage()}));
      } finally {
        try {
          file.getInputStream().close();
        } catch (IOException e) {
          log.error("operation failed", e);
        }
      }
    }
    return Result.error(I18nUtil.getMessage("sysDepart.importFailed", new Object[]{""}));
  }

  /**
   * Query all department info
   *
   * @return
   */
  @PostMapping("listAll")
  public Result<List<SysDepart>> listAll(
      @RequestParam(name = "id", required = false) String id,
      HttpServletRequest request,
      HttpServletResponse response) {
    Result<List<SysDepart>> result = new Result<>();
    LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<SysDepart>();
    query.orderByAsc(SysDepart::getOrgCode);
    if (oConvertUtils.isNotEmpty(id)) {
      String[] arr = id.split(",");
      query.in(SysDepart::getId, arr);
    }
    List<SysDepart> ls = this.sysDepartService.list(query);
    result.setSuccess(true);
    result.setResult(ls);
    String etag = ETagResponseSupport.computeWeakEtag(ls);
    if (ETagResponseSupport.applyEtagOrNotModified(request, response, etag)) {
      result.setResult(null);
    }
    return result;
  }

  /**
   * Query all departments and return tree structure to frontend
   *
   * @return
   */
  @RequestMapping(value = "/queryTreeByKeyWord", method = RequestMethod.POST)
  public Result<Map<String, Object>> queryTreeByKeyWord(
      @RequestParam(name = "keyWord", required = false) String keyWord) {
    Result<Map<String, Object>> result = new Result<>();
    try {
      Map<String, Object> map = new HashMap(5);
      List<SysDepartTreeModel> list = sysDepartService.queryTreeByKeyWord(keyWord);
      // BykeyWordGetUserInfo
      LambdaQueryWrapper<SysUser> queryUser = new LambdaQueryWrapper<SysUser>();
      queryUser.eq(SysUser::getDelFlag, CommonConstant.DEL_FLAG_0);
      queryUser.and(
          i -> i.like(SysUser::getUsername, keyWord).or().like(SysUser::getRealname, keyWord));
      List<SysUser> sysUsers = this.sysUserService.list(queryUser);
      map.put("userList", sysUsers);
      map.put("departList", list);
      result.setResult(map);
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return result;
  }

  /**
   * ByDepartmentCodeGetDepartmentInfo
   *
   * @param orgCode
   * @return
   */
  @PostMapping("/getDepartName")
  public Result<SysDepart> getDepartName(@RequestParam(name = "orgCode") String orgCode) {
    Result<SysDepart> result = new Result<>();
    LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<>();
    query.eq(SysDepart::getOrgCode, orgCode);
    SysDepart sysDepart = sysDepartService.getOne(query);
    result.setSuccess(true);
    result.setResult(sysDepart);
    return result;
  }

  /**
   * ByDepartmentidGetUserInfo
   *
   * @param id
   * @return
   */
  @PostMapping("/getUsersByDepartId")
  public Result<List<SysUser>> getUsersByDepartId(@RequestParam(name = "id") String id) {
    Result<List<SysUser>> result = new Result<>();
    List<SysUser> sysUsers = sysUserDepartService.queryUserByDepId(id);
    // P0: strip sensitive columns before returning to frontend.
    if (sysUsers != null) {
      sysUsers.forEach(SysUser::clearSensitiveForResponse);
    }
    result.setSuccess(true);
    result.setResult(sysUsers);
    return result;
  }

  /**
   * @Function: Batch query by id
   *
   * @param deptIds
   * @return
   */
  @RequestMapping(value = "/queryByIds", method = RequestMethod.POST)
  public Result<Collection<SysDepart>> queryByIds(@RequestParam(name = "deptIds") String deptIds) {
    Result<Collection<SysDepart>> result = new Result<>();
    String[] ids = deptIds.split(",");
    Collection<String> idList = Arrays.asList(ids);
    Collection<SysDepart> deptList = sysDepartService.listByIds(idList);
    result.setSuccess(true);
    result.setResult(deptList);
    return result;
  }

  @PostMapping("/getMyDepartList")
  public Result<List<SysDepart>> getMyDepartList() {
    List<SysDepart> list = sysDepartService.getMyDepartList();
    return Result.ok(list);
  }

  /**
   * Async query department list
   *
   * @param parentId parent node for async load
   * @return
   */
  @RequestMapping(value = "/queryBookDepTreeSync", method = RequestMethod.POST)
  public Result<List<SysDepartTreeModel>> queryBookDepTreeSync(
      @RequestParam(name = "pid", required = false) String parentId,
      @RequestParam(name = "tenantId") Integer tenantId,
      @RequestParam(name = "departName", required = false) String departName) {
    Result<List<SysDepartTreeModel>> result = new Result<>();
    try {
      List<SysDepartTreeModel> list =
          sysDepartService.queryBookDepTreeSync(parentId, tenantId, departName);
      result.setResult(list);
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return result;
  }

  /**
   * Get users by department id and tenant id [low-code app: select department head]
   *
   * @param departId
   * @return
   */
  @PostMapping("/getUsersByDepartTenantId")
  public Result<List<SysUser>> getUsersByDepartTenantId(@RequestParam("departId") String departId) {
    int tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
    List<SysUser> sysUserList = sysUserDepartService.getUsersByDepartTenantId(departId, tenantId);
    // P0: strip sensitive columns before returning to frontend.
    if (sysUserList != null) {
      sysUserList.forEach(SysUser::clearSensitiveForResponse);
    }
    return Result.ok(sysUserList);
  }

  /**
   * Export Excel [low-code app: export departments]
   *
   * @param request
   */
  @PostMapping(value = "/appExportXls")
  public ModelAndView appExportXls(SysDepart sysDepart, HttpServletRequest request) {
    // Step.1 Build query conditions
    int tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
    ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
    List<ExportDepartVo> pageList = sysDepartService.getExcelDepart(tenantId);
    // Step.2 Export Excel via AutoPoi
    // Export file name
    mv.addObject(NormalExcelConstants.FILE_NAME, "DepartmentList");
    mv.addObject(NormalExcelConstants.CLASS, ExportDepartVo.class);
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    mv.addObject(
        NormalExcelConstants.PARAMS,
        new ExportParams("DepartmentListData", "Exported by:" + user.getRealname(), "Export information"));
    mv.addObject(NormalExcelConstants.DATA_LIST, pageList);
    return mv;
  }

  /**
   * Import Excel [low-code app: export departments]
   *
   * @param request
   */
  @RequestMapping(value = "/appImportExcel", method = RequestMethod.POST)
  @CacheEvict(
      value = {CacheConstant.SYS_DEPARTS_CACHE, CacheConstant.SYS_DEPART_IDS_CACHE},
      allEntries = true)
  public Result<?> appImportExcel(HttpServletRequest request, HttpServletResponse response) {
    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
    List<String> errorMessageList = new ArrayList<>();
    List<ExportDepartVo> listSysDeparts = null;
    Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
    for (Map.Entry<String, MultipartFile> entity : fileMap.entrySet()) {
      // Get uploaded file object
      MultipartFile file = entity.getValue();
      ImportParams params = new ImportParams();
      params.setTitleRows(2);
      params.setHeadRows(1);
      params.setNeedSave(true);
      try {
        listSysDeparts =
            ExcelImportUtil.importExcel(file.getInputStream(), ExportDepartVo.class, params);
        sysDepartService.importExcel(listSysDeparts, errorMessageList);
        // Clear department cache
        Set<String> keys3 = RedisScanSupport.scanKeys(redisTemplate, CacheConstant.SYS_DEPARTS_CACHE + "*");
        Set<String> keys4 = RedisScanSupport.scanKeys(redisTemplate, CacheConstant.SYS_DEPART_IDS_CACHE + "*");
        // Cluster-safe: per-key DEL (a multi-key DEL across slots is rejected with CROSSSLOT)
        RedisScanSupport.deleteEach(redisTemplate, keys3);
        RedisScanSupport.deleteEach(redisTemplate, keys4);
        return ImportExcelUtil.imporReturnRes(
            errorMessageList.size(),
            listSysDeparts.size() - errorMessageList.size(),
            errorMessageList);
      } catch (Exception e) {
        log.error("operation failed", e);
        return Result.error(I18nUtil.getMessage("sysDepart.importFailed", new Object[]{e.getMessage()}));
      } finally {
        try {
          file.getInputStream().close();
        } catch (IOException e) {
          log.error("operation failed", e);
        }
      }
    }
    return Result.error(I18nUtil.getMessage("sysDepart.importFailed", new Object[]{""}));
  }
}
