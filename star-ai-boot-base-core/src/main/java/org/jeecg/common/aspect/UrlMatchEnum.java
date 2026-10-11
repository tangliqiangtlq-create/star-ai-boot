package org.jeecg.common.aspect;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * @Author scott
 * @Date 2020/1/14 13:36
 * @Description: Request URL and menu routing URL conversion rules (convenient to use menu routing URL to configure data permission rules)
 */
public enum UrlMatchEnum {
    /**Find URL and menu routing URL conversion rules /online/cgform/api/getData/ */
    CGFORM_DATA("/online/cgform/api/getData/", "/online/cgformList/"),
    /**Find URL and menu routing URL conversion rules /online/cgform/api/exportXls/ */
    CGFORM_EXCEL_DATA("/online/cgform/api/exportXls/", "/online/cgformList/"),
    /**Find URL and menu routing URL conversion rules /online/cgform/api/getTreeData/ */
    CGFORM_TREE_DATA("/online/cgform/api/getTreeData/", "/online/cgformList/"),
    /**Find URL and menu routing URL conversion rules /online/cgreport/api/getColumnsAndData/ */
    CGREPORT_DATA("/online/cgreport/api/getColumnsAndData/", "/online/cgreport/"),
    /** Asking for URL and menu routing URL conversion rules/online/cgreport/api/getData/ [vue3 report data request address] */
    CGREPORT_ONLY_DATA("/online/cgreport/api/getData/", "/online/cgreport/"),
    /**Find URL and menu routing URL conversion rules /online/cgreport/api/exportXls/ */
    CGREPORT_EXCEL_DATA("/online/cgreport/api/exportXls/", "/online/cgreport/"),
    /**Find URL and menu routing URL conversion rules /online/cgreport/api/exportManySheetXls/ */
    CGREPORT_EXCEL_DATA2("/online/cgreport/api/exportManySheetXls/", "/online/cgreport/");

    UrlMatchEnum(String url, String matchUrl) {
        this.url = url;
        this.matchUrl = matchUrl;
    }

    /**
     * Request request URL prefix
     */
    private String url;
    /**
     * Menu routing URL prefix (corresponding to menu path)
     */
    private String matchUrl;

    /**
     * Get the menu configuration path (front-end page routing URL) according to req url
     *
     * @param url
     * @return
     */
    public static String getMatchResultByUrl(String url) {
        //Get the enumeration
        UrlMatchEnum[] values = UrlMatchEnum.values();
        //Strengthen the for loop to perform traversal operations
        for (UrlMatchEnum lr : values) {
            //If the type obtained by traversing is consistent with paramstype
            if (url.indexOf(lr.url) != -1) {
                //Returns the desc of the type object
                return url.replace(lr.url, lr.matchUrl);
            }
        }
        return null;
    }

    public String getMatchUrl() {
        return matchUrl;
    }
    //    public static void main(String[] args) {
//        /**
//         * For example, request real request URL: /online/cgform/api/getData/81fcf7d8922d45069b0d5ba983612d3a
//         * After converting the matching routing URL (corresponding to the configured menu path):/online/cgformList/81fcf7d8922d45069b0d5ba983612d3a
//         */
//        log.debug(String.valueOf(UrlMatchEnum.getMatchResultByUrl("/online/cgform/api/getData/81fcf7d8922d45069b0d5ba983612d3a")));
//    }
}