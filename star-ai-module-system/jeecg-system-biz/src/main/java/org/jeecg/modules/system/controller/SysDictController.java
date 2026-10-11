package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.IOException;
import io.swagger.annotations.ApiOperation;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.system.vo.DictQuery;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.ImportExcelUtil;
import org.jeecg.common.util.JeecgJsonUtils;
import org.jeecg.common.util.ETagResponseSupport;
import org.jeecg.common.util.OpsNotifyEmailUtil;
import org.jeecg.common.util.RedisScanSupport;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.system.entity.SysDict;
import org.jeecg.modules.system.entity.SysDictItem;
import org.jeecg.modules.system.model.SysDictTree;
import org.jeecg.modules.system.model.TreeSelectModel;
import org.jeecg.modules.system.service.ISysDictItemService;
import org.jeecg.modules.system.service.ISysDictService;
import org.jeecg.modules.system.vo.SysDictPage;
import org.jeecgframework.poi.excel.ExcelImportCheckUtil;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

/**
 * Dictionary table front-end controller @Author zhangweijian
 *
 * @since 2018-12-28*/
@RestController
@RequestMapping("/sys/dict")
@Slf4j
@SaCheckLogin
public class SysDictController {

  @Autowired private ISysDictService sysDictService;
  @Autowired private ISysDictItemService sysDictItemService;
  @Autowired public RedisTemplate<String, Object> redisTemplate;

  @RequestMapping(value = "/list", method = RequestMethod.POST)
  public Result<IPage<SysDict>> queryPageList(
      SysDict sysDict,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<IPage<SysDict>> result = new Result<IPage<SysDict>>();
    // ------------------------------------------------------------------------------------------------
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      sysDict.setTenantId(oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------
    QueryWrapper<SysDict> queryWrapper =
        QueryGenerator.initQueryWrapper(sysDict, req.getParameterMap());
    Page<SysDict> page = new Page<SysDict>(pageNo, pageSize);
    IPage<SysDict> pageList = sysDictService.page(page, queryWrapper);
    log.debug("Query current page: " + pageList.getCurrent());
    log.debug("Query current page count: " + pageList.getSize());
    log.debug("Query result count: " + pageList.getRecords().size());
    log.debug("Total records: " + pageList.getTotal());
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  /**
   * @Function: Get tree dict data
   *
   * @param sysDict
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @SuppressWarnings("unchecked")
  @RequestMapping(value = "/treeList", method = RequestMethod.POST)
  public Result<List<SysDictTree>> treeList(
      SysDict sysDict,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<List<SysDictTree>> result = new Result<>();
    LambdaQueryWrapper<SysDict> query = new LambdaQueryWrapper<>();
    // Build query conditions
    String dictName = sysDict.getDictName();
    if (oConvertUtils.isNotEmpty(dictName)) {
      query.like(true, SysDict::getDictName, dictName);
    }
    query.orderByDesc(true, SysDict::getCreateTime);
    List<SysDict> list = sysDictService.list(query);
    List<SysDictTree> treeList = new ArrayList<>();
    for (SysDict node : list) {
      treeList.add(new SysDictTree(node));
    }
    result.setSuccess(true);
    result.setResult(treeList);
    return result;
  }

  /**
   * GetAllDictionaryData
   *
   * @return
   */
  @RequestMapping(value = "/queryAllDictItems", method = RequestMethod.POST)
  public Result<?> queryAllDictItems(HttpServletRequest request, HttpServletResponse response) {
    Map<String, List<DictModel>> res = new HashMap(5);
    res = sysDictService.queryAllDictItems();
    // C2-3 / CodePlan 36: ETag on stable dict payload → 304 when If-None-Match matches
    String etag = ETagResponseSupport.computeWeakEtag(res);
    if (ETagResponseSupport.applyEtagOrNotModified(request, response, etag)) {
      return Result.ok(null);
    }
    return Result.ok(res);
  }

  /**
   * GetDictionaryData
   *
   * @param dictCode
   * @return
   */
  @RequestMapping(value = "/getDictText/{dictCode}/{key}", method = RequestMethod.POST)
  public Result<String> getDictText(
      @PathVariable("dictCode") String dictCode, @PathVariable("key") String key) {
    log.info(" dictCode : " + dictCode);
    Result<String> result = new Result<String>();
    String text = null;
    try {
      text = sysDictService.queryDictTextByKey(dictCode, key);
      result.setSuccess(true);
      result.setResult(text);
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysDict.operationFailed"));
      return result;
    }
    return result;
  }

  /**
   * Get dictionary data [API signature verification]
   *
   * @param dictCode dictionary code
   * @param dictCode table name, text field, code field | e.g. sys_user,realname,id
   * @return
   */
  @RequestMapping(value = "/getDictItems/{dictCode}", method = RequestMethod.POST)
  public Result<List<DictModel>> getDictItems(
      @PathVariable("dictCode") String dictCode,
      @RequestParam(value = "sign", required = false) String sign,
      HttpServletRequest request,
      HttpServletResponse response) {
    log.info(" dictCode : " + dictCode);
    Result<List<DictModel>> result = new Result<List<DictModel>>();
    try {
      List<DictModel> ls = sysDictService.getDictItems(dictCode);
      if (ls == null) {
        // Malformed dictCode is a client error -> 400, not HTTP 200 + business 500.
        return Result.error(400, I18nUtil.getMessage("sysDict.dictCodeFormatError"));
      }
      // C2-3 / CodePlan 36: dictionary ETag / If-None-Match → 304
      String etag = ETagResponseSupport.computeWeakEtag(ls);
      if (ETagResponseSupport.applyEtagOrNotModified(request, response, etag)) {
        result.setSuccess(true);
        result.setResult(null);
        return result;
      }
      result.setSuccess(true);
      result.setResult(ls);
      log.debug(result.toString());
    } catch (NotPermissionException e) {
      // Sensitive dict hard-reject: must surface as HTTP 403, not be swallowed into 200 + code 500.
      throw e;
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysDict.operationFailed"));
      return result;
    }
    return result;
  }

  @PostMapping("/edit/{code}")
  @ApiOperation("Edit dictionary value")
  @SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
  public Result editValue(@PathVariable String code, @RequestBody JSONObject data) {
    sysDictService.editValueTxn(code, JSONUtil.parseObj(data.toJSONString()));
    refleshCache();
    return Result.OK(I18nUtil.getMessage("common.success.edit"));
  }

  /**
   * [API signature verification] [Dedicated endpoint for JSearchSelectTag dropdown search] Large dictionary tables load asynchronously; frontend filters data by input
   *
   * @param dictCode dict code format: table,text,code
   * @return
   */
  @RequestMapping(value = "/loadDict/{dictCode}", method = RequestMethod.POST)
  public Result<List<DictModel>> loadDict(
      @PathVariable("dictCode") String dictCode,
      @RequestParam(name = "keyword", required = false) String keyword,
      @RequestParam(value = "sign", required = false) String sign,
      @RequestParam(value = "pageSize", required = false) Integer pageSize) {

    // update-begin-author:taoyan date:2023-5-22 for: /issues/4905 caused by bracket (%5) issue
    // Form generator: when selecting related field for advanced config, DB list fails to load with Sign signature check error! #4905 RouteToRequestUrlFilter
    if (keyword != null && keyword.indexOf("%5") >= 0) {
      try {
        keyword = URLDecoder.decode(keyword, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        log.error("Dropdown search keyword decode failed", e);
      }
    }
    // update-end-author:taoyan date:2023-5-22 for: /issues/4905 caused by bracket (%5) issue
    // Form generator: when selecting related field for advanced config, DB list fails to load with Sign signature check error! #4905

    log.info(" Loading dictionary table data, keyword: " + keyword);
    Result<List<DictModel>> result = new Result<List<DictModel>>();
    try {
      List<DictModel> ls = sysDictService.loadDict(dictCode, keyword, pageSize);
      if (ls == null) {
        // Malformed dictCode is a client error -> 400.
        return Result.error(400, I18nUtil.getMessage("sysDict.dictCodeFormatError"));
      }
      result.setSuccess(true);
      result.setResult(ls);
      log.info(result.toString());
      return result;
    } catch (NotPermissionException e) {
      // Sensitive dict hard-reject: must surface as HTTP 403, not 200 + code 500.
      throw e;
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysDict.operationFailedWithError", new Object[]{e.getMessage()}));
      return result;
    }
  }

  /**
   * [API signature verification] [Form designer table dict] Dropdown search mode; dynamically assemble data when value present
   *
   * @param dictCode
   * @param keyword current control value, comma-separated allowed
   * @param sign
   * @param pageSize
   * @return
   */
  @RequestMapping(value = "/loadDictOrderByValue/{dictCode}", method = RequestMethod.POST)
  public Result<List<DictModel>> loadDictOrderByValue(
      @PathVariable("dictCode") String dictCode,
      @RequestParam(name = "keyword") String keyword,
      @RequestParam(value = "sign", required = false) String sign,
      @RequestParam(value = "pageSize", required = false) Integer pageSize) {
    // First query returns user-selected values without pagination
    Result<List<DictModel>> firstRes = this.loadDict(dictCode, keyword, sign, null);
    if (!firstRes.isSuccess()) {
      return firstRes;
    }
    // Then query the first page of data
    Result<List<DictModel>> result = this.loadDict(dictCode, "", sign, pageSize);
    if (!result.isSuccess()) {
      return result;
    }
    // Merge data from two queries
    List<DictModel> firstList = firstRes.getResult();
    List<DictModel> list = result.getResult();
    for (DictModel firstItem : firstList) {
      // anyMatch means: if any element in the condition matches, return true
      // allMatch means: if all elements in the condition match, return true
      // noneMatch is opposite of allMatch: if all elements fail to match, return true
      boolean none = list.stream().noneMatch(item -> item.getValue().equals(firstItem.getValue()));
      // Add to collection only when element does not exist
      if (none) {
        list.add(0, firstItem);
      }
    }
    return result;
  }

  /**
   * [API signature verification] Load and return dictionary text by dict code
   *
   * @param dictCode order: tableName,text,code
   * @param keys key to query
   * @param sign
   * @param delNotExist whether to remove missing keys (default true); if false, return the key itself when absent in DB
   * @param request
   * @return
   */
  @RequestMapping(value = "/loadDictItem/{dictCode}", method = RequestMethod.POST)
  public Result<List<String>> loadDictItem(
      @PathVariable("dictCode") String dictCode,
      @RequestParam(name = "key") String keys,
      @RequestParam(value = "sign", required = false) String sign,
      @RequestParam(value = "delNotExist", required = false, defaultValue = "true")
          boolean delNotExist,
      HttpServletRequest request) {
    Result<List<String>> result = new Result<>();
    try {
      if (dictCode.indexOf(SymbolConstant.COMMA) != -1) {
        String[] params = dictCode.split(SymbolConstant.COMMA);
        if (params.length != 3) {
          return Result.error(400, I18nUtil.getMessage("sysDict.dictCodeFormatError"));
        }
        List<String> texts =
            sysDictService.queryTableDictByKeys(params[0], params[1], params[2], keys, delNotExist);

        result.setSuccess(true);
        result.setResult(texts);
        log.info(result.toString());
      } else {
        return Result.error(400, I18nUtil.getMessage("sysDict.dictCodeFormatError"));
      }
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysDict.operationFailed"));
      return result;
    }

    return result;
  }

  /**
   * [API signature verification] Load tree data by table name, display field, storage field, and pid
   *
   * @param hasChildField leaf-node indicator field
   * @param converIsLeafVal whether the system should convert leaf-node values (0 = no conversion, 1 = standard auto conversion)
   * @param tableName table name
   * @param text label field
   * @param code value field
   * @param condition query condition ?
   */
  @SuppressWarnings("unchecked")
  @RequestMapping(value = "/loadTreeData", method = RequestMethod.POST)
  public Result<List<TreeSelectModel>> loadTreeData(
      @RequestParam(name = "pid", required = false) String pid,
      @RequestParam(name = "pidField") String pidField,
      @RequestParam(name = "tableName") String tableName,
      @RequestParam(name = "text") String text,
      @RequestParam(name = "code") String code,
      @RequestParam(name = "hasChildField") String hasChildField,
      @RequestParam(name = "converIsLeafVal", defaultValue = "1") int converIsLeafVal,
      @RequestParam(name = "condition") String condition,
      @RequestParam(value = "sign", required = false) String sign,
      HttpServletRequest request) {
    Result<List<TreeSelectModel>> result = new Result<List<TreeSelectModel>>();

    // [QQYUN-9207] Prevent errors when parameters are empty
    if (oConvertUtils.isEmpty(tableName)
        || oConvertUtils.isEmpty(text)
        || oConvertUtils.isEmpty(code)) {
      return Result.error(400, I18nUtil.getMessage("sysDict.dictCodeFormatError"));
    }

    // 1.GetQueryConditionParameter
    Map<String, String> query = null;
    if (oConvertUtils.isNotEmpty(condition)) {
      try {
        query = JeecgJsonUtils.parseToStringMap(condition);
      } catch (IOException e) {
        log.warn("Rejected invalid dict tree condition JSON", e);
        return Result.error(400, "Invalid condition JSON");
      }
    }

    // 2.ReturnQueryResult
    List<TreeSelectModel> ls =
        sysDictService.queryTreeList(
            query, tableName, text, code, pidField, pid, hasChildField, converIsLeafVal);
    result.setSuccess(true);
    result.setResult(ls);
    return result;
  }

  /**
   * [APP API] Query table dictionary data by dict config (no callers found yet)
   *
   * @param query
   * @param pageNo
   * @param pageSize
   * @return
   */
  @Deprecated
  @PostMapping("/queryTableData")
  public Result<List<DictModel>> queryTableData(
      DictQuery query,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      @RequestParam(value = "sign", required = false) String sign,
      HttpServletRequest request) {
    Result<List<DictModel>> res = new Result<List<DictModel>>();
    List<DictModel> ls = this.sysDictService.queryDictTablePageList(query, pageSize, pageNo);
    res.setResult(ls);
    res.setSuccess(true);
    return res;
  }

  /**
   * @Function: Add
   *
   * @param sysDict
   * @return
   */
  @SaCheckPermission("system:dict:add")
  @RequestMapping(value = "/add", method = RequestMethod.POST)
  public Result<SysDict> add(@RequestBody SysDict sysDict) {
    Result<SysDict> result = new Result<SysDict>();
    try {
      sysDict.setCreateTime(new Date());
      sysDict.setDelFlag(CommonConstant.DEL_FLAG_0);
      sysDictService.save(sysDict);
      result.success(I18nUtil.getMessage("sysDict.saveSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysDict.operationFailed"));
    }
    return result;
  }

  /**
   * @Function: Edit
   *
   * @param sysDict
   * @return
   */
  @SaCheckPermission("system:dict:edit")
  @RequestMapping(
      value = "/edit",
      method = {RequestMethod.PUT, RequestMethod.POST})
  public Result<SysDict> edit(@RequestBody SysDict sysDict) {
    Result<SysDict> result = new Result<SysDict>();
    SysDict sysdict = sysDictService.getById(sysDict.getId());
    if (sysdict == null) {
      // Targeting a non-existent dict -> 404, not HTTP 200 + business 500.
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      sysDict.setUpdateTime(new Date());
      boolean ok = sysDictService.updateById(sysDict);
      if (ok) {
        result.success(I18nUtil.getMessage("sysDict.editSuccess"));
      }
    }
    return result;
  }

  /**
   * @Function: Delete
   *
   * @param id
   * @return
   */
  @SaCheckPermission("system:dict:delete")
  @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
  @CacheEvict(
      value = {CacheConstant.SYS_DICT_CACHE, CacheConstant.SYS_ENABLE_DICT_CACHE},
      allEntries = true)
  public Result<SysDict> delete(@RequestParam(name = "id", required = true) String id) {
    Result<SysDict> result = new Result<SysDict>();
    boolean ok = sysDictService.removeById(id);
    if (ok) {
      result.success(I18nUtil.getMessage("sysDict.deleteSuccess"));
    } else {
      result.error500(I18nUtil.getMessage("sysDict.deleteFailed"));
    }
    return result;
  }

  /**
   * @Function: Batch delete
   *
   * @param ids
   * @return
   */
  @SaCheckPermission("system:dict:deleteBatch")
  @RequestMapping(value = "/deleteBatch", method = RequestMethod.DELETE)
  @CacheEvict(
      value = {CacheConstant.SYS_DICT_CACHE, CacheConstant.SYS_ENABLE_DICT_CACHE},
      allEntries = true)
  public Result<SysDict> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    Result<SysDict> result = new Result<SysDict>();
    if (oConvertUtils.isEmpty(ids)) {
      // Empty ids is a client error -> 400.
      return Result.error(400, I18nUtil.getMessage("sysDict.paramNotRecognized"));
    } else {
      sysDictService.removeByIds(Arrays.asList(ids.split(",")));
      result.success(I18nUtil.getMessage("sysDict.deleteSuccess"));
    }
    return result;
  }

  /**
   * @Function: Refresh cache
   *
   * @return
   */
  @PostMapping(value = "/refleshCache")
  public Result<?> refleshCache() {
    Result<?> result = new Result<SysDict>();
    // CodePlan 36 / C2-3: prefix delete via RedisScanSupport (SCAN COUNT 100); ScanSafeRedisUtil.removeAll
    // also delegates here — never redisTemplate.keys / KEYS.
    RedisScanSupport.deleteByPattern(redisTemplate, CacheConstant.SYS_DICT_CACHE);
    RedisScanSupport.deleteByPattern(redisTemplate, CacheConstant.SYS_ENABLE_DICT_CACHE);
    RedisScanSupport.deleteByPattern(redisTemplate, CacheConstant.SYS_DICT_TABLE_CACHE);
    RedisScanSupport.deleteByPattern(redisTemplate, CacheConstant.SYS_DICT_TABLE_BY_KEYS_CACHE);
    RedisScanSupport.deleteByPattern(redisTemplate, CacheConstant.SYS_DEPARTS_CACHE);
    RedisScanSupport.deleteByPattern(redisTemplate, CacheConstant.SYS_DEPART_IDS_CACHE);
    RedisScanSupport.deleteByPattern(redisTemplate, "jmreport:cache:dict");
    RedisScanSupport.deleteByPattern(redisTemplate, "jmreport:cache:dictTable");
    RedisScanSupport.deleteByPattern(redisTemplate, "dcs");

    result.setMessage(I18nUtil.getMessage("sysDict.refreshCacheSuccess"));

    return result;
  }

  /**
   * Export excel
   *
   * @param request
   */
  @PostMapping(value = "/exportXls")
  public ModelAndView exportXls(SysDict sysDict, HttpServletRequest request) {
    // ------------------------------------------------------------------------------------------------
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      sysDict.setTenantId(oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------

    // Step.1 Build query conditions
    QueryWrapper<SysDict> queryWrapper =
        QueryGenerator.initQueryWrapper(sysDict, request.getParameterMap());
    // Step.2 Export Excel via AutoPoi
    ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
    String selections = request.getParameter("selections");
    if (!oConvertUtils.isEmpty(selections)) {
      queryWrapper.in("id", selections.split(","));
    }
    List<SysDictPage> pageList = new ArrayList<SysDictPage>();

    List<SysDict> sysDictList = sysDictService.list(queryWrapper);
    for (SysDict dictMain : sysDictList) {
      SysDictPage vo = new SysDictPage();
      BeanUtils.copyProperties(dictMain, vo);
      // Query flight tickets
      List<SysDictItem> sysDictItemList = sysDictItemService.selectItemsByMainId(dictMain.getId());
      vo.setSysDictItemList(sysDictItemList);
      pageList.add(vo);
    }

    // Export file name
    mv.addObject(NormalExcelConstants.FILE_NAME, "DataDictionary");
    // Annotation target Class
    mv.addObject(NormalExcelConstants.CLASS, SysDictPage.class);
    // Custom table params
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    mv.addObject(
        NormalExcelConstants.PARAMS,
        new ExportParams("DataDictionaryList", "Exported by:" + user.getRealname(), "DataDictionary"));
    // Export data list
    mv.addObject(NormalExcelConstants.DATA_LIST, pageList);
    return mv;
  }

  /**
   * Import data through excel
   *
   * @param request
   * @param
   * @return
   */
  @SaCheckPermission("system:dict:importExcel")
  @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
  public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
    Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
    for (Map.Entry<String, MultipartFile> entity : fileMap.entrySet()) {
      // Get uploaded file object
      MultipartFile file = entity.getValue();
      ImportParams params = new ImportParams();
      params.setTitleRows(2);
      params.setHeadRows(2);
      params.setNeedSave(true);
      try {
        // Import Excel format check; match field text probability
        Boolean t = ExcelImportCheckUtil.check(file.getInputStream(), SysDictPage.class, params);
        if (t != null && !t) {
          throw new RuntimeException("Import ExcelValidateFailed ！");
        }
        List<SysDictPage> list =
            ExcelImportUtil.importExcel(file.getInputStream(), SysDictPage.class, params);
        // Error messages
        List<String> errorMessage = new ArrayList<>();
        int successLines = 0, errorLines = 0;
        for (int i = 0; i < list.size(); i++) {
          SysDict po = new SysDict();
          BeanUtils.copyProperties(list.get(i), po);
          po.setDelFlag(CommonConstant.DEL_FLAG_0);
          try {
            Integer integer = sysDictService.saveMain(po, list.get(i).getSysDictItemList());
            if (integer > 0) {
              successLines++;
              // update-begin---author:wangshuai ---date:20220211
              // for：[JTC-1168]skip import when dict item value empty------------
            } else if (integer == -1) {
              errorLines++;
              errorMessage.add("DictionaryName：" + po.getDictName() + ", dictionary item values for the corresponding dictionary list cannot be empty; import ignored.");
            } else {
              // update-end---author:wangshuai ---date:20220211
              // for：[JTC-1168]skip import when dict item value empty------------
              errorLines++;
              int lineNumber = i + 1;
              // update-begin---author:wangshuai ---date:20220209
              // for: [JTC-1168] Dictionary code cannot be empty ------------
              if (oConvertUtils.isEmpty(po.getDictCode())) {
                errorMessage.add("Row " + lineNumber + " row: dictionary code cannot be empty, import ignored.");
              } else {
                errorMessage.add("Row " + lineNumber + " row: dictionary code already exists, import ignored.");
              }
              // update-end---author:wangshuai ---date:20220209  for: [JTC-1168] Dictionary code cannot be empty ------------
            }
          } catch (Exception e) {
            errorLines++;
            int lineNumber = i + 1;
            errorMessage.add("Row " + lineNumber + " row: dictionary code already exists, import ignored.");
          }
        }
        return ImportExcelUtil.imporReturnRes(errorLines, successLines, errorMessage);
      } catch (Exception e) {
        log.error("operation failed", e);
        return Result.error(I18nUtil.getMessage("sysDict.importFailed", new Object[]{e.getMessage()}));
      } finally {
        try {
          file.getInputStream().close();
        } catch (Exception e) {
          log.error("operation failed", e);
        }
      }
    }
    return Result.error(I18nUtil.getMessage("sysDict.importFailed", new Object[]{""}));
  }

  /**
   * Physical delete
   *
   * @param id
   * @return
   */
  @RequestMapping(value = "/deletePhysic/{id}", method = RequestMethod.DELETE)
  public Result<?> deletePhysic(@PathVariable("id") String id) {
    try {
      sysDictService.deleteOneDictPhysically(id);
      return Result.ok(I18nUtil.getMessage("sysDict.deleteSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      return Result.error(I18nUtil.getMessage("sysDict.deleteFailed"));
    }
  }

  /**
   * @Function: Query logically deleted dict data in recycle bin
   *
   * @param tenantId tenant ID (optional, for multi-tenant mode)
   * @return
   */
  @PostMapping(value = "/deleteList")
  public Result<List<SysDict>> deleteList(
      @RequestParam(name = "tenantId", required = false) String tenantId) {
    Result<List<SysDict>> result = new Result<List<SysDict>>();
    try {
      List<SysDict> list = sysDictService.queryDeleteList(tenantId);
      result.setSuccess(true);
      result.setResult(list);
      return result;
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysDict.queryFailed"));
      return result;
    }
  }

  /**
   * Recover a logically deleted field
   *
   * @param id
   * @return
   */
  @RequestMapping(value = "/back/{id}", method = RequestMethod.PUT)
  public Result<?> back(@PathVariable("id") String id) {
    try {
      sysDictService.updateDictDelFlag(0, id);
      return Result.ok(I18nUtil.getMessage("sysDict.operationSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      return Result.error(I18nUtil.getMessage("sysDict.operationFailedMsg"));
    }
  }

  /**
   * VUEN-2584 [issue] Platform SQL injection vulnerabilities.
   * Some special functions can embed query results in error messages, exposing database information.
   *
   * @param e
   * @return
   */
  @ExceptionHandler(java.sql.SQLException.class)
  public Result<?> handleSQLException(Exception e) {
    String msg = e.getMessage();
    String extractvalue = "extractvalue";
    String updatexml = "updatexml";
    if (msg != null
        && (msg.toLowerCase().indexOf(extractvalue) >= 0
            || msg.toLowerCase().indexOf(updatexml) >= 0)) {
      return Result.error(I18nUtil.getMessage("sysDict.sqlParseError"));
    }
    return Result.error(I18nUtil.getMessage("sysDict.sqlParseError") + " " + msg);
  }
}
