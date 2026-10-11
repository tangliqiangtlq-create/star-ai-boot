package org.jeecg.modules.message.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.message.entity.SysTemplateEditLog;
import org.jeecg.modules.message.mapper.SysTemplateEditLogMapper;
import org.jeecg.modules.message.service.ISysTemplateEditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class SysTemplateEditLogServiceImpl
        extends ServiceImpl<SysTemplateEditLogMapper, SysTemplateEditLog>
        implements ISysTemplateEditLogService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<SysTemplateEditLog> approve(
            String id, String approverUserId, String approverUsername, String remark) {
        if (StrUtil.isBlank(id)) {
            return Result.error("id empty");
        }
        SysTemplateEditLog row = this.getById(id);
        if (row == null) {
            return Result.error("sys_template_edit_log not found id=" + id);
        }
        if (!SysTemplateEditLog.STATUS_PENDING.equals(row.getStatus())) {
            return Result.error("status not PENDING, current=" + row.getStatus());
        }
        LambdaUpdateWrapper<SysTemplateEditLog> uw = new LambdaUpdateWrapper<>();
        uw.eq(SysTemplateEditLog::getId, id)
                .eq(SysTemplateEditLog::getStatus, SysTemplateEditLog.STATUS_PENDING)
                .set(SysTemplateEditLog::getStatus, SysTemplateEditLog.STATUS_APPROVED)
                .set(SysTemplateEditLog::getApprovedBy, approverUserId)
                .set(SysTemplateEditLog::getRejectReason, StrUtil.isBlank(remark) ? null : remark)
                .set(SysTemplateEditLog::getUpdatedAt, LocalDateTime.now());
        boolean ok = this.update(uw);
        if (!ok) {
            throw new JeecgBootException("sys_template_edit_log approve CAS failed id=" + id);
        }
        row = this.getById(id);
        log.info(
                "[template_edit_ops_approve] approved id={} approver={} approverName={}",
                id, approverUserId, approverUsername);
        return Result.OK(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<SysTemplateEditLog> reject(
            String id, String approverUserId, String approverUsername, String rejectReason) {
        if (StrUtil.isBlank(id)) {
            return Result.error("id empty");
        }
        if (StrUtil.isBlank(rejectReason)) {
            return Result.error("rejectReason empty");
        }
        SysTemplateEditLog row = this.getById(id);
        if (row == null) {
            return Result.error("sys_template_edit_log not found id=" + id);
        }
        if (!SysTemplateEditLog.STATUS_PENDING.equals(row.getStatus())) {
            return Result.error("status not PENDING, current=" + row.getStatus());
        }
        LambdaUpdateWrapper<SysTemplateEditLog> uw = new LambdaUpdateWrapper<>();
        uw.eq(SysTemplateEditLog::getId, id)
                .eq(SysTemplateEditLog::getStatus, SysTemplateEditLog.STATUS_PENDING)
                .set(SysTemplateEditLog::getStatus, SysTemplateEditLog.STATUS_REJECTED)
                .set(SysTemplateEditLog::getApprovedBy, approverUserId)
                .set(SysTemplateEditLog::getRejectReason, rejectReason)
                .set(SysTemplateEditLog::getUpdatedAt, LocalDateTime.now());
        boolean ok = this.update(uw);
        if (!ok) {
            throw new JeecgBootException("sys_template_edit_log reject CAS failed id=" + id);
        }
        row = this.getById(id);
        log.info(
                "[template_edit_ops_reject] rejected id={} approver={} approverName={} reasonLen={}",
                id, approverUserId, approverUsername,
                rejectReason == null ? 0 : rejectReason.length());
        return Result.OK(row);
    }
}
