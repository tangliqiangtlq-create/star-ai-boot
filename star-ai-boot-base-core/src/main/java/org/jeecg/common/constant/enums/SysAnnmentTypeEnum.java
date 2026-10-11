package org.jeecg.common.constant.enums;

import org.jeecg.common.util.oConvertUtils;

/**
 * System announcement custom jump method
 * @author: jeecg-boot
 */
public enum SysAnnmentTypeEnum {
    /**
     * Email redirectComponent
     */
    EMAIL("email", "component", "modules/eoa/email/modals/EoaEmailInForm"),
    /**
     * Process redirect to my tasks
     */
    BPM("bpm", "url", "/bpm/task/MyTaskList"),
    
    /**
     * Process CC task
     */
    BPM_VIEW("bpm_cc", "url", "/bpm/task/MyTaskList"),
    /**
     * Invite users to jump to personal settings
     */
    TENANT_INVITE("tenant_invite", "url", "/system/usersetting");

    /**
     * Business type (email: email, bpm: workflow)
     */
    private String type;
    /**
     * Open mode: component or url route
     */
    private String openType;
    /**
     * Component/route address
     */
    private String openPage;

    SysAnnmentTypeEnum(String type, String openType, String openPage) {
        this.type = type;
        this.openType = openType;
        this.openPage = openPage;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOpenType() {
        return openType;
    }

    public void setOpenType(String openType) {
        this.openType = openType;
    }

    public String getOpenPage() {
        return openPage;
    }

    public void setOpenPage(String openPage) {
        this.openPage = openPage;
    }

    public static SysAnnmentTypeEnum getByType(String type) {
        if (oConvertUtils.isEmpty(type)) {
            return null;
        }
        for (SysAnnmentTypeEnum val : values()) {
            if (val.getType().equals(type)) {
                return val;
            }
        }
        return null;
    }
}
