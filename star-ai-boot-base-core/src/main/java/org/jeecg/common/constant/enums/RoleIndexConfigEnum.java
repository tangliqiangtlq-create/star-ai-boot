package org.jeecg.common.constant.enums;

import org.jeecg.common.util.oConvertUtils;

import java.util.List;

/**
 * Home page customization
 * passRole codewith home pageComponentPath configuration
 * The order of enumeration has the effect of high and low weight of permissions (that is, configuring multiple roles, the previous role homepage will take effect first)
 * @author: jeecg-boot
 */
public enum RoleIndexConfigEnum {

    /**Home page customization admin*/
//    ADMIN("admin", "dashboard/Analysis"),
    //TEST("test",  "dashboard/IndexChart"),
    /**Home page customization hr*/
//    HR("hr", "dashboard/IndexBdc");
  
    //DM("dm", "dashboard/IndexTask"),

    // Note: This value is only to prevent error reporting and has no practical significance.
    ROLE_INDEX_CONFIG_ENUM("RoleIndexConfigEnumDefault", "dashboard/Analysis");

    /**
     * RoleCode
     */
    String roleCode;
    /**
     * routingindex
     */
    String componentUrl;

    /**
     * Constructor
     *
     * @param roleCode role code
     * @param componentUrl home page component path (same rules as menu configuration)
     */
    RoleIndexConfigEnum(String roleCode, String componentUrl) {
        this.roleCode = roleCode;
        this.componentUrl = componentUrl;
    }
    /**
     * Find enum by code
     * @param roleCode role code
     * @return
     */
    private static RoleIndexConfigEnum getEnumByCode(String roleCode) {
        for (RoleIndexConfigEnum e : RoleIndexConfigEnum.values()) {
            if (e.roleCode.equals(roleCode)) {
                return e;
            }
        }
        return null;
    }
    /**
     * Find index by code
     * @param roleCode role code
     * @return
     */
    private static String getIndexByCode(String roleCode) {
        for (RoleIndexConfigEnum e : RoleIndexConfigEnum.values()) {
            if (e.roleCode.equals(roleCode)) {
                return e.componentUrl;
            }
        }
        return null;
    }

    public static String getIndexByRoles(List<String> roles) {
        String[] rolesArray = roles.toArray(new String[roles.size()]);
        for (RoleIndexConfigEnum e : RoleIndexConfigEnum.values()) {
            if (oConvertUtils.isIn(e.roleCode,rolesArray)){
                return e.componentUrl;
            }
        }
        return null;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getComponentUrl() {
        return componentUrl;
    }

    public void setComponentUrl(String componentUrl) {
        this.componentUrl = componentUrl;
    }
}
