package org.jeecg.config.firewall.SqlInjection.impl;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.exception.JeecgSqlInjectionException;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.common.util.sqlparse.JSqlParserUtils;
import org.jeecg.common.util.sqlparse.vo.SelectSqlInfo;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.config.firewall.SqlInjection.IDictTableWhiteListHandler;
import org.jeecg.config.firewall.interceptor.LowCodeModeInterceptor;
import org.jeecg.modules.system.security.DictQueryBlackListHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.util.*;

/**
 * Generic whitelist handler; write separate implementations for edge cases
 */
@Slf4j
@Component("dictTableWhiteListHandlerImpl")
public class DictTableWhiteListHandlerImpl implements IDictTableWhiteListHandler {

    /**
     * key - table name
     * value - field names, comma-separated
     * Two configuration styles — configure all keys in lowercase
     * whiteTablesRuleMap.put("sys_user", "*") — all sys_user fields are queryable
     * whiteTablesRuleMap.put("sys_user", "username,password") — username and password in sys_user are queryable
     */
    private static final Map<String, String> whiteTablesRuleMap = new HashMap<>();
    /**
     * Whether LowCode is in dev mode.
     * <p>[PR-B1-A3-2] Fail-Close hardening: when jeecg.firewall configuration is absent or
     * {@code lowCodeMode} is not explicitly set to {@code publish}, DO NOT default to dev to avoid
     * auto-inserting any queried table into the whitelist, which was the root cause of anonymous
     * access to sensitive tables (sys_user_mfa, dcs_blockchain_api_key, sys_log) via
     * {@code /sys/dict/getDictItems} endpoints.
     */
    private static Boolean LOW_CODE_IS_DEV = null;


    @Autowired
    private JeecgBaseConfig jeecgBaseConfig;

    /**
     * Initialize whiteTablesRuleMap Method
     */
    private void init() {
        // In dev mode, query database each time to bypass cache
        if (this.isDev()) {
            DictTableWhiteListHandlerImpl.whiteTablesRuleMap.clear();
        }
    }

    @Override
    public boolean isPassBySql(String sql) {
        Map<String, SelectSqlInfo> parsedMap = null;
        try {
            parsedMap = JSqlParserUtils.parseAllSelectTable(sql);
        } catch (Exception e) {
            log.warn("Validate SQL statement, parse error: {}");
        }
        // If SQL is invalid, execution will fail anyway, so return true directly
        if (parsedMap == null) {
            return true;
        }
        log.info("Getselect sqlInfo ：{} ", parsedMap);
        // Iterate all table names in current SQL; fail if any table or column is not whitelisted
        for (Map.Entry<String, SelectSqlInfo> entry : parsedMap.entrySet()) {
            SelectSqlInfo sqlInfo = entry.getValue();
            if (sqlInfo.isSelectAll()) {
                log.warn("Query statement contains * field, temporarily pass through");
                continue;
            }
            Set<String> queryFields = sqlInfo.getAllRealSelectFields();
            // Validate table and field allowed for query
            String tableName = entry.getKey();
            if (!this.checkWhiteList(tableName, queryFields)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isPassByDict(String dictCodeString) {
        if (oConvertUtils.isEmpty(dictCodeString)) {
            return true;
        }
        try {
            // Decode escaped characters
            dictCodeString = URLDecoder.decode(dictCodeString, "UTF-8");
        } catch (Exception e) {
            log.warn("operation failed");
            //this.throwException("Dict code decode failed; illegal characters may be used, please check!");
        }
        dictCodeString = dictCodeString.trim();
        String[] arr = dictCodeString.split(SymbolConstant.COMMA);
        // Get table name
        String tableName = this.getTableName(arr[0]);
        // GetQueryField
        arr = Arrays.copyOfRange(arr, 1, arr.length);
        // distinct deduplicates, equivalent to Set<String>
        String[] fields = Arrays.stream(arr).map(String::trim).distinct().toArray(String[]::new);
        // Validate table and field allowed for query
        return this.isPassByDict(tableName, fields);
    }

    @Override
    public boolean isPassByDict(String tableName, String... fields) {
        if (oConvertUtils.isEmpty(tableName)) {
            return true;
        }
        if (fields == null || fields.length == 0) {
            fields = new String[]{"*"};
        }
        String sql = "select " + String.join(",", fields) + " from " + tableName;
        log.info("Dictionary-concatenated query SQL: {}", sql);
        try {
            // Perform SQL parsing
            JSqlParserUtils.parseSelectSqlInfo(sql);
        } catch (Exception e) {
            // If SQL parsing fails, validate by field name and table name
            return checkWhiteList(tableName, new HashSet<>(Arrays.asList(fields)));
        }
        // Validate via SQL parsing to prevent SQL injection
        return this.isPassBySql(sql);
    }

    /**
     * Validate whether table name and fields are in the whitelist
     *
     * @param tableName
     * @param queryFields
     * @return
     */
    public boolean checkWhiteList(String tableName, Set<String> queryFields) {
        this.init();
        // 1. Validate table name; empty means validation failed
        if (oConvertUtils.isEmpty(tableName)) {
            log.error("Whitelist validation: table name is empty");
            this.throwException();
        }
        // Normalize to lowercase
        tableName = tableName.toLowerCase();
        String allowFieldStr = DictTableWhiteListHandlerImpl.whiteTablesRuleMap.get(tableName);
        log.info("checkWhiteList tableName: {}", tableName);
        if (oConvertUtils.isEmpty(allowFieldStr)) {
            // In dev mode, auto-insert data into database
            if (this.isDev()) {
                this.autoAddWhiteList(tableName, String.join(",", queryFields));
                allowFieldStr = DictTableWhiteListHandlerImpl.whiteTablesRuleMap.get(tableName);
            } else {
                // in prod mode, throw exception directly
                log.error("Whitelist validation: table \"{}\" failed validation", tableName);
                this.throwException();
            }
        }
        // 2. Validate field name
        // Normalize to lowercase
        allowFieldStr = allowFieldStr.toLowerCase();
        // Wildcard "*" means all fields are allowed
        if ("*".equals(allowFieldStr.trim())) {
            log.info("Whitelist validation: table \"{}\" wildcard (*) all fields allowed", tableName);
            return true;
        }
        Set<String> allowFields = new HashSet<>(Arrays.asList(allowFieldStr.split(",")));
        // Fields to merge
        Set<String> waitMergerFields = new HashSet<>();
        for (String field : queryFields) {
            if(oConvertUtils.isEmpty(field)){
                continue;
            }
            // Normalize to lowercase
            field = field.toLowerCase();
            // If queried field is not in allowed fields, throw exception directly
            if (!allowFields.contains(field)) {
                // In dev mode, record fields to merge
                if (this.isDev()) {
                    waitMergerFields.add(field);
                } else {
                    log.error("Whitelist validation: field {} is not within {}, access denied!", field, allowFields);
                    this.throwException();
                }
            }
        }
        // Auto-merge failed fields into database
        if (!waitMergerFields.isEmpty()) {
            this.autoAddWhiteList(tableName, String.join(",", waitMergerFields));
        }
        log.info("Whitelist validation: query table \"{}\", query field {} passed validation", tableName, queryFields);
        return true;
    }

    /**
     * Auto add whitelist; merge fields if already in database
     *
     * @param tableName
     * @param allowFieldStr
     */
    private void autoAddWhiteList(String tableName, String allowFieldStr) {
    }

    /**
     * Check whether the current LowCode mode is dev.
     *
     * <p>[PR-B1-A3-2] Fail-Close hardening:
     * <ol>
     *   <li>When jeecg.firewall configuration is absent → false (never dev)</li>
     *   <li>When jeecg.firewall.lowCodeMode is blank → false (never dev)</li>
     *   <li>When spring.profiles.active contains prod/prd/production → force false regardless of
     *       lowCodeMode value — prevents accidental Nacos misconfiguration from opening the dict
     *       auto-add-whitelist on production environments.</li>
     *   <li>Only in dev/test/staging environments with lowCodeMode explicitly set to "dev" → true.</li>
     * </ol>
     */
    private boolean isDev() {
        if (DictTableWhiteListHandlerImpl.LOW_CODE_IS_DEV == null) {
            if (this.jeecgBaseConfig.getFirewall() != null) {
                String lowCodeMode = this.jeecgBaseConfig.getFirewall().getLowCodeMode();
                if (oConvertUtils.isNotEmpty(lowCodeMode)
                    && LowCodeModeInterceptor.LOW_CODE_MODE_DEV.equals(lowCodeMode)) {
                    if (isLocalUnitTestProfile()) {
                        DictTableWhiteListHandlerImpl.LOW_CODE_IS_DEV = true;
                    } else {
                            log.error("C5 DEV-LOW-CODE is forbidden in ANY profile except local unit test; remove the lowCodeMode=dev config or migrate to explicit whitelist INSERT by ops_admin");
                            throw new JeecgSqlInjectionException("C5 DEV-LOW-CODE is forbidden in ANY profile except local unit test; remove the lowCodeMode=dev config or migrate to explicit whitelist INSERT by ops_admin");
                    }
                } else {
                    DictTableWhiteListHandlerImpl.LOW_CODE_IS_DEV = false;
                }
            } else {
                DictTableWhiteListHandlerImpl.LOW_CODE_IS_DEV = false;
            }
        }
        return DictTableWhiteListHandlerImpl.LOW_CODE_IS_DEV;
    }

    private boolean isLocalUnitTestProfile() {
        try {
            String active = SpringContextUtils.getApplicationContext() != null
                ? SpringContextUtils.getApplicationContext().getEnvironment().getProperty("spring.profiles.active", "")
                : "";
            if (oConvertUtils.isEmpty(active)) {
                active = System.getProperty("spring.profiles.active", "");
            }
            if (oConvertUtils.isEmpty(active)) {
                active = System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "");
            }
            if (oConvertUtils.isEmpty(active)) {
                return false;
            }
            String[] parts = active.toLowerCase().split("[,\\s]+");
            for (String p : parts) {
                if (p.contains("test") || p.contains("unittest") || p.contains("unit")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("isLocalUnitTestProfile resolve failed, default to fail-close(false): {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean clear() {
        DictTableWhiteListHandlerImpl.whiteTablesRuleMap.clear();
        return true;
    }

    
    /**
     * Extract the table name before WHERE
     *
     * @param str
     * @see DictQueryBlackListHandler#getTableName(String)
     */
    @SuppressWarnings("JavadocReference")
    private String getTableName(String str) {
        String[] arr = str.split("\\s+(?i)where\\s+");
        String tableName = arr[0].trim();
        //[20230814] fix tableName=sys_user t& retest; vulnerability still present
        if (tableName.contains(".")) {
            tableName = tableName.substring(tableName.indexOf(".") + 1, tableName.length()).trim();
        }
        if (tableName.contains(" ")) {
            tableName = tableName.substring(0, tableName.indexOf(" ")).trim();
        }

        //【issues/4393】 sys_user , (sys_user), sys_user%20, %60sys_user%60
        String reg = "\\s+|\\(|\\)|`";
        return tableName.replaceAll(reg, "");
    }

    private void throwException() throws JeecgSqlInjectionException {
        this.throwException(this.getErrorMsg());
    }

    private void throwException(String message) throws JeecgSqlInjectionException {
        if (oConvertUtils.isEmpty(message)) {
            message = this.getErrorMsg();
        }
        log.error(message);
        throw new JeecgSqlInjectionException(message);
    }

    @Override
    public String getErrorMsg() {
        return "Whitelist validation failed!";
    }

}
