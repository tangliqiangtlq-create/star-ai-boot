package org.jeecg.config.vo;

/**
 * Platform security configuration
 *
 * @author: scott
 * @date: September 5, 2023 9:25
 */
public class Firewall {
    /**
     * Data source security (when enabled, Online report/chart data source is required)
     */
    private Boolean dataSourceSafe = false;
    /**
     * Low-code mode (dev: development, prod: production — disables all online development configuration)
     */
    private String lowCodeMode;
//    /**
// * Table dictionary security mode (white: whitelist - tables configured with a whitelist can be accessed through the table dictionary, black: blacklist - tables configured with a blacklist are not allowed to be accessed through the table dictionary)
//     */
//    private String tableDictMode;

    public Boolean getDataSourceSafe() {
        return dataSourceSafe;
    }

    public void setDataSourceSafe(Boolean dataSourceSafe) {
        this.dataSourceSafe = dataSourceSafe;
    }

    public String getLowCodeMode() {
        return lowCodeMode;
    }

    public void setLowCodeMode(String lowCodeMode) {
        this.lowCodeMode = lowCodeMode;
    }

}
