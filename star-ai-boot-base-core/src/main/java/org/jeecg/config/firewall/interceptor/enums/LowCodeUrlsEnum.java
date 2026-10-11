package org.jeecg.config.firewall.interceptor.enums;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 
 * @author: qinfeng
 * @date: 2023/09/04 11:44
 */
public enum LowCodeUrlsEnum {
    /**
     * Online form configuration request TODO add/update/delete
     */
    NEW_LOW_APP_ADD_URL("/online/cgform/api/addAll", "Add online form"),
    NEW_LOW_APP_EDIT_URL("/online/cgform/api/editAll", "Edit online form"),
    ONLINE_DB_SYNC("/online/cgform/api/doDbSynch/**/**", "Online form sync database"),
    ONLINE_DEL_BATCH("/online/cgform/head/deleteBatch", "Online form batch delete"),
    ONLINE_DELETE("/online/cgform/head/delete", "Online form delete"),
    ONLINE_REMOVE("/online/cgform/head/removeRecord", "Online form remove"),
    ONLINE_COPY("/online/cgform/head/copyOnline", "Online form generate view"),
    ONLINE_TABLE("/online/cgform/head/copyOnlineTable", "Online form copy table"),
    ONLINE_BUTTON_AI_TEST("/online/cgform/button/aitest", "Online form custom button generate data"),
    ONLINE_BUTTON_ADD("/online/cgform/button/add", "Online form custom button add"),
    ONLINE_BUTTON_EDIT("/online/cgform/button/edit", "Online form custom button edit"),
    ONLINE_BUTTON_DEL("/online/cgform/button/deleteBatch", "Online form custom button delete"),
    ONLINE_ENHANCE_JS("/online/cgform/head/enhanceJs/**", "Online form JS enhancement"),
    ONLINE_ENHANCE_JAVA("/online/cgform/head/enhanceJava/**", "Online form JAVA enhancement"),
    /**
     * Online report configuration request.
     */
    ONLINE_CG_REPORT_ADD("/online/cgreport/head/add", "Online report add"),
    ONLINE_CG_REPORT_EDIT("/online/cgreport/head/editAll", "Online report edit"),
    ONLINE_CG_REPORT_DEL("/online/cgreport/head/delete", "Online report delete"),
    ONLINE_CG_REPORT_PARSE_SQL("/online/cgreport/head/parseSql", "Online report SQL parse"),
    /**
     * Online chart configuration request.
     */
    ONLINE_GRAPH_REPORT_ADD("/online/graphreport/head/add", "Online chart add"),
    ONLINE_GRAPH_REPORT_EDIT("/online/graphreport/head/edit", "Online chart edit"),
    ONLINE_GRAPH_REPORT_DEL("/online/graphreport/head/deleteBatch", "Online chart delete"),
    ONLINE_GRAPH_REPORT_PARSE_SQL("/online/cgreport/head/parseSql", "Online chart parse SQL"),

    /**
     * Large-screen configuration request
     */
    BIG_SCREEN_DB_ADD("/bigscreen/bigScreenDb/add", "Dashboard datasource add"),
    BIG_SCREEN_DB_EDIT("/bigscreen/bigScreenDb/edit", "Dashboard datasource edit"),
    BIG_SCREEN_DB_DEL("/bigscreen/bigScreenDb/delete", "Dashboard datasource delete"),
    BIG_SCREEN_DB_TEST_CONNECTION("/bigscreen/bigScreenDb/testConnection", "Dashboard datasource connection test"),
//    BIG_SCREEN_SAVE("/bigscreen/visual/save", "Big screen create"),
//    BIG_SCREEN_EDIT("/bigscreen/visual/update", "Big screen edit"),
//    BIG_SCREEN_COPY("/bigscreen/visual/copy", "Big screen copy"),
//    BIG_SCREEN_REMOVE("/bigscreen/visual/remove", "Big screen remove"),
//    BIG_SCREEN_DEL("/bigscreen/visual/deleteById", "Big screen delete"),

    /**
     * Dashboard configuration request.
     */
    DRAG_DB_ADD("/drag/onlDragDataSource/add", "Dashboard data source add"),
    DRAG_DB_TEST_CONNECTION("/drag/onlDragDataSource/testConnection", "Dashboard data source connection test"),
    DRAG_PARSE_SQL("/drag/onlDragDatasetHead/queryFieldBySql", "Dashboard dataset SQL parse"),
    DRAG_DATASET_ADD("/drag/onlDragDatasetHead/add", "Dashboard dataset add");

    /**
     * Other configuration request.
     */

    private String url;
    private String title;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    LowCodeUrlsEnum(String url, String title) {
        this.url = url;
        this.title = title;
    }

    /**
     * Get available count by code
     *
     * @return
     */
    public static List<String> getLowCodeInterceptUrls() {
        return Arrays.stream(LowCodeUrlsEnum.values()).map(LowCodeUrlsEnum::getUrl).collect(Collectors.toList());
    }

}
