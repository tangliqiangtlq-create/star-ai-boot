package org.jeecg.modules.message.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.message.entity.SysMessageTemplate;
import org.jeecg.modules.message.entity.SysTemplateEditLog;
import org.jeecg.modules.message.mapper.SysMessageTemplateMapper;
import org.jeecg.modules.message.mapper.SysTemplateEditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class TemplateEditApprovalService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_APPROVAL = "APPROVAL";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    @Autowired private SysTemplateEditLogMapper editLogMapper;

    @Lazy @Autowired private ISysMessageTemplateService messageTemplateService;
    @Autowired private SysMessageTemplateMapper messageTemplateMapper;

    @Transactional(rollbackFor = Exception.class)
    public SysTemplateEditLog writeAuditLogPending(
            String templateId,
            String templateCode,
            String oldContent,
            String newContent,
            String changeSummary) {
        String editorUserId = null;
        String editorUsername = null;
        try {
            LoginUser cu = null;
            try {
                Object lu = StpUtil.getSession().get("loginUser");
                if (lu instanceof LoginUser) {
                    cu = (LoginUser) lu;
                }
            } catch (Exception ignore) {
            }
            if (cu != null) {
                editorUserId = cu.getId();
                editorUsername = cu.getUsername();
            } else {
                editorUserId = StpUtil.getLoginIdAsString();
                editorUsername = editorUserId;
            }
        } catch (Exception ignore) {
        }

        SysTemplateEditLog row = new SysTemplateEditLog();
        row.setTemplateId(templateId);
        row.setOldContent(oldContent);
        row.setNewContent(newContent);
        row.setEditorUserId(editorUserId);
        row.setEditorUsername(editorUsername);
        row.setApprovedBy(null);
        row.setStatus(SysTemplateEditLog.STATUS_PENDING);
        row.setRejectReason(null);
        row.setTemplateCode(templateCode);
        row.setChangeSummary(changeSummary);
        row.setTenantId(null);
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        editLogMapper.insert(row);

        boolean autoApprove = false;
        try {
            autoApprove =
                    StrUtil.isNotBlank(editorUserId)
                            && (StpUtil.hasRoleOr(SecurityRoleConstants.ROLE_TEMPLATE_APPROVER)
                                    || CommonConstant.FILE_ADMIN.equalsIgnoreCase(editorUsername)
                                    || hasAnyApproverRoleInUser());
        } catch (Exception ignore) {
        }
        if (autoApprove) {
            row.setStatus(SysTemplateEditLog.STATUS_APPROVED);
            row.setApprovedBy(editorUserId);
            row.setUpdatedAt(LocalDateTime.now());
            editLogMapper.updateById(row);
            log.info(
                    "[sys_template_edit_log] auto-approved id={} editor={} templateCode={}",
                    row.getId(), editorUserId, templateCode);
        } else {
            log.info(
                    "[sys_template_edit_log] pending id={} editor={} templateCode={} await template_approver",
                    row.getId(), editorUserId, templateCode);
        }
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean guardSaveSysMessageTemplate(
            SysMessageTemplate entity, boolean isUpdate, String changeSummary) {
        if (entity == null) {
            return false;
        }
        String oldContent = null;
        String currentStatus = null;
        if (isUpdate && StrUtil.isNotBlank(entity.getId())) {
            SysMessageTemplate old =
                    messageTemplateService.getOne(
                            new QueryWrapper<SysMessageTemplate>().eq("id", entity.getId()));
            if (old != null) {
                oldContent = old.getTemplateContent();
                currentStatus = old.getStatus();
            } else {
                oldContent = "";
            }
        } else {
            oldContent = "";
        }
        String newContent = entity.getTemplateContent();
        String templateId = entity.getId();
        String templateCode = entity.getTemplateCode();

        boolean isApprover = false;
        try {
            String editorUserId = StpUtil.getLoginIdAsString();
            isApprover =
                    StrUtil.isNotBlank(editorUserId)
                            && (StpUtil.hasRoleOr(SecurityRoleConstants.ROLE_TEMPLATE_APPROVER)
                                    || CommonConstant.FILE_ADMIN.equalsIgnoreCase(editorUserId)
                                    || hasAnyApproverRoleInUser());
        } catch (Exception ignore) {
        }

        String desiredStatus = entity.getStatus();
        if (oConvertUtils.isEmpty(desiredStatus)) {
            desiredStatus = currentStatus == null ? STATUS_DRAFT : currentStatus;
        }

        boolean transitionAllowed = false;
        if (isApprover) {
            transitionAllowed = true;
            if (STATUS_APPROVED.equalsIgnoreCase(desiredStatus) || STATUS_REJECTED.equalsIgnoreCase(desiredStatus)) {
            }
        } else {
            if (STATUS_DRAFT.equalsIgnoreCase(desiredStatus) || STATUS_APPROVAL.equalsIgnoreCase(desiredStatus)) {
                transitionAllowed = true;
            }
            if (STATUS_APPROVED.equalsIgnoreCase(desiredStatus)) {
                transitionAllowed = false;
            }
        }

        if (!transitionAllowed) {
            log.warn(
                    "[TemplateEditApprovalService] status transition denied. templateCode={}, desiredStatus={}, isApprover={}",
                    templateCode, desiredStatus, isApprover);
            return false;
        }

        entity.setStatus(desiredStatus);

        SysTemplateEditLog audit =
                writeAuditLogPending(
                        templateId, templateCode, oldContent, newContent, changeSummary);

        boolean persisted = isUpdate
                ? messageTemplateService.updateById(entity)
                : messageTemplateService.save(entity);

        if (!persisted) {
            log.warn("[TemplateEditApprovalService] save/update failed for templateCode={}", templateCode);
            return false;
        }

        if (isApprover && STATUS_APPROVED.equalsIgnoreCase(desiredStatus)) {
            audit.setStatus(SysTemplateEditLog.STATUS_APPROVED);
            audit.setApprovedBy(StpUtil.getLoginIdDefaultNull() != null ? StpUtil.getLoginIdAsString() : "system");
            audit.setUpdatedAt(LocalDateTime.now());
            editLogMapper.updateById(audit);
        }
        return true;
    }

    private boolean hasAnyApproverRoleInUser() {
        try {
            Object raw = StpUtil.getRoleList();
            if (raw instanceof java.util.Collection) {
                java.util.Collection<?> rs = (java.util.Collection<?>) raw;
                for (Object r : rs) {
                    if (r != null && SecurityRoleConstants.ROLE_TEMPLATE_APPROVER.equalsIgnoreCase(String.valueOf(r))) {
                        return true;
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return false;
    }
}
