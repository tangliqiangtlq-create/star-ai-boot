package org.jeecg.common.system.base.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import java.io.IOException;
import java.util.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @Description: Controller base class @Author: dangzhenghui@163.com @Date: 2019-4-21 8:13 @Version: 1.0
 */
public class JeecgController<T, S extends IService<T>> {
  protected static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JeecgController.class);

  public static final int MAX_PAGE_SIZE = 500;
  public static final int MAX_EXPORT_SHEET_PAGE_SIZE = 1000;

  /** issues/2933 JeecgController uses protected modification when injecting service to avoid repeated reference to service. */
  @Autowired protected S service;

  @Resource private JeecgBaseConfig jeecgBaseConfig;

  /**
   * Export excel
   *
   * @param request
   */
  protected ModelAndView exportXls(
      HttpServletRequest request, T object, Class<T> clazz, String title) {
    // Step.1 Assemble query conditions
    QueryWrapper<T> queryWrapper =
        QueryGenerator.initQueryWrapper(object, request.getParameterMap());
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");

    // Filter selected data
    String selections = request.getParameter("selections");
    if (oConvertUtils.isNotEmpty(selections)) {
      List<String> selectionList = Arrays.asList(selections.split(","));
      queryWrapper.in("id", selectionList);
    }
    // Step.2 Get exported data
    List<T> exportList = service.list(queryWrapper);

    // Step.3 AutoPoi Export Excel
    ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
    // filename set here is ignored; frontend will reset it
    mv.addObject(NormalExcelConstants.FILE_NAME, title);
    mv.addObject(NormalExcelConstants.CLASS, clazz);
    // update-begin--Author:liusq Date:20210126 for: Image export error, ImageBasePath is not set-----------------------------
    ExportParams exportParams =
        new ExportParams(title + "Report", "Exported by:" + sysUser.getRealname(), title);
    exportParams.setImageBasePath(jeecgBaseConfig.getPath().getUpload());
    // update-end--Author:liusq Date:20210126 for: Image export error, ImageBasePath is not set----------------------
    mv.addObject(NormalExcelConstants.PARAMS, exportParams);
    mv.addObject(NormalExcelConstants.DATA_LIST, exportList);
    return mv;
  }

  /**
   * Export multiple sheets based on the number of sheets per page
   *
   * @param request
   * @param object entity class
   * @param clazz entity class class
   * @param title title
   * @param exportFields export Field customization
   * @param pageNum The number of data items in each sheet
   * @param request
   */
  protected ModelAndView exportXlsSheet(
      HttpServletRequest request,
      T object,
      Class<T> clazz,
      String title,
      String exportFields,
      Integer pageNum) {
    // Step.1 Assemble query conditions
    QueryWrapper<T> queryWrapper =
        QueryGenerator.initQueryWrapper(object, request.getParameterMap());
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    // Step.2 Calculate paging sheet data
    double total = service.count();
    int count = (int) Math.ceil(total / pageNum);
    // update-begin-author:liusq---date:20220629--for: Multi-sheet export is adjusted according to the selected export writing method ---
    // Step.3  Filter selected data
    String selections = request.getParameter("selections");
    if (oConvertUtils.isNotEmpty(selections)) {
      List<String> selectionList = Arrays.asList(selections.split(","));
      queryWrapper.in("id", selectionList);
    }
    // update-end-author:liusq---date:20220629--for: Multi-sheet export is adjusted according to the selected export writing method ---
    // Step.4 Multiple sheet processing
    List<Map<String, Object>> listMap = new ArrayList<Map<String, Object>>();
    if (pageNum == null || pageNum <= 0) {
      pageNum = 200;
    } else if (pageNum > MAX_EXPORT_SHEET_PAGE_SIZE) {
      log.warn("exportXlsSheet: requested pageNum={} exceeds MAX={}, capped to {}", pageNum, MAX_EXPORT_SHEET_PAGE_SIZE, MAX_EXPORT_SHEET_PAGE_SIZE);
      pageNum = MAX_EXPORT_SHEET_PAGE_SIZE;
    }
    for (int i = 1; i <= count; i++) {
      Page<T> page = new Page<T>(i, pageNum);
      IPage<T> pageList = service.page(page, queryWrapper);
      List<T> exportList = pageList.getRecords();
      Map<String, Object> map = new HashMap<>(5);
      ExportParams exportParams =
          new ExportParams(
              title + "Report",
              "Exported by:" + sysUser.getRealname(),
              title + i,
              jeecgBaseConfig.getPath().getUpload());
      exportParams.setType(ExcelType.XSSF);
      // map.put("title",exportParams);
      // TableTitle
      map.put(NormalExcelConstants.PARAMS, exportParams);
      // Table corresponding entity
      map.put(NormalExcelConstants.CLASS, clazz);
      // data collection
      map.put(NormalExcelConstants.DATA_LIST, exportList);
      listMap.add(map);
    }
    // Step.4 AutoPoi Export Excel
    ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
    // filename set here is ignored; frontend will reset it
    mv.addObject(NormalExcelConstants.FILE_NAME, title);
    mv.addObject(NormalExcelConstants.MAP_LIST, listMap);
    return mv;
  }

  /**
   * Export excel according to permissions and pass in the export Field parameters
   *
   * @param request
   */
  protected ModelAndView exportXls(
      HttpServletRequest request, T object, Class<T> clazz, String title, String exportFields) {
    ModelAndView mv = this.exportXls(request, object, clazz, title);
    mv.addObject(NormalExcelConstants.EXPORT_FIELDS, exportFields);
    return mv;
  }

  /**
   * GetObjectID
   *
   * @return
   */
  private String getId(T item) {
    try {
      return PropertyUtils.getProperty(item, "id").toString();
    } catch (Exception e) {
      log.error("operation failed", e);
      return null;
    }
  }

  /**
   * Import data through Excel
   *
   * @param request
   * @param response
   * @return
   */
  protected Result<?> importExcel(
      HttpServletRequest request, HttpServletResponse response, Class<T> clazz) {
    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
    Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
    for (Map.Entry<String, MultipartFile> entity : fileMap.entrySet()) {
      // Get uploaded file object
      MultipartFile file = entity.getValue();
      ImportParams params = new ImportParams();
      params.setHeadRows(1);
      params.setNeedSave(true);
      try {
        List<T> list = ExcelImportUtil.importExcel(file.getInputStream(), clazz, params);
        // update-begin-author:taoyan date:20190528 for:Insert data in batches
        long start = System.currentTimeMillis();
        service.saveBatch(list);
        // 400 items saveBatch Elapsed time 1592 milliseconds Loop insertion Elapsed time 1947 milliseconds
        // 1200 items saveBatch Elapsed time 3687 milliseconds Loop insertion Elapsed time 5212 milliseconds
        log.info("Elapsed time" + (System.currentTimeMillis() - start) + "ms");
        // update-end-author:taoyan date:20190528 for:Insert data in batches
        return Result.ok(I18nUtil.getMessage("common.success.import_with_count", new Object[]{list.size()}));
      } catch (Exception e) {
        // update-begin-author:taoyan date:20211124 for: Import data duplicate increase prompt
        String msg = e.getMessage();
        log.error(msg, e);
        if (msg != null && msg.indexOf("Duplicate entry") >= 0) {
          return Result.error(I18nUtil.getMessage("common.error.import_duplicate_data"));
        } else {
          return Result.error(I18nUtil.getMessage("common.error.import_failed_with_reason", new Object[]{e.getMessage()}));
        }
        // update-end-author:taoyan date:20211124 for: Import data duplicate increase prompt
      } finally {
        try {
          file.getInputStream().close();
        } catch (IOException e) {
          log.error("operation failed", e);
        }
      }
    }
    return Result.error(I18nUtil.getMessage("common.error.import_failed"));
  }

  protected static final int SSE_CURSOR_BATCH_SIZE = 100;
  protected static final long SSE_EMITTER_TIMEOUT_MS = -1L;

  protected SseEmitter exportSseCursor(
      HttpServletRequest request, T object, Class<T> clazz, String title) {
    final SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT_MS);
    QueryWrapper<T> queryWrapper =
        QueryGenerator.initQueryWrapper(object, request.getParameterMap());
    String selections = request.getParameter("selections");
    if (oConvertUtils.isNotEmpty(selections)) {
      List<String> selectionList = Arrays.asList(selections.split(","));
      queryWrapper.in("id", selectionList);
    }
    final long total = service.count(queryWrapper);
    final String cursorCol = "id";

    new Thread(
            () -> {
              try {
                emitter.send(
                    SseEmitter.event()
                        .name("export-start")
                        .data(
                            "{\"title\":"
                                + cn.hutool.json.JSONUtil.toJsonStr(title)
                                + ",\"totalRows\":"
                                + total
                                + ",\"batchSize\":"
                                + SSE_CURSOR_BATCH_SIZE
                                + "}"));
                String lastId = null;
                long sent = 0L;
                long batchIndex = 0L;
                long totalBatches =
                    total == 0 ? 0L : (long) Math.ceil((double) total / SSE_CURSOR_BATCH_SIZE);
                while (sent < total) {
                  long pageNo = (sent / SSE_CURSOR_BATCH_SIZE) + 1L;
                  Page<T> page = new Page<>(pageNo, SSE_CURSOR_BATCH_SIZE);
                  IPage<T> pageList = service.page(page, queryWrapper);
                  List<T> rows = pageList.getRecords();
                  if (rows == null || rows.isEmpty()) {
                    break;
                  }
                  batchIndex++;
                  emitter.send(
                      SseEmitter.event()
                          .id("batch-" + batchIndex)
                          .name("export-rows")
                          .data(cn.hutool.json.JSONUtil.toJsonStr(rows)));
                  Object lastRowIdVal = null;
                  try {
                    lastRowIdVal =
                        org.apache.commons.beanutils.PropertyUtils.getProperty(
                            rows.get(rows.size() - 1), "id");
                  } catch (Exception ignore) {
                  }
                  if (lastRowIdVal != null) {
                    lastId = String.valueOf(lastRowIdVal);
                  }
                  sent += rows.size();
                  emitter.send(
                      SseEmitter.event()
                          .name("export-progress")
                          .data(
                              "{\"batchIndex\":"
                                  + batchIndex
                                  + ",\"totalBatches\":"
                                  + totalBatches
                                  + ",\"sentRows\":"
                                  + sent
                                  + ",\"totalRows\":"
                                  + total
                                  + "}"));
                }
                emitter.send(
                    SseEmitter.event()
                        .name("export-complete")
                        .data(
                            "{\"totalRows\":"
                                + sent
                                + ",\"totalBatches\":"
                                + batchIndex
                                + ",\"entityClass\":"
                                + cn.hutool.json.JSONUtil.toJsonStr(clazz.getName())
                                + "}"));
                emitter.complete();
              } catch (Exception e) {
                log.error("exportSseCursor failed title={}", title, e);
                try {
                  emitter.send(
                      SseEmitter.event()
                          .name("export-error")
                          .data(
                              "{\"error\":"
                                  + cn.hutool.json.JSONUtil.toJsonStr(e.getMessage())
                                  + "}"));
                } catch (IOException ignore) {
                }
                emitter.completeWithError(e);
              }
            },
            "sse-export-" + cn.hutool.core.util.IdUtil.fastSimpleUUID())
        .start();
    return emitter;
  }
}
