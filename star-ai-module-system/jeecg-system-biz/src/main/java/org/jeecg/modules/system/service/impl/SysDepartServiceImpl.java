package org.jeecg.modules.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.netty.util.internal.StringUtil;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.FillRuleConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.security.C3FieldReflectionInspector;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.security.LedgerWriteGuard;
import org.jeecg.common.util.FillRuleUtil;
import org.jeecg.common.util.ImportExcelUtil;
import org.jeecg.common.util.YouBianCodeUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.system.entity.*;
import org.jeecg.modules.system.mapper.*;
import org.jeecg.modules.system.model.DepartIdModel;
import org.jeecg.modules.system.model.SysDepartTreeModel;
import org.jeecg.modules.system.service.ISysDepartService;
import org.jeecg.modules.system.util.FindsDepartsChildrenUtil;
import org.jeecg.modules.system.vo.SysDepartExportVo;
import org.jeecg.modules.system.vo.lowapp.ExportDepartVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Department table service implementation class
 *
 * <p>@Author Steve @Since 2019-01-22
 */
@Service
public class SysDepartServiceImpl extends ServiceImpl<SysDepartMapper, SysDepart>
    implements ISysDepartService {

  private static final Set<String> REQUIRED_FIELDS_FOR_THIS_TABLE =
      Set.of("departName", "departNameEn", "departNameAbbr", "departOrder", "description", "orgCategory", "orgType", "orgCode", "mobile", "fax", "address", "memo", "status", "updateBy", "updateTime");

  @Autowired private SysUserDepartMapper userDepartMapper;
  @Autowired private SysUserMapper sysUserMapper;
  @Autowired private SysDepartMapper departMapper;

  @Override
  public List<SysDepartTreeModel> queryMyDeptTreeList(String departIds) {
    // Get responsible departments by department id
    LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<SysDepart>();
    String[] codeArr = this.getMyDeptParentOrgCode(departIds);
    // update-begin---author:wangshuai---date:2023-12-01---for: [QQYUN-7320] empty dept query NPE---
    if (ArrayUtil.isEmpty(codeArr)) {
      return null;
    }
    // update-end---author:wangshuai---date:2023-12-01---for: [QQYUN-7320] empty dept query NPE---
    for (int i = 0; i < codeArr.length; i++) {
      query.or().likeRight(SysDepart::getOrgCode, codeArr[i]);
    }
    query.eq(SysDepart::getDelFlag, CommonConstant.DEL_FLAG_0.toString());

    // ------------------------------------------------------------------------------------------------
    // Whether to enable SASS control of the system management module
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      query.eq(SysDepart::getTenantId, oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------

    query.orderByAsc(SysDepart::getDepartOrder);
    // Set parent ParentId to null
    List<SysDepart> listDepts = this.list(query);
    for (int i = 0; i < codeArr.length; i++) {
      for (SysDepart dept : listDepts) {
        if (dept.getOrgCode().equals(codeArr[i])) {
          dept.setParentId(null);
        }
      }
    }
    // Call wrapTreeDataToTreeList to build tree data
    List<SysDepartTreeModel> listResult =
        FindsDepartsChildrenUtil.wrapTreeDataToTreeList(listDepts);
    return listResult;
  }

  /** queryTreeList: load all departments as a tree for the front-end */
  @Override
  // @Cacheable(value = CacheConstant.SYS_DEPARTS_CACHE)
  public List<SysDepartTreeModel> queryTreeList() {
    LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<SysDepart>();
    // ------------------------------------------------------------------------------------------------
    // Whether to enable multi-tenant data isolation for the system management module [SAAS multi-tenant mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      query.eq(SysDepart::getTenantId, oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------
    query.eq(SysDepart::getDelFlag, CommonConstant.DEL_FLAG_0.toString());
    query.orderByAsc(SysDepart::getDepartOrder);
    List<SysDepart> list = this.list(query);
    // update-begin---author:wangshuai ---date:20220307  for: [JTC-119] set department manager under department menu; no handling needed when creating user
    // Set user id and let it be displayed in the front desk
    this.setUserIdsByDepList(list);
    // update-begin---author:wangshuai ---date:20220307  for: [JTC-119] set department manager under department menu; no handling needed when creating user
    // Call wrapTreeDataToTreeList to build tree data
    List<SysDepartTreeModel> listResult = FindsDepartsChildrenUtil.wrapTreeDataToTreeList(list);
    return listResult;
  }

  /** queryTreeList: query by department ids for front-end display */
  @Override
  public List<SysDepartTreeModel> queryTreeList(String ids) {
    List<SysDepartTreeModel> listResult = new ArrayList<>();
    LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<SysDepart>();
    query.eq(SysDepart::getDelFlag, CommonConstant.DEL_FLAG_0.toString());
    if (oConvertUtils.isNotEmpty(ids)) {
      query.in(true, SysDepart::getId, ids.split(","));
    }
    // ------------------------------------------------------------------------------------------------
    // Whether to enable multi-tenant data isolation for the system management module [SAAS multi-tenant mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      query.eq(SysDepart::getTenantId, oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------
    query.orderByAsc(SysDepart::getDepartOrder);
    List<SysDepart> list = this.list(query);
    for (SysDepart depart : list) {
      listResult.add(new SysDepartTreeModel(depart));
    }
    return listResult;
  }

  // @Cacheable(value = CacheConstant.SYS_DEPART_IDS_CACHE)
  @Override
  public List<DepartIdModel> queryDepartIdTreeList() {
    LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<SysDepart>();
    query.eq(SysDepart::getDelFlag, CommonConstant.DEL_FLAG_0.toString());
    // ------------------------------------------------------------------------------------------------
    // Whether to enable multi-tenant data isolation for the system management module [SAAS multi-tenant mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      query.eq(SysDepart::getTenantId, oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------
    query.orderByAsc(SysDepart::getDepartOrder);
    List<SysDepart> list = this.list(query);
    // Call wrapTreeDataToTreeList to build tree data
    List<DepartIdModel> listResult = FindsDepartsChildrenUtil.wrapTreeDataToDepartIdTreeList(list);
    return listResult;
  }

  /** saveDepartData (add): persist department created on the page */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void saveDepartData(SysDepart sysDepart, String username) {
    if (sysDepart != null && username != null) {
      // update-begin---author:wangshuai ---date:20230216  for: [QQYUN-4163] add has-child flag on department------------
      if (oConvertUtils.isEmpty(sysDepart.getParentId())) {
        sysDepart.setParentId("");
      } else {
        // Mark parent department as non-leaf
        departMapper.setMainLeaf(sysDepart.getParentId(), CommonConstant.NOT_LEAF);
      }
      // update-end---author:wangshuai ---date:20230216  for: [QQYUN-4163] add has-child flag on department------------
      // String s = UUID.randomUUID().toString().replace("-", "");
      sysDepart.setId(IdWorker.getIdStr(sysDepart));
      // If parent id exists it is not top-level; otherwise it is top-level
      // Get parent id
      String parentId = sysDepart.getParentId();
      // update-begin--Author:baihailong  Date:20191209 for: make department code generator a shared config
      JSONObject formData = new JSONObject();
      formData.put("parentId", parentId);
      String[] codeArray = (String[]) FillRuleUtil.executeRule(FillRuleConstant.DEPART, formData);
      // update-end--Author:baihailong  Date:20191209 for: make department code generator a shared config
      sysDepart.setOrgCode(codeArray[0]);
      String orgType = codeArray[1];
      sysDepart.setOrgType(String.valueOf(orgType));
      sysDepart.setCreateTime(new Date());
      sysDepart.setDelFlag(CommonConstant.DEL_FLAG_0.toString());
      // Newly added department is a leaf
      sysDepart.setIzLeaf(CommonConstant.IS_LEAF);
      // [QQYUN-7172] DB default-value compatibility
      if (oConvertUtils.isEmpty(sysDepart.getOrgCategory())) {
        if (oConvertUtils.isEmpty(sysDepart.getParentId())) {
          sysDepart.setOrgCategory("1");
        } else {
          sysDepart.setOrgCategory("2");
        }
      }
      this.save(sysDepart);
      // update-begin---author:wangshuai ---date:20220307  for: [JTC-119] set department manager under department menu; no handling needed when creating user
      // When adding a department, also set responsible departments
      if (oConvertUtils.isNotEmpty(sysDepart.getDirectorUserIds())) {
        this.addDepartByUserIds(sysDepart, sysDepart.getDirectorUserIds());
      }
      // update-end---author:wangshuai ---date:20220307  for: [JTC-119] set dept leaders under dept menu; skip on user create
    }
  }

  /**
   * Helper for saveDepartData: generate dept code/type (deprecated logic)
   *
   * @deprecated
   * @param parentId
   * @return
   */
  private String[] generateOrgCode(String parentId) {
    // update-begin--Author:Steve  Date:20190201 for: org structure add-data code adjustments
    LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<SysDepart>();
    LambdaQueryWrapper<SysDepart> query1 = new LambdaQueryWrapper<SysDepart>();
    String[] strArray = new String[2];
    // Create a List collection to store all SysDepart objects returned by the query
    List<SysDepart> departList = new ArrayList<>();
    // Define new encoding string
    String newOrgCode = "";
    // Define old encoding string
    String oldOrgCode = "";
    // Define department type
    String orgType = "";
    // If it is the highest level, query the org_code of the same level, call the tool class to generate the code and return
    if (StringUtil.isNullOrEmpty(parentId)) {
      // Line to determine whether the table in the database is empty, if empty, return to the initial code directly
      query1.eq(SysDepart::getParentId, "").or().isNull(SysDepart::getParentId);
      query1.orderByDesc(SysDepart::getOrgCode);
      departList = this.list(query1);
      if (departList == null || departList.size() == 0) {
        strArray[0] = YouBianCodeUtil.getNextYouBianCode(null);
        strArray[1] = "1";
        return strArray;
      } else {
        SysDepart depart = departList.get(0);
        oldOrgCode = depart.getOrgCode();
        orgType = depart.getOrgType();
        newOrgCode = YouBianCodeUtil.getNextYouBianCode(oldOrgCode);
      }
    } else { // Otherwise, all departments at the same level are queried. After obtaining the results, there are two situations: those with the same level and those without the same level.
      // Build query conditions for siblings
      query.eq(SysDepart::getParentId, parentId);
      // Sort descending
      query.orderByDesc(SysDepart::getOrgCode);
      // Query sibling departments
      List<SysDepart> parentList = this.list(query);
      // Query the parent department
      SysDepart depart = this.getById(parentId);
      // Get the Code of the parent department
      String parentCode = depart.getOrgCode();
      // Calculate the type of the current department based on the parent department type
      orgType = String.valueOf(Integer.valueOf(depart.getOrgType()) + 1);
      // Handle the situation where the same level department is null
      if (parentList == null || parentList.size() == 0) {
        // Directly generate the current department code and return
        newOrgCode = YouBianCodeUtil.getSubYouBianCode(parentCode, null);
      } else { // Handle situations where there are departments at the same level
        // Obtain the code of the same level department and use the tool class
        String subCode = parentList.get(0).getOrgCode();
        // Returns the generated current department code
        newOrgCode = YouBianCodeUtil.getSubYouBianCode(parentCode, subCode);
      }
    }
    // Returns an array that finally encapsulates the department code and department type
    strArray[0] = newOrgCode;
    strArray[1] = orgType;
    return strArray;
    // update-end--Author:Steve  Date:20190201 for: org structure add-data code adjustments
  }

  /** removeDepartDataById (delete): delete department by id */
  /*
   * @Override
   *
   * @Transactional public boolean removeDepartDataById(String id) {
   * System.out.println("id to delete =============================>>>>>"+id); boolean
   * flag = this.removeById(id); return flag; }
   */

  /** updateDepartDataById (edit): update department by primary key */
  // C3-WHITELIST-CTRLLED: field-level only (not updateById whole entity)
  @Override
  @Transactional(rollbackFor = Exception.class)
  public Boolean updateDepartDataById(SysDepart sysDepart, String username) {
    if (sysDepart != null && username != null) {
      Date now = new Date();
      LambdaUpdateWrapper<SysDepart> uw = Wrappers.lambdaUpdate();
      uw.eq(SysDepart::getId, sysDepart.getId())
        .set(SysDepart::getParentId, sysDepart.getParentId())
        .set(SysDepart::getDepartName, sysDepart.getDepartName())
        .set(SysDepart::getDepartNameEn, sysDepart.getDepartNameEn())
        .set(SysDepart::getDepartNameAbbr, sysDepart.getDepartNameAbbr())
        .set(SysDepart::getDepartOrder, sysDepart.getDepartOrder())
        .set(SysDepart::getDescription, sysDepart.getDescription())
        .set(SysDepart::getOrgCategory, sysDepart.getOrgCategory())
        .set(SysDepart::getOrgType, sysDepart.getOrgType())
        .set(SysDepart::getOrgCode, sysDepart.getOrgCode())
        .set(SysDepart::getMobile, sysDepart.getMobile())
        .set(SysDepart::getFax, sysDepart.getFax())
        .set(SysDepart::getAddress, sysDepart.getAddress())
        .set(SysDepart::getMemo, sysDepart.getMemo())
        .set(SysDepart::getStatus, sysDepart.getStatus())
        .set(SysDepart::getQywxIdentifier, sysDepart.getQywxIdentifier())
        .set(SysDepart::getDingIdentifier, sysDepart.getDingIdentifier())
        .set(SysDepart::getIzLeaf, sysDepart.getIzLeaf())
        .set(SysDepart::getUpdateBy, username)
        .set(SysDepart::getUpdateTime, now);
      Set<String> actual = C3FieldReflectionInspector.extractSetFieldNames(uw);
      if (!actual.containsAll(REQUIRED_FIELDS_FOR_THIS_TABLE)) { Set<String> miss = new HashSet<>(REQUIRED_FIELDS_FOR_THIS_TABLE); miss.removeAll(actual); throw new JeecgBootException("C3 BLOCK MISSING_WRITE_FIELDS table=sys_depart missing=" + miss); }
      // LedgerWriteGuard.assertLambdaUpdateWrapperFields("sys_depart", uw, "update_time", "update_by"); // table not whitelisted in LedgerWriteGuard
      baseMapper.update(null, uw);
      // update-begin---author:wangshuai ---date:20220307  for: [JTC-119] set department manager under department menu; no handling needed when creating user
      // When editing department, update responsible departments
      this.updateChargeDepart(sysDepart);
      // update-begin---author:wangshuai ---date:20220307  for: [JTC-119] set department manager under department menu; no handling needed when creating user
      return true;
    } else {
      return false;
    }
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void deleteBatchWithChildren(List<String> ids) {
    // Store child ids
    List<String> idList = new ArrayList<String>();
    // Store parent ids
    List<String> parentIdList = new ArrayList<>();
    for (String id : ids) {
      idList.add(id);
      // This step deletes children
      this.checkChildrenExists(id, idList);
      // update-begin---author:wangshuai ---date:20230712 for: [QQYUN-5757] Departments are not correctly set as leaf nodes when deleting departments in batches
      // ------------
      SysDepart depart = this.getDepartById(id);
      if (oConvertUtils.isNotEmpty(depart.getParentId())) {
        if (!parentIdList.contains(depart.getParentId())) {
          parentIdList.add(depart.getParentId());
        }
      }
      // update-end---author:wangshuai ---date:20230712  for: [QQYUN-5757] batch delete did not mark leaf correctly
      // ------------
    }
    this.removeByIds(idList);
    // update-begin---author:wangshuai ---date:20230712 for: [QQYUN-5757] Departments are not correctly set as leaf nodes when deleting departments in batches
    // ------------
    // Capture parent id before delete, otherwise it stays empty
    this.setParentDepartIzLeaf(parentIdList);
    // update-end---author:wangshuai ---date:20230712  for: [QQYUN-5757] batch delete did not mark leaf correctly ------------
    // Delete user-department relationship based on department id
    userDepartMapper.delete(
        new LambdaQueryWrapper<SysUserDepart>().in(SysUserDepart::getDepId, idList));
    // NOTE: SysDepartRole/SysDepartPermission/SysDepartRolePermission/SysDepartRoleUser cleaned
  }

  @Override
  public List<String> getSubDepIdsByDepId(String departId) {
    return this.baseMapper.getSubDepIdsByDepId(departId);
  }

  @Override
  public List<String> getMySubDepIdsByDepId(String departIds) {
    // Get responsible departments by department id
    String[] codeArr = this.getMyDeptParentOrgCode(departIds);
    if (codeArr == null || codeArr.length == 0) {
      return null;
    }
    return this.baseMapper.getSubDepIdsByOrgCodes(codeArr);
  }

  /** Search related department data based on keywords */
  @Override
  public List<SysDepartTreeModel> searchByKeyWord(
      String keyWord, String myDeptSearch, String departIds) {
    LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<SysDepart>();
    List<SysDepartTreeModel> newList = new ArrayList<>();
    // When myDeptSearch is set, search only responsible departments
    if (!StringUtil.isNullOrEmpty(myDeptSearch)) {
      // Empty departIds means normal user or no managed departments
      if (StringUtil.isNullOrEmpty(departIds)) {
        return newList;
      }
      // Get responsible departments by department id
      String[] codeArr = this.getMyDeptParentOrgCode(departIds);
      // update-begin-author:taoyan date:20220104 for:/issues/3311
      // When the user belongs to two departments, and the two departments have no superior-subordinate relationship, my department-department name query condition fuzzy search fails!
      if (codeArr != null && codeArr.length > 0) {
        query.nested(
            i -> {
              for (String s : codeArr) {
                i.or().likeRight(SysDepart::getOrgCode, s);
              }
            });
      }
      // update-end-author:taoyan date:20220104 for:/issues/3311
      // When the user belongs to two departments, and the two departments have no superior-subordinate relationship, my department-department name query condition fuzzy search fails!
      query.eq(SysDepart::getDelFlag, CommonConstant.DEL_FLAG_0.toString());
    }
    query.like(SysDepart::getDepartName, keyWord);
    // update-begin--Author:huangzhilin  Date:20140417 for: [bugfree] org search display optimization--------------------
    SysDepartTreeModel model = new SysDepartTreeModel();
    List<SysDepart> departList = this.list(query);
    if (departList.size() > 0) {
      for (SysDepart depart : departList) {
        model = new SysDepartTreeModel(depart);
        model.setChildren(null);
        // update-end--Author:huangzhilin  Date:20140417
        // for: [bugfree] org search display optimization----------------------
        newList.add(model);
      }
      return newList;
    }
    return null;
  }

  /** Delete department by id and any child departments */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean delete(String id) {
    List<String> idList = new ArrayList<>();
    idList.add(id);
    this.checkChildrenExists(id, idList);
    // Clear department-tree memory cache
    // FindsDepartsChildrenUtil.clearDepartIdModel();
    boolean ok = this.removeByIds(idList);
    // Delete user-department relationship based on department id
    userDepartMapper.delete(
        new LambdaQueryWrapper<SysUserDepart>().in(SysUserDepart::getDepId, idList));
    // NOTE: SysDepartRole/SysDepartPermission/SysDepartRolePermission/SysDepartRoleUser cleaned
    return ok;
  }

  /**
   * delete method call
   *
   * @param id
   * @param idList
   */
  private void checkChildrenExists(String id, List<String> idList) {
    LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<SysDepart>();
    query.eq(SysDepart::getParentId, id);
    List<SysDepart> departList = this.list(query);
    if (departList != null && departList.size() > 0) {
      for (SysDepart depart : departList) {
        idList.add(depart.getId());
        this.checkChildrenExists(depart.getId(), idList);
      }
    }
  }

  @Override
  public List<SysDepart> queryUserDeparts(String userId) {
    return baseMapper.queryUserDeparts(userId);
  }

  @Override
  public List<SysDepart> queryDepartsByUsername(String username) {
    return baseMapper.queryDepartsByUsername(username);
  }

  @Override
  public List<String> queryDepartsByUserId(String userId) {
    List<String> list = baseMapper.queryDepartsByUserId(userId);
    return list;
  }

  /**
   * Get parent department codes from user's responsible department ids
   *
   * @param departIds
   * @return
   */
  private String[] getMyDeptParentOrgCode(String departIds) {
    // Query responsible departments by department id
    LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<SysDepart>();
    query.eq(SysDepart::getDelFlag, CommonConstant.DEL_FLAG_0.toString());
    if (oConvertUtils.isNotEmpty(departIds)) {
      query.in(SysDepart::getId, Arrays.asList(departIds.split(",")));
    }

    // ------------------------------------------------------------------------------------------------
    // Whether to enable multi-tenant data isolation for the system management module [SAAS multi-tenant mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      query.eq(SysDepart::getTenantId, oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------
    query.orderByAsc(SysDepart::getOrgCode);
    List<SysDepart> list = this.list(query);
    // Find root department
    if (list == null || list.size() == 0) {
      return null;
    }
    String orgCode = this.getMyDeptParentNode(list);
    String[] codeArr = orgCode.split(",");
    return codeArr;
  }

  /**
   * Get parent of responsible department
   *
   * @param list
   * @return
   */
  private String getMyDeptParentNode(List<SysDepart> list) {
    Map<String, String> map = new HashMap(5);
    // 1. Group by same company first
    for (SysDepart dept : list) {
      String code = dept.getOrgCode().substring(0, 3);
      if (map.containsKey(code)) {
        String mapCode = map.get(code) + "," + dept.getOrgCode();
        map.put(code, mapCode);
      } else {
        map.put(code, dept.getOrgCode());
      }
    }
    StringBuffer parentOrgCode = new StringBuffer();
    // 2. Get root node within the same company
    for (String str : map.values()) {
      String[] arrStr = str.split(",");
      parentOrgCode.append(",").append(this.getMinLengthNode(arrStr));
    }
    return parentOrgCode.substring(1);
  }

  /**
   * Get department with shortest org code in the same company
   *
   * @param str
   * @return
   */
  private String getMinLengthNode(String[] str) {
    int min = str[0].length();
    StringBuilder orgCodeBuilder = new StringBuilder(str[0]);
    for (int i = 1; i < str.length; i++) {
      if (str[i].length() <= min) {
        min = str[i].length();
        orgCodeBuilder.append(SymbolConstant.COMMA).append(str[i]);
      }
    }
    return orgCodeBuilder.toString();
  }

  /**
   * Get department tree filtered by keyword
   *
   * @param keyWord
   * @return
   */
  @Override
  public List<SysDepartTreeModel> queryTreeByKeyWord(String keyWord) {
    LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<SysDepart>();
    query.eq(SysDepart::getDelFlag, CommonConstant.DEL_FLAG_0.toString());
    query.orderByAsc(SysDepart::getDepartOrder);
    List<SysDepart> list = this.list(query);
    // Call wrapTreeDataToTreeList to build tree data
    List<SysDepartTreeModel> listResult = FindsDepartsChildrenUtil.wrapTreeDataToTreeList(list);
    List<SysDepartTreeModel> treelist = new ArrayList<>();
    if (StringUtils.isNotBlank(keyWord)) {
      this.getTreeByKeyWord(keyWord, listResult, treelist);
    } else {
      return listResult;
    }
    return treelist;
  }

  /**
   * Query department tree by parentId
   *
   * @param parentId
   * @param ids passed by front-end for display
   * @param primaryKey primary key field (id or orgCode)
   * @return
   */
  @Override
  public List<SysDepartTreeModel> queryTreeListByPid(
      String parentId, String ids, String primaryKey) {
    Consumer<LambdaQueryWrapper<SysDepart>> square =
        i -> {
          if (oConvertUtils.isNotEmpty(ids)) {
            if (CommonConstant.DEPART_KEY_ORG_CODE.equals(primaryKey)) {
              i.in(SysDepart::getOrgCode, ids.split(SymbolConstant.COMMA));
            } else {
              i.in(SysDepart::getId, ids.split(SymbolConstant.COMMA));
            }
          } else {
            if (oConvertUtils.isEmpty(parentId)) {
              i.and(
                  q ->
                      q.isNull(true, SysDepart::getParentId)
                          .or()
                          .eq(true, SysDepart::getParentId, ""));
            } else {
              i.eq(true, SysDepart::getParentId, parentId);
            }
          }
        };
    LambdaQueryWrapper<SysDepart> lqw = new LambdaQueryWrapper<>();
    // ------------------------------------------------------------------------------------------------
    // Whether to enable SASS control of the system management module
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      lqw.eq(SysDepart::getTenantId, oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------
    lqw.eq(true, SysDepart::getDelFlag, CommonConstant.DEL_FLAG_0.toString());
    lqw.func(square);
    // update-begin---author:wangshuai ---date:20220527
    // for: [VUEN-1143] sort wrong on vue2/3; should be ascending------------
    lqw.orderByAsc(SysDepart::getDepartOrder);
    // update-end---author:wangshuai ---date:20220527
    // for: [VUEN-1143] sort wrong on vue2/3; should be ascending--------------
    List<SysDepart> list = list(lqw);
    // update-begin---author:wangshuai ---date:20220316  for: [JTC-119] set dept leaders under dept menu; skip on user create
    // Set user id and let it be displayed in the front desk
    this.setUserIdsByDepList(list);
    // update-end---author:wangshuai ---date:20220316  for: [JTC-119] set dept leaders under dept menu; skip on user create
    List<SysDepartTreeModel> records = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      SysDepart depart = list.get(i);
      SysDepartTreeModel treeModel = new SysDepartTreeModel(depart);
      // TODO Async tree key appends __+timestamp so each expand refreshes
      // treeModel.setKey(treeModel.getKey()+"__"+System.currentTimeMillis());
      records.add(treeModel);
    }
    return records;
  }

  @Override
  public JSONObject queryAllParentIdByDepartId(String departId) {
    JSONObject result = new JSONObject();
    for (String id : departId.split(SymbolConstant.COMMA)) {
      JSONObject all = this.queryAllParentId("id", id);
      result.put(id, all);
    }
    return result;
  }

  @Override
  public JSONObject queryAllParentIdByOrgCode(String orgCode) {
    JSONObject result = new JSONObject();
    for (String code : orgCode.split(SymbolConstant.COMMA)) {
      JSONObject all = this.queryAllParentId("org_code", code);
      result.put(code, all);
    }
    return result;
  }

  /**
   * Query all parent ids for a department
   *
   * @param fieldName field name
   * @param value value
   */
  private JSONObject queryAllParentId(String fieldName, String value) {
    JSONObject data = new JSONObject();
    // Ordered parent-id collection
    data.put("parentIds", new JSONArray());
    // Parent department data map: key=id, value=entity
    data.put("parentMap", new JSONObject());
    this.queryAllParentIdRecursion(fieldName, value, data);
    return data;
  }

  /** Recursively call parent-department query */
  private void queryAllParentIdRecursion(String fieldName, String value, JSONObject data) {
    QueryWrapper<SysDepart> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq(fieldName, value);
    SysDepart depart = super.getOne(queryWrapper);
    if (depart != null) {
      data.getJSONArray("parentIds").add(0, depart.getId());
      data.getJSONObject("parentMap").put(depart.getId(), depart);
      if (oConvertUtils.isNotEmpty(depart.getParentId())) {
        this.queryAllParentIdRecursion("id", depart.getParentId(), data);
      }
    }
  }

  @Override
  public SysDepart queryCompByOrgCode(String orgCode) {
    int length = YouBianCodeUtil.ZHANWEI_LENGTH;
    String compyOrgCode = orgCode.substring(0, length);
    return this.baseMapper.queryCompByOrgCode(compyOrgCode);
  }

  /**
   * Query child departments by id
   *
   * @param pid
   * @return
   */
  @Override
  public List<SysDepart> queryDeptByPid(String pid) {
    return this.baseMapper.queryDeptByPid(pid);
  }

  /**
   * Filter departments by keyword
   *
   * @param keyWord
   * @return
   */
  public void getTreeByKeyWord(
      String keyWord, List<SysDepartTreeModel> allResult, List<SysDepartTreeModel> newResult) {
    for (SysDepartTreeModel model : allResult) {
      if (model.getDepartName().contains(keyWord)) {
        newResult.add(model);
        continue;
      } else if (model.getChildren() != null) {
        getTreeByKeyWord(keyWord, model.getChildren(), newResult);
      }
    }
  }

  // update-begin---author:wangshuai ---date:20200308
  // for: [JTC-119] Set the department head under the department management menu, add a method to add the department head, delete the responsible department head, and inquire the corresponding person of the department
  /**
   * Set responsible departments by user id
   *
   * @param sysDepart SysDepart entity
   * @param userIds responsible user ids
   */
  public void addDepartByUserIds(SysDepart sysDepart, String userIds) {
    // Get department id and save onto user
    String departId = sysDepart.getId();
    // Iterate user ids
    String[] userIdArray = userIds.split(",");
    for (String userId : userIdArray) {
      // P2 (CodePlan §4.3.4): C6-safe load — only depart_ids/user_identity are mutated below.
      SysUser sysUser = sysUserMapper.selectInfoById(userId);
      // If department id is set, concatenate it
      if (oConvertUtils.isNotEmpty(sysUser.getDepartIds())) {
        if (!sysUser.getDepartIds().contains(departId)) {
          sysUser.setDepartIds(sysUser.getDepartIds() + "," + departId);
        }
      } else {
        sysUser.setDepartIds(departId);
      }
      // Set identity as leader
      sysUser.setUserIdentity(CommonConstant.USER_IDENTITY_2);
      // Update user table
      sysUserMapper.updateById(sysUser);
      // Check whether user already has the department
      List<SysUserDepart> userDepartList = userDepartMapper.getUserDepartByUid(userId);
      boolean isExistDepId =
          userDepartList.stream().anyMatch(item -> departId.equals(item.getDepId()));
      // If missing, set belonging department
      if (!isExistDepId) {
        userDepartMapper.insert(new SysUserDepart(userId, departId));
      }
    }
  }

  /**
   * Update user's responsible departments
   *
   * @param sysDepart SysDepart object
   */
  private void updateChargeDepart(SysDepart sysDepart) {
    // New user ids
    String directorIds = sysDepart.getDirectorUserIds();
    // Old user ids (already in DB)
    String oldDirectorIds = sysDepart.getOldDirectorUserIds();
    String departId = sysDepart.getId();
    // If user ids empty, clear responsible-department ids on users
    if (oConvertUtils.isEmpty(directorIds)) {
      this.deleteChargeDepId(departId, null);
    } else if (oConvertUtils.isNotEmpty(directorIds) && oConvertUtils.isEmpty(oldDirectorIds)) {
      // If new user ids set but previous responsible users were empty
      this.addDepartByUserIds(sysDepart, directorIds);
    } else {
      // If both are non-empty, compare and add or delete
      // Diff new vs old responsible user ids and delete removals
      List<String> userIdList =
          Arrays.stream(oldDirectorIds.split(","))
              .filter(item -> !directorIds.contains(item))
              .collect(Collectors.toList());
      for (String userId : userIdList) {
        this.deleteChargeDepId(departId, userId);
      }
      // Diff old vs new responsible user ids and insert additions
      String addUserIds =
          Arrays.stream(directorIds.split(","))
              .filter(item -> !oldDirectorIds.contains(item))
              .collect(Collectors.joining(","));
      if (oConvertUtils.isNotEmpty(addUserIds)) {
        this.addDepartByUserIds(sysDepart, addUserIds);
      }
    }
  }

  /**
   * Remove user's responsible departments
   *
   * @param departId department id
   * @param userId User id
   */
  private void deleteChargeDepId(String departId, String userId) {
    // Load responsible user ids first (comma-joined on department)
    LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
    query.like(SysUser::getDepartIds, departId);
    // When deleting all, user id may be absent
    if (oConvertUtils.isNotEmpty(userId)) {
      query.eq(SysUser::getId, userId);
    }
    List<SysUser> userList = sysUserMapper.selectList(query);
    for (SysUser sysUser : userList) {
      // Remove department ids that no longer exist
      String departIds = sysUser.getDepartIds();
      List<String> list = new ArrayList<>(Arrays.asList(departIds.split(",")));
      list.remove(departId);
      // After deletion, join remaining ids with commas and update
      String newDepartIds = String.join(",", list);
      sysUser.setDepartIds(newDepartIds);
      sysUserMapper.updateById(sysUser);
    }
  }

  /**
   * Attach user ids onto departments for front-end display
   *
   * @param departList department list
   */
  private void setUserIdsByDepList(List<SysDepart> departList) {
    // Query rows where responsible departments are set
    LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
    query.isNotNull(SysUser::getDepartIds);
    List<SysUser> users = sysUserMapper.selectList(query);
    Map<String, Object> map = new HashMap(5);
    // First pass: collect distinct responsible department ids
    for (SysUser user : users) {
      String departIds = user.getDepartIds();
      String[] departIdArray = departIds.split(",");
      for (String departId : departIdArray) {
        // If map already has department key, append responsible users
        if (map.containsKey(departId)) {
          String userIds = map.get(departId) + "," + user.getId();
          map.put(departId, userIds);
        } else {
          map.put(departId, user.getId());
        }
      }
    }
    // Iterate departments and attach responsible users by id
    for (SysDepart sysDepart : departList) {
      if (map.containsKey(sysDepart.getId())) {
        sysDepart.setDirectorUserIds(map.get(sysDepart.getId()).toString());
      }
    }
  }

  // update-end---author:wangshuai ---date:20200308
  // for: [JTC-119] Set the department head under the department management menu, add a method to add the department head, delete the responsible department head, and inquire the corresponding person of the department

  /**
   * Get the companies my department has joined
   *
   * @return
   */
  @Override
  public List<SysDepart> getMyDepartList() {
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    String userId = user.getId();
    // Dict code collection
    List<String> list = new ArrayList<>();
    // Query departments I joined
    List<SysDepart> sysDepartList = this.baseMapper.queryUserDeparts(userId);
    for (SysDepart sysDepart : sysDepartList) {
      // Get top-level department codes
      String orgCode = sysDepart.getOrgCode();
      if (YouBianCodeUtil.ZHANWEI_LENGTH <= orgCode.length()) {
        int length = YouBianCodeUtil.ZHANWEI_LENGTH;
        String companyOrgCode = orgCode.substring(0, length);
        list.add(companyOrgCode);
      }
    }
    // Dict code collection is not empty
    if (oConvertUtils.isNotEmpty(list)) {
      // Query top-level department data
      LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<>();
      query.select(SysDepart::getDepartName, SysDepart::getId, SysDepart::getOrgCode);
      query.eq(SysDepart::getDelFlag, String.valueOf(CommonConstant.DEL_FLAG_0));
      query.in(SysDepart::getOrgCode, list);
      return this.baseMapper.selectList(query);
    }
    return null;
  }

  @Override
  public void deleteDepart(String id) {
    // After delete, update parent leaf flag
    this.setIzLeaf(id);
    this.delete(id);
    // Delete department-user relations
    LambdaQueryWrapper<SysUserDepart> query =
        new LambdaQueryWrapper<SysUserDepart>().eq(SysUserDepart::getDepId, id);
    this.userDepartMapper.delete(query);
  }

  @Override
  public List<SysDepartTreeModel> queryBookDepTreeSync(
      String parentId, Integer tenantId, String departName) {
    List<SysDepart> list = departMapper.queryBookDepTreeSync(parentId, tenantId, departName);
    List<SysDepartTreeModel> records = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      SysDepart depart = list.get(i);
      SysDepartTreeModel treeModel = new SysDepartTreeModel(depart);
      records.add(treeModel);
    }
    return records;
  }

  @Override
  public SysDepart getDepartById(String id) {
    return departMapper.getDepartById(id);
  }

  @Override
  public IPage<SysDepart> getMaxCodeDepart(Page<SysDepart> page, String parentId) {
    return page.setRecords(departMapper.getMaxCodeDepart(page, parentId));
  }

  @Override
  public void updateIzLeaf(String id, Integer izLeaf) {
    departMapper.setMainLeaf(id, izLeaf);
  }

  /**
   * Set whether the parent node has leaf nodes
   *
   * @param id
   */
  private void setIzLeaf(String id) {
    SysDepart depart = this.getDepartById(id);
    String parentId = depart.getParentId();
    if (oConvertUtils.isNotEmpty(parentId)) {
      Long count =
          this.count(new QueryWrapper<SysDepart>().lambda().eq(SysDepart::getParentId, parentId));
      if (count == 1) {
        // If the parent has no other children, the parent is a leaf node
        departMapper.setMainLeaf(parentId, CommonConstant.IS_LEAF);
      }
    }
  }

  // ========================begin no-code dept/user export
  // ==================================================================

  @Override
  public List<ExportDepartVo> getExcelDepart(int tenantId) {
    // Get parent department
    List<ExportDepartVo> parentDepart = departMapper.getDepartList("", tenantId);
    // subdepartment
    List<ExportDepartVo> childrenDepart = new ArrayList<>();
    // Put the name of the first-level department inside
    List<ExportDepartVo> exportDepartVoList = new ArrayList<>();
    // Store department level id to avoid duplication
    List<String> departIdList = new ArrayList<>();
    for (ExportDepartVo departVo : parentDepart) {
      departIdList.add(departVo.getId());
      departVo.setDepartNameUrl(departVo.getDepartName());
      exportDepartVoList.add(departVo);
      // create path
      List<String> path = new ArrayList<>();
      path.add(departVo.getDepartName());
      // Create subdepartment path
      findPath(departVo, path, tenantId, childrenDepart, departIdList);
      path.clear();
    }
    exportDepartVoList.addAll(childrenDepart);
    childrenDepart.clear();
    departIdList.clear();
    return exportDepartVoList;
  }

  /**
   * Find Department Paths
   *
   * @param departVo department vo
   * @param path department path
   * @param tenantId tenant id
   * @ param childrenDepart subdepartment
   * @ param departIdList department id collection
   */
  private void findPath(
      ExportDepartVo departVo,
      List<String> path,
      Integer tenantId,
      List<ExportDepartVo> childrenDepart,
      List<String> departIdList) {
    // Get department data by tenant id and department parent id
    List<ExportDepartVo> departList = departMapper.getDepartList(departVo.getId(), tenantId);
    // Judgment that the department is empty
    if (departList == null || departList.size() <= 0) {
      if (!departIdList.contains(departVo.getId())) {
        departVo.setDepartNameUrl(String.join(SymbolConstant.SINGLE_SLASH, path));
        childrenDepart.add(departVo);
      }
      return;
    }

    for (int i = 0; i < departList.size(); i++) {
      ExportDepartVo exportDepartVo = departList.get(i);
      // Store child path
      List<String> cPath = new ArrayList<>();
      cPath.addAll(path);
      cPath.add(exportDepartVo.getDepartName());
      if (!departIdList.contains(departVo.getId())) {
        departIdList.add(departVo.getId());
        departVo.setDepartNameUrl(String.join(SymbolConstant.SINGLE_SLASH, path));
        childrenDepart.add(departVo);
      }
      findPath(exportDepartVo, cPath, tenantId, childrenDepart, departIdList);
    }
  }

  // ========================end no-code dept/user export
  // ==================================================================

  // ========================begin no-code dept/user import
  // ==================================================================
  @Override
  public void importExcel(List<ExportDepartVo> listSysDeparts, List<String> errorMessageList) {
    int num = 0;
    int tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);

    // Department path sorting
    Collections.sort(
        listSysDeparts,
        new Comparator<ExportDepartVo>() {
          @Override
          public int compare(ExportDepartVo o1, ExportDepartVo o2) {
            if (oConvertUtils.isNotEmpty(o1.getDepartNameUrl())
                && oConvertUtils.isNotEmpty(o2.getDepartNameUrl())) {
              int oldLength = o1.getDepartNameUrl().split(SymbolConstant.SINGLE_SLASH).length;
              int newLength = o2.getDepartNameUrl().split(SymbolConstant.SINGLE_SLASH).length;
              return oldLength - newLength;
            } else {
              return 0;
            }
          }
        });
    // Map where department data is stored
    Map<String, SysDepart> departMap = new HashMap<>();
    // Loop second pass import data
    for (ExportDepartVo exportDepartVo : listSysDeparts) {
      SysDepart sysDepart = new SysDepart();
      // orgCode encoding length
      int codeLength = YouBianCodeUtil.ZHANWEI_LENGTH;
      Boolean izExport = false;
      try {
        izExport =
            this.addDepartByName(
                exportDepartVo.getDepartNameUrl(),
                exportDepartVo.getDepartName(),
                sysDepart,
                errorMessageList,
                tenantId,
                departMap,
                num);
      } catch (Exception e) {
        // No parentDept found
      }
      // Data will be imported only when there are no errors
      if (izExport) {
        sysDepart.setOrgType(sysDepart.getOrgCode().length() / codeLength + "");
        sysDepart.setDelFlag(CommonConstant.DEL_FLAG_0.toString());
        sysDepart.setOrgCategory("1");
        sysDepart.setTenantId(tenantId);
        ImportExcelUtil.importDateSaveOne(
            sysDepart,
            ISysDepartService.class,
            errorMessageList,
            num,
            CommonConstant.SQL_INDEX_UNIQ_DEPART_ORG_CODE);
        departMap.put(exportDepartVo.getDepartNameUrl(), sysDepart);
      }
      num++;
    }
  }

  /**
   * Add department
   *
   * @param departNameUrl department path
   * @param departName department name
   * @param sysDepart department class
   * @param errorMessageList error list
   * @param tenantId tenant id
   * @param departMap department map to avoid re-query; key=path, value=department
   * @param num row number with error
   */
  private Boolean addDepartByName(
      String departNameUrl,
      String departName,
      SysDepart sysDepart,
      List<String> errorMessageList,
      Integer tenantId,
      Map<String, SysDepart> departMap,
      int num) {
    int lineNumber = num + 1;
    if (oConvertUtils.isEmpty(departNameUrl) && oConvertUtils.isEmpty(departName)) {
      // DepartmentPathis empty
      errorMessageList.add("Row " + lineNumber + " row: department path or department name is empty, import forbidden");
      return false;
    }
    // GetDepartmentNamePath
    String name = "";
    if (departNameUrl.contains(SymbolConstant.SINGLE_SLASH)) {
      // Get the divided department name
      name = departNameUrl.substring(departNameUrl.lastIndexOf(SymbolConstant.SINGLE_SLASH) + 1);
    } else {
      name = departNameUrl;
    }

    if (!name.equals(departName)) {
      // Department name already exists
      errorMessageList.add(
          "Row "
              + lineNumber
              + " row: department path recorded:\""
              + departNameUrl
              + "“"
              + " and department name: "
              + departName
              + "Inconsistent, please verify!");
      return false;
    } else {
      String parentId = "";
      // Determine whether it contains "/"
      if (departNameUrl.contains(SymbolConstant.SINGLE_SLASH)) {
        // Get the path before the last slash
        String departNames =
            departNameUrl.substring(0, departNameUrl.lastIndexOf(SymbolConstant.SINGLE_SLASH));
        // Determine whether the department path is included
        if (departMap.containsKey(departNames)) {
          SysDepart depart = departMap.get(departNames);
          if (null != depart) {
            parentId = depart.getId();
          }
        } else {
          // Split the slash path to see if this path exists in the database
          String[] departNameUrls = departNameUrl.split(SymbolConstant.SINGLE_SLASH);
          String departUrlName = departNameUrls[0];
          // Determine whether it is the last one
          int count = 0;
          SysDepart depart = new SysDepart();
          depart.setId("");
          String parentIdByName =
              this.getDepartListByName(
                  departUrlName,
                  tenantId,
                  depart,
                  departNameUrls,
                  count,
                  departNameUrls.length - 1,
                  name,
                  departMap);
          // If parentId is not empty
          if (oConvertUtils.isNotEmpty(parentIdByName)) {
            parentId = parentIdByName;
          } else {
            // Department name already exists
            errorMessageList.add("Row " + lineNumber + " row: department name recorded \"" + departName + "Parent does not exist, please verify!");
            return false;
          }
        }
      }
      // Check whether the department name already exists
      SysDepart parentDept = null;
      // update-begin---author: wangshuai ---date: 20230721 for: A tenant department name may have more than one ------------
      List<SysDepart> sysDepartList = departMapper.getDepartByName(departName, tenantId, parentId);
      if (CollectionUtil.isNotEmpty(sysDepartList)) {
        parentDept = sysDepartList.get(0);
      }
      // update-end---author:wangshuai ---date:20230721 for: A tenant department name may have multiple names------------
      if (null != parentDept) {
        // Department name already exists
        errorMessageList.add("Row " + lineNumber + " row: department name recorded \"" + departName + "”already exists，Please check！");
        return false;
      } else {
        Page<SysDepart> page = new Page<>(1, 1);
        // Need to get the parent id to see if the parent already exists
        // Get the maximum orgCode of the first-level department
        List<SysDepart> records = departMapper.getMaxCodeDepart(page, parentId);
        String newOrgCode = "";
        if (CollectionUtil.isNotEmpty(records)) {
          newOrgCode = YouBianCodeUtil.getNextYouBianCode(records.get(0).getOrgCode());
        } else {
          // Query parent id
          if (oConvertUtils.isNotEmpty(parentId)) {
            SysDepart departById = departMapper.getDepartById(parentId);
            newOrgCode = YouBianCodeUtil.getSubYouBianCode(departById.getOrgCode(), null);
          } else {
            newOrgCode = YouBianCodeUtil.getNextYouBianCode(null);
          }
        }
        if (oConvertUtils.isNotEmpty(parentId)) {
          this.updateIzLeaf(parentId, CommonConstant.NOT_LEAF);
          sysDepart.setParentId(parentId);
        }
        sysDepart.setOrgCode(newOrgCode);
        sysDepart.setDepartName(departName);
        return true;
      }
    }
  }

  /**
   * Get the department name url (subordinate)
   *
   * @param departName department name
   * @param tenantId tenant id
   * @param sysDepart department object
   * @param count department path subscript
   * @param departNameUrls department path
   * @param departNum department path count
   * @param name department path count
   * @param departMap department data; key=path, value=department object
   */
  private String getDepartListByName(
      String departName,
      Integer tenantId,
      SysDepart sysDepart,
      String[] departNameUrls,
      int count,
      int departNum,
      String name,
      Map<String, SysDepart> departMap) {
    // Recursively search for the next level
    // update-begin---author: wangshuai ---date: 20230721 for: A tenant department name may have more than one ------------
    SysDepart parentDept = null;
    List<SysDepart> departList =
        departMapper.getDepartByName(departName, tenantId, sysDepart.getId());
    if (CollectionUtil.isNotEmpty(departList)) {
      parentDept = departList.get(0);
    }
    // update-end---author:wangshuai ---date:20230721 for: A tenant department name may have multiple names------------
    // Determine whether it contains /
    if (oConvertUtils.isNotEmpty(name)) {
      name = name + SymbolConstant.SINGLE_SLASH + departName;
    } else {
      name = departName;
    }
    if (null != parentDept) {
      // If the name path key no longer exists, add one to avoid querying again.
      if (!departMap.containsKey(name)) {
        departMap.put(name, parentDept);
      }
      // The department name returned from the query is compared with the department name in the department path. If it does not exist, empty is returned directly.
      if (parentDept.getDepartName().equals(departNameUrls[count])) {
        count = count + 1;
        // If the quantity and department quantity are equal, it means that the last digit has been reached, and the department ID is returned directly.
        if (count == departNum) {
          return parentDept.getId();
        } else {
          return this.getDepartListByName(
              departNameUrls[count],
              tenantId,
              parentDept,
              departNameUrls,
              count,
              departNum,
              name,
              departMap);
        }
      } else {
        return "";
      }
    } else {
      return "";
    }
  }

  // ========================end no-code dept/user import
  // ==================================================================

  /**
   * Clear department id
   *
   * @param parentIdList
   */
  private void setParentDepartIzLeaf(List<String> parentIdList) {
    if (CollectionUtil.isNotEmpty(parentIdList)) {
      for (String parentId : parentIdList) {
        // When the query parent id has no children, it is updated as a leaf node.
        LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<>();
        query.eq(SysDepart::getParentId, parentId);
        Long count = departMapper.selectCount(query);
        // When no children exist, set the current department as a leaf node
        if (count == 0) {
          departMapper.setMainLeaf(parentId, CommonConstant.IS_LEAF);
        }
      }
    }
  }

  // ========================begin system dept/user import
  // ==================================================================
  /**
   * System department export
   *
   * @param tenantId
   * @return
   */
  @Override
  public List<SysDepartExportVo> getExportDepart(Integer tenantId) {
    // Get parent department
    List<SysDepartExportVo> parentDepart = departMapper.getSysDepartList("", tenantId);
    // subdepartment
    List<SysDepartExportVo> childrenDepart = new ArrayList<>();
    // Put the name of the first-level department inside
    List<SysDepartExportVo> exportDepartVoList = new ArrayList<>();
    // Store department level id to avoid duplication
    List<String> departIdList = new ArrayList<>();
    for (SysDepartExportVo sysDepart : parentDepart) {
      // step 1.add first-level departments
      departIdList.add(sysDepart.getId());
      sysDepart.setDepartNameUrl(sysDepart.getDepartName());
      exportDepartVoList.add(sysDepart);
      // step 2.append own department path separated by /
      // create path
      List<String> path = new ArrayList<>();
      path.add(sysDepart.getDepartName());
      // Create subdepartment path
      findSysDepartPath(sysDepart, path, tenantId, childrenDepart, departIdList);
      path.clear();
    }
    exportDepartVoList.addAll(childrenDepart);
    childrenDepart.clear();
    departIdList.clear();
    return exportDepartVoList;
  }

  /**
   * System department import
   *
   * @param listSysDeparts
   * @param errorMessageList
   */
  @Override
  public void importSysDepart(
      List<SysDepartExportVo> listSysDeparts, List<String> errorMessageList) {
    int num = 0;
    int tenantId = 0;
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
    }
    // Department path sorting
    Collections.sort(
        listSysDeparts,
        new Comparator<SysDepartExportVo>() {
          @Override
          public int compare(SysDepartExportVo o1, SysDepartExportVo o2) {
            if (oConvertUtils.isNotEmpty(o1.getDepartNameUrl())
                && oConvertUtils.isNotEmpty(o2.getDepartNameUrl())) {
              int oldLength = o1.getDepartNameUrl().split(SymbolConstant.SINGLE_SLASH).length;
              int newLength = o2.getDepartNameUrl().split(SymbolConstant.SINGLE_SLASH).length;
              return oldLength - newLength;
            } else {
              return 0;
            }
          }
        });
    // Map where department data is stored
    Map<String, SysDepart> departMap = new HashMap<>();
    // orgCode encoding length
    int codeLength = YouBianCodeUtil.ZHANWEI_LENGTH;
    // Loop second pass import data
    for (SysDepartExportVo departExportVo : listSysDeparts) {
      SysDepart sysDepart = new SysDepart();
      boolean izExport = false;
      try {
        izExport =
            this.addDepartByName(
                departExportVo.getDepartNameUrl(),
                departExportVo.getDepartName(),
                sysDepart,
                errorMessageList,
                tenantId,
                departMap,
                num);
      } catch (Exception e) {
        // No parentDept found
      }
      // Data will be imported only when there are no errors
      if (izExport) {
        if (oConvertUtils.isNotEmpty(departExportVo.getOrgCode())) {
          SysDepart depart = this.baseMapper.queryCompByOrgCode(departExportVo.getOrgCode());
          if (null != depart) {
            if (oConvertUtils.isNotEmpty(sysDepart.getParentId())) {
              // Update parent department as leaf node
              this.updateIzLeaf(sysDepart.getParentId(), CommonConstant.IS_LEAF);
            }
            // Department name already exists
            errorMessageList.add(
                "Row " + num + " row: department name recorded \"" + departExportVo.getDepartName() + "Duplicate department code, please verify!");
            continue;
          }
          String departNameUrl = departExportVo.getDepartNameUrl();
          // Include/Describe is multi-level
          if (departNameUrl.contains(SymbolConstant.SINGLE_SLASH)) {
            // Determine whether the rules of the add department are consistent with the generated ones
            if (!sysDepart.getOrgCode().equals(departExportVo.getOrgCode())) {
              if (oConvertUtils.isNotEmpty(sysDepart.getParentId())) {
                // Update parent department as leaf node
                this.updateIzLeaf(sysDepart.getParentId(), CommonConstant.IS_LEAF);
              }
              // Department name already exists
              errorMessageList.add(
                  "Row " + num + " row: department name recorded \"" + departExportVo.getDepartName() + "Department code rule mismatch, please verify!");
              continue;
            }
          }
          sysDepart.setOrgCode(departExportVo.getOrgCode());
          if (oConvertUtils.isNotEmpty(sysDepart.getParentId())) {
            // Parent
            sysDepart.setOrgType("2");
          } else {
            // subordinate
            sysDepart.setOrgType("1");
          }
        } else {
          sysDepart.setOrgType(sysDepart.getOrgCode().length() / codeLength + "");
        }
        sysDepart.setDelFlag(CommonConstant.DEL_FLAG_0.toString());
        sysDepart.setDepartNameEn(departExportVo.getDepartNameEn());
        sysDepart.setDepartOrder(departExportVo.getDepartOrder());
        sysDepart.setOrgCategory(oConvertUtils.getString(departExportVo.getOrgCategory(), "1"));
        sysDepart.setMobile(departExportVo.getMobile());
        sysDepart.setFax(departExportVo.getFax());
        sysDepart.setAddress(departExportVo.getAddress());
        sysDepart.setMemo(departExportVo.getMemo());
        ImportExcelUtil.importDateSaveOne(
            sysDepart,
            ISysDepartService.class,
            errorMessageList,
            num,
            CommonConstant.SQL_INDEX_UNIQ_DEPART_ORG_CODE);
        departMap.put(departExportVo.getDepartNameUrl(), sysDepart);
      }
      num++;
    }
  }

  /**
   * Find Department Paths
   *
   * @param departVo department vo
   * @param path department path
   * @param tenantId tenant id
   * @ param childrenDepart subdepartment
   * @ param departIdList department id collection
   */
  private void findSysDepartPath(
      SysDepartExportVo departVo,
      List<String> path,
      Integer tenantId,
      List<SysDepartExportVo> childrenDepart,
      List<String> departIdList) {
    // step 1.query child department data
    // Get department data by tenant id and department parent id
    List<SysDepartExportVo> departList = departMapper.getSysDepartList(departVo.getId(), tenantId);
    // Judgment that the department is empty
    if (departList == null || departList.size() <= 0) {
      // Determine whether the last sub-department has been spliced
      if (!departIdList.contains(departVo.getId())) {
        departVo.setDepartNameUrl(String.join(SymbolConstant.SINGLE_SLASH, path));
        childrenDepart.add(departVo);
      }
      return;
    }

    for (SysDepartExportVo exportDepartVo : departList) {
      // Store child path
      List<String> cPath = new ArrayList<>(path);
      cPath.add(exportDepartVo.getDepartName());
      // step 2.build child department path
      if (!departIdList.contains(departVo.getId())) {
        departIdList.add(departVo.getId());
        departVo.setDepartNameUrl(String.join(SymbolConstant.SINGLE_SLASH, path));
        childrenDepart.add(departVo);
      }
      // step 3.recursively query child paths until none
      findSysDepartPath(exportDepartVo, cPath, tenantId, childrenDepart, departIdList);
    }
  }
  // ========================end system dept/user import
  // ==================================================================
}
