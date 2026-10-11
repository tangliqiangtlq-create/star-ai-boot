package org.jeecg.modules.message.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.jeecg.common.system.base.service.impl.JeecgServiceImpl;
import org.jeecg.modules.message.entity.SysMessageTemplate;
import org.jeecg.modules.message.mapper.SysMessageTemplateMapper;
import org.jeecg.modules.message.service.ISysMessageTemplateService;
import org.jeecg.modules.message.service.TemplateEditApprovalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class SysMessageTemplateServiceImpl extends JeecgServiceImpl<SysMessageTemplateMapper, SysMessageTemplate> implements ISysMessageTemplateService {

    @Autowired
    private SysMessageTemplateMapper sysMessageTemplateMapper;

    @Resource
    @Lazy
    private TemplateEditApprovalService templateEditApprovalService;


    @Override
    public List<SysMessageTemplate> selectByCode(String code) {
        return sysMessageTemplateMapper.selectByCode(code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(SysMessageTemplate entity) {
        if (entity != null && templateEditApprovalService != null) {
            templateEditApprovalService.guardSaveSysMessageTemplate(
                entity, false,
                StrUtil.isBlank(entity.getTemplateName()) ? "CREATE_TEMPLATE" : "CREATE: " + entity.getTemplateName());
        }
        return super.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(SysMessageTemplate entity) {
        if (entity != null && templateEditApprovalService != null) {
            String cs =
                (entity.getTemplateCode() == null ? "" : entity.getTemplateCode())
                    + (entity.getTemplateName() == null ? "" : "/" + entity.getTemplateName());
            templateEditApprovalService.guardSaveSysMessageTemplate(
                entity, true, cs.isEmpty() ? "UPDATE_TEMPLATE" : "UPDATE: " + cs);
        }
        return super.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdate(SysMessageTemplate entity) {
        if (entity != null) {
            boolean isUpdate = entity.getId() != null
                && sysMessageTemplateMapper.selectById(entity.getId()) != null;
            if (templateEditApprovalService != null) {
                String cs =
                    (entity.getTemplateCode() == null ? "" : entity.getTemplateCode())
                        + (entity.getTemplateName() == null ? "" : "/" + entity.getTemplateName());
                templateEditApprovalService.guardSaveSysMessageTemplate(
                    entity, isUpdate,
                    (isUpdate ? "UPDATE" : "CREATE") + (cs.isEmpty() ? "_TEMPLATE" : ": " + cs));
            }
        }
        return super.saveOrUpdate(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBatchById(Collection<SysMessageTemplate> entityList) {
        if (entityList != null && templateEditApprovalService != null) {
            for (SysMessageTemplate e : entityList) {
                if (e == null) continue;
                String cs =
                    (e.getTemplateCode() == null ? "" : e.getTemplateCode())
                        + (e.getTemplateName() == null ? "" : "/" + e.getTemplateName());
                templateEditApprovalService.guardSaveSysMessageTemplate(
                    e, true, cs.isEmpty() ? "BATCH_UPDATE" : "BATCH_UPDATE: " + cs);
            }
        }
        return super.updateBatchById(entityList);
    }
}
