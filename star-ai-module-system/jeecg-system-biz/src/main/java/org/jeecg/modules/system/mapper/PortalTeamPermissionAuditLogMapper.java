package org.jeecg.modules.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.jeecg.modules.system.entity.PortalTeamPermissionAuditLog;

@InterceptorIgnore(tenantLine = "true")
public interface PortalTeamPermissionAuditLogMapper
    extends BaseMapper<PortalTeamPermissionAuditLog> {}
