package org.jeecg.common.constant.enums;

import org.jeecg.common.constant.CommonConstant;

/**
 * @Description: operateType
 * @author: jeecg-boot
 * @date: 2022/3/31 10:05
 */
public enum OperateTypeEnum {

    /**
     * List
     */
    LIST(CommonConstant.OPERATE_TYPE_1, "list"),

    /**
     * Add
     */
    ADD(CommonConstant.OPERATE_TYPE_2, "add"),

    /**
     * Edit
     */
    EDIT(CommonConstant.OPERATE_TYPE_3, "edit"),

    /**
     * Delete
     */
    DELETE(CommonConstant.OPERATE_TYPE_4, "delete"),

    /**
     * Import
     */
    IMPORT(CommonConstant.OPERATE_TYPE_5, "import"),

    /**
     * Export
     */
    EXPORT(CommonConstant.OPERATE_TYPE_6, "export");

    /**
     * Type 1 list,2 add,3 edit,4 delete,5 import,6 export
     */
    int type;

    /**
     * Code (request method)
     */
    String code;


    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Constructor
     *
     * @param type type
     * @param code code (request method)
     */
    OperateTypeEnum(int type, String code) {
        this.type = type;
        this.code = code;
    }


    /**
     * Match by request name
     *
     * @param methodName request name
     * @return Integer type
     */
    public static Integer getTypeByMethodName(String methodName) {
        for (OperateTypeEnum e : OperateTypeEnum.values()) {
            if (methodName.startsWith(e.getCode())) {
                return e.getType();
            }
        }
        return CommonConstant.OPERATE_TYPE_1;
    }
}
