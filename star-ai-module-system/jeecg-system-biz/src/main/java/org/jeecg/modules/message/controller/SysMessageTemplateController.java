package org.jeecg.modules.message.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.dto.message.MessageDTO;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.JeecgJsonUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.message.entity.MsgParams;
import org.jeecg.modules.message.entity.SysMessageTemplate;
import org.jeecg.modules.message.service.ISysMessageTemplateService;
import org.jeecg.modules.message.util.PushMsgUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

/**
 * @Description: message template @Author: jeecg-boot @Sate: 2019-04-09 @Version: V1.0
 */
@Slf4j
@RestController
@RequestMapping("/sys/message/sysMessageTemplate")
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class SysMessageTemplateController
    extends JeecgController<SysMessageTemplate, ISysMessageTemplateService> {
  @Autowired private ISysMessageTemplateService sysMessageTemplateService;
  @Autowired private PushMsgUtil pushMsgUtil;

  @Autowired private ISysBaseAPI sysBaseApi;

  /**
   * Paginated list query
   *
   * @param sysMessageTemplate
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @PostMapping(value = "/list")
  public Result<?> queryPageList(
      SysMessageTemplate sysMessageTemplate,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    QueryWrapper<SysMessageTemplate> queryWrapper =
        QueryGenerator.initQueryWrapper(sysMessageTemplate, req.getParameterMap());
    Page<SysMessageTemplate> page = new Page<SysMessageTemplate>(pageNo, pageSize);
    IPage<SysMessageTemplate> pageList = sysMessageTemplateService.page(page, queryWrapper);
    return Result.ok(pageList);
  }

  /**
   * Add
   *
   * @param sysMessageTemplate
   * @return
   */
  @PostMapping(value = "/add")
  public Result<?> add(@RequestBody SysMessageTemplate sysMessageTemplate) {
    sysMessageTemplateService.save(sysMessageTemplate);
    return Result.ok(I18nUtil.getMessage("common.success.add"));
  }

  /**
   * Edit
   *
   * @param sysMessageTemplate
   * @return
   */
  @PutMapping(value = "/edit")
  public Result<?> edit(@RequestBody SysMessageTemplate sysMessageTemplate) {
    sysMessageTemplateService.updateById(sysMessageTemplate);
    return Result.ok(I18nUtil.getMessage("common.success.update"));
  }

  /**
   * Delete by id
   *
   * @param id
   * @return
   */
  @DeleteMapping(value = "/delete")
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    sysMessageTemplateService.removeById(id);
    return Result.ok(I18nUtil.getMessage("common.success.delete"));
  }

  /**
   * Batch delete
   *
   * @param ids
   * @return
   */
  @DeleteMapping(value = "/deleteBatch")
  public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    this.sysMessageTemplateService.removeByIds(Arrays.asList(ids.split(",")));
    return Result.ok(I18nUtil.getMessage("common.success.batch_delete"));
  }

  /**
   * Query by id
   *
   * @param id
   * @return
   */
  @PostMapping(value = "/queryById")
  public Result<?> queryById(@RequestParam(name = "id", required = true) String id) {
    SysMessageTemplate sysMessageTemplate = sysMessageTemplateService.getById(id);
    return Result.ok(sysMessageTemplate);
  }

  /**
   * Export excel
   *
   * @param request
   */
  @PostMapping(value = "/exportXls")
  public ModelAndView exportXls(HttpServletRequest request, SysMessageTemplate sysMessageTemplate) {
    return super.exportXls(request, sysMessageTemplate, SysMessageTemplate.class, "Push message template");
  }

  /**
   * Excel import
   *
   * @param request
   * @param response
   * @return
   */
  @PostMapping(value = "/importExcel")
  public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
    return super.importExcel(request, response, SysMessageTemplate.class);
  }

  /** SendMessage */
  @PostMapping(value = "/sendMsg")
  public Result<SysMessageTemplate> sendMessage(@RequestBody MsgParams msgParams) {
    Result<SysMessageTemplate> result = new Result<SysMessageTemplate>();
    try {
      MessageDTO md = new MessageDTO();
      md.setToAll(false);
      md.setTitle("Message send test");
      md.setTemplateCode(msgParams.getTemplateCode());
      md.setToUser(msgParams.getReceiver());
      md.setType(msgParams.getMsgType());
      String testData = msgParams.getTestData();
      if (oConvertUtils.isNotEmpty(testData)) {
        try {
          Map<String, Object> data = JeecgJsonUtils.parseToMap(testData);
          md.setData(data);
        } catch (IOException e) {
          log.warn("Rejected invalid message template testData JSON", e);
          return result.error500("Invalid testData JSON");
        }
      }
      sysBaseApi.sendTemplateMessage(md);
      return result.success(I18nUtil.getMessage("sysMessageTemplate.sendSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      return result.error500(I18nUtil.getMessage("sysMessageTemplate.sendFailed"));
    }
  }
}
