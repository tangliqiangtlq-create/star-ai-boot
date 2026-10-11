package org.jeecg.modules.message.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Arrays;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.modules.message.entity.SysMessage;
import org.jeecg.modules.message.service.ISysMessageService;
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
 * @Description: Message
 *
 * @author: jeecg-boot
 * @date: 2019-04-09
 * @version: V1.0
 */
@Slf4j
@RestController
@RequestMapping("/sys/message/sysMessage")
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class SysMessageController extends JeecgController<SysMessage, ISysMessageService> {
  @Autowired private ISysMessageService sysMessageService;

  /**
   * Paginated list query
   *
   * @param sysMessage
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @PostMapping(value = "/list")
  public Result<?> queryPageList(
      SysMessage sysMessage,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    QueryWrapper<SysMessage> queryWrapper =
        QueryGenerator.initQueryWrapper(sysMessage, req.getParameterMap());
    Page<SysMessage> page = new Page<SysMessage>(pageNo, pageSize);
    IPage<SysMessage> pageList = sysMessageService.page(page, queryWrapper);
    return Result.ok(pageList);
  }

  /**
   * Add
   *
   * @param sysMessage
   * @return
   */
  @PostMapping(value = "/add")
  public Result<?> add(@RequestBody SysMessage sysMessage) {
    sysMessageService.save(sysMessage);
    return Result.ok(I18nUtil.getMessage("common.success.add"));
  }

  /**
   * Edit
   *
   * @param sysMessage
   * @return
   */
  @PutMapping(value = "/edit")
  public Result<?> edit(@RequestBody SysMessage sysMessage) {
    sysMessageService.updateById(sysMessage);
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
    sysMessageService.removeById(id);
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

    this.sysMessageService.removeByIds(Arrays.asList(ids.split(",")));
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
    SysMessage sysMessage = sysMessageService.getById(id);
    return Result.ok(sysMessage);
  }

  /**
   * Export excel
   *
   * @param request
   */
  @PostMapping(value = "/exportXls")
  public ModelAndView exportXls(HttpServletRequest request, SysMessage sysMessage) {
    return super.exportXls(request, sysMessage, SysMessage.class, "Push message template");
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
    return super.importExcel(request, response, SysMessage.class);
  }
}
