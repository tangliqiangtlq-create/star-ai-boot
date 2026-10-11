package org.jeecg.modules.message.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.message.entity.SysTemplateEditLog;
import org.jeecg.modules.message.service.ISysTemplateEditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;

@Api(tags = "sys_template_edit_log")
@RestController
@RequestMapping("/message/sysTemplateEditLog")
@Slf4j
public class SysTemplateEditLogController
        extends JeecgController<SysTemplateEditLog, ISysTemplateEditLogService> {

    @Autowired private ISysTemplateEditLogService sysTemplateEditLogService;

    @ApiOperation(value = "sys_template_edit_log-page list", notes = "page list")
    @GetMapping(value = "/list")
    public Result<?> queryPageList(
            SysTemplateEditLog req,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest req_) {
        QueryWrapper<SysTemplateEditLog> qw = new QueryWrapper<>(req);
        if (oConvertUtils.isNotEmpty(req.getTemplateId())) {
            qw.eq("template_id", req.getTemplateId());
        }
        Page<SysTemplateEditLog> page = new Page<>(pageNo, pageSize);
        IPage<SysTemplateEditLog> pageList = sysTemplateEditLogService.page(page, qw.orderByDesc("created_at"));
        return Result.OK(pageList);
    }

    @ApiOperation(value = "sys_template_edit_log-query by id", notes = "query by id")
    @GetMapping(value = "/queryById")
    public Result<?> queryById(@RequestParam(name = "id") String id) {
        SysTemplateEditLog row = sysTemplateEditLogService.getById(id);
        return row == null ? Result.error(404, "not found") : Result.OK(row);
    }

    @ApiOperation(value = "sys_template_edit_log-approve", notes = "approve pending change")
    @AutoLog("sys_template_edit_log approve")
    @SaCheckRole(SecurityRoleConstants.ROLE_TEMPLATE_APPROVER)
    @PostMapping(value = "/approve")
    public Result<?> approve(
            @RequestParam(name = "id") String id,
            @RequestParam(name = "remark", required = false) String remark) {
        LoginUser cu = (LoginUser) StpUtil.getSession().get("loginUser");
        String uid = cu == null ? StpUtil.getLoginIdAsString() : cu.getId();
        String uname = cu == null ? StpUtil.getLoginIdAsString() : cu.getUsername();
        return sysTemplateEditLogService.approve(id, uid, uname, remark);
    }

    @ApiOperation(value = "sys_template_edit_log-reject", notes = "reject pending change with reason")
    @AutoLog("sys_template_edit_log reject")
    @SaCheckRole(SecurityRoleConstants.ROLE_TEMPLATE_APPROVER)
    @PostMapping(value = "/reject")
    public Result<?> reject(
            @RequestParam(name = "id") String id,
            @RequestParam(name = "rejectReason") String rejectReason) {
        LoginUser cu = (LoginUser) StpUtil.getSession().get("loginUser");
        String uid = cu == null ? StpUtil.getLoginIdAsString() : cu.getId();
        String uname = cu == null ? StpUtil.getLoginIdAsString() : cu.getUsername();
        return sysTemplateEditLogService.reject(id, uid, uname, rejectReason);
    }

    @ApiOperation(value = "sys_template_edit_log-delete", notes = "delete by id")
    @AutoLog("sys_template_edit_log delete")
    @SaCheckRole(SecurityRoleConstants.ROLE_TEMPLATE_APPROVER)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id") String id) {
        sysTemplateEditLogService.removeById(id);
        return Result.OK("Deleted");
    }

    @ApiOperation(value = "sys_template_edit_log-batch delete", notes = "batch delete")
    @AutoLog("sys_template_edit_log batch delete")
    @SaCheckRole(SecurityRoleConstants.ROLE_TEMPLATE_APPROVER)
    @DeleteMapping(value = "/deleteBatch")
    public Result<?> deleteBatch(@RequestParam(name = "ids") String ids) {
        this.sysTemplateEditLogService.removeByIds(Arrays.asList(ids.split(",")));
        return Result.OK("Batch deleted");
    }
}
