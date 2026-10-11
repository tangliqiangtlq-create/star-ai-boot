package org.jeecg.common.system.security;

import cn.dev33.satoken.stp.StpUtil;
import org.jeecg.common.constant.SecurityRoleConstants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CommonRoleGuard {

    public static final Set<String> OWNER_ADMIN_ROLE_SET = new HashSet<>(
        Arrays.asList("admin",
            SecurityRoleConstants.ROLE_OPS_ADMIN,
            SecurityRoleConstants.ROLE_FILE_ADMIN,
            SecurityRoleConstants.ROLE_SEC_ADMIN,
            SecurityRoleConstants.ROLE_SUPER_ADMIN)
    );

    private CommonRoleGuard() {}

    public static boolean hasAnyOwnerAdminRole() {
        try {
            if (!StpUtil.isLogin()) {
                return false;
            }
            String[] roles = OWNER_ADMIN_ROLE_SET.toArray(new String[0]);
            try {
                return StpUtil.hasRoleOr(roles);
            } catch (NoSuchMethodError e) {
                for (String role : OWNER_ADMIN_ROLE_SET) {
                    if (StpUtil.hasRole(role)) {
                        return true;
                    }
                }
                return false;
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    public static boolean hasAnyRole(String... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }
        try {
            if (!StpUtil.isLogin()) {
                return false;
            }
            try {
                return StpUtil.hasRoleOr(roles);
            } catch (NoSuchMethodError e) {
                List<String> userRoles = StpUtil.getRoleList();
                if (userRoles == null || userRoles.isEmpty()) {
                    return false;
                }
                Set<String> userRoleSet = new HashSet<>(userRoles);
                for (String role : roles) {
                    if (userRoleSet.contains(role)) {
                        return true;
                    }
                }
                return false;
            }
        } catch (Exception ignore) {
        }
        return false;
    }
}
