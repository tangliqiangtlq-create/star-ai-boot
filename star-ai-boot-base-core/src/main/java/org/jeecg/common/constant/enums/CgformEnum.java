package org.jeecg.common.constant.enums;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * onlineform enum code generatorUsed
 * @author: jeecg-boot
 */
public enum CgformEnum {

    /**
     * Single table.
     */
    ONE(1, "one", "/jeecg/code-template-online", "default.one", "Classic style", new String[]{"vue3","vue","vue3Native"}),

    /**
     * Multi-table
     */
    MANY(2, "many", "/jeecg/code-template-online", "default.onetomany", "Classic style" ,new String[]{"vue"}),
    /**
     * Multi-table (JVxe style)
     *  */
    JVXE_TABLE(2, "jvxe", "/jeecg/code-template-online", "jvxe.onetomany", "Default style" ,new String[]{"vue3","vue","vue3Native"}),

    /**
     * Multi-table (ERP style)
     */
    ERP(2, "erp", "/jeecg/code-template-online", "erp.onetomany", "ERP style" ,new String[]{"vue3","vue","vue3Native"}),
    /**
     * Multi-table (inline sub-table style)
     */
    INNER_TABLE(2, "innerTable", "/jeecg/code-template-online", "inner-table.onetomany", "Embedded sub-table style" ,new String[]{"vue3","vue"}),
    /**
     * Multi-table (tab style)
     *  */
    TAB(2, "tab", "/jeecg/code-template-online", "tab.onetomany", "Tab style" ,new String[]{"vue3","vue"}),
    /**
     * Tree list
     */
    TREE(3, "tree", "/jeecg/code-template-online", "default.tree", "Tree list" ,new String[]{"vue3","vue","vue3Native"});

    /**
     * Type 1/single table 2/one-to-many 3/tree
     */
    int type;
    /**
     * Code identifier
     */
    String code;
    /**
     * Code generator template path.
     */
    String templatePath;
    /**
     * Code generator template path.
     */
    String stylePath;
    /**
     * Template style name
     */
    String note;
    /**
     * Supported code styles: vue3 (Vue3 wrapper), vue3Native (native Vue3), vue (Vue2)
     */
    String[] vueStyle;

    /**
     * Constructor
     *
     * @param type type: 1/single table, 2/one-to-many, 3/tree
     * @param code template code
     * @param templatePath  template path
     * @param stylePath  template sub-path
     * @param note
     * @param vueStyle supported code style
     */
    CgformEnum(int type, String code, String templatePath, String stylePath, String note, String[] vueStyle) {
        this.type = type;
        this.code = code;
        this.templatePath = templatePath;
        this.stylePath = stylePath;
        this.note = note;
        this.vueStyle = vueStyle;
    }

    /**
     * Get template path by code
     *
     * @param code
     * @return
     */
    public static String getTemplatePathByConfig(String code) {
        return getCgformEnumByConfig(code).templatePath;
    }


    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public String getStylePath() {
        return stylePath;
    }

    public void setStylePath(String stylePath) {
        this.stylePath = stylePath;
    }

    public String[] getVueStyle() {
        return vueStyle;
    }

    public void setVueStyle(String[] vueStyle) {
        this.vueStyle = vueStyle;
    }

    /**
     * Find enum by code
     *
     * @param code
     * @return
     */
    public static CgformEnum getCgformEnumByConfig(String code) {
        for (CgformEnum e : CgformEnum.values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Find all by type
     *
     * @param type
     * @return
     */
    public static List<Map<String, Object>> getJspModelList(int type) {
        List<Map<String, Object>> ls = new ArrayList<Map<String, Object>>();
        for (CgformEnum e : CgformEnum.values()) {
            if (e.type == type) {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("code", e.code);
                map.put("note", e.note);
                ls.add(map);
            }
        }
        return ls;
    }


}
