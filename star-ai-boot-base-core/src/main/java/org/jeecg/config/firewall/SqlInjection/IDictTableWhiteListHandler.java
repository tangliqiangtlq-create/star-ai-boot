package org.jeecg.config.firewall.SqlInjection;

/**
 * Dictionary table query :: whitelist configuration
 *
 * @Author taoYan
 * @Date 2022/3/17 11:21
 **/
public interface IDictTableWhiteListHandler {

    /**
     * Validate whether [table name] and [field] are legal for query; returns true if allowed
     *
     * @param sql
     * @return
     */
    boolean isPassBySql(String sql);

    /**
     * Validate whether dictionary passes
     *
     * @param dictCodeString dictionary table configuration
     * @return
     */
    boolean isPassByDict(String dictCodeString);

    boolean isPassByDict(String tableName, String... fields);

    /**
     * Clear cache to apply changes
     *
     * @return
     */
    boolean clear();

    String getErrorMsg();

}
