package org.jeecg.common.util.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgSqlInjectionException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Query table/Field blacklist processing
 * @Author taoYan
 * @Date 2022/3/17 11:21
 **/
@Slf4j
public abstract class AbstractQueryBlackListHandler {

    /**
     * key - table name
     * value - field names, comma-separated
     * Two configuration styles — configure all keys in lowercase
     * ruleMap.put("sys_user", "*") — all sys_user fields are not queryable
     * ruleMap.put("sys_user", "username,password") — username and password in sys_user are not queryable
     */
    public static final Map<String, String> ruleMap = new HashMap<>();

    /**
     * Characters that must not appear in table or field names.
     */
    public static final Pattern ILLEGAL_NAME_REG = Pattern.compile("[-]{2,}");

    static {
        ruleMap.put("sys_user", "password,salt");
    }


    /**
     * Get table and field information from SQL statement; must be overridden in concrete implementation -
     * handling may differ by scenario and needs customization, but return type is fixed
     * @param sql
     * @return
     */
    protected abstract List<QueryTable> getQueryTableInfo(String sql);


    /**
     * Validate SQL statement; returns true on success
     * @param sql
     * @return
     */
    public boolean isPass(String sql) {
        List<QueryTable> list = null;
        //[jeecg-boot/issues/4040] Online reports do not support subqueries, and an error is reported in parsing #4040
        try {
            list = this.getQueryTableInfo(sql.toLowerCase());
        } catch (Exception e) {
            log.warn("Business alert");
        }
        
        if(list==null){
            return true;
        }
        log.info("  GetsqlInfo ：{} ", list.toString());
        boolean flag = checkTableAndFieldsName(list);
        if(flag == false){
            return false;
        }
        for (QueryTable table : list) {
            String name = table.getName();
            String fieldRule = ruleMap.get(name);
            // Have you configured this table?
            if (fieldRule != null) {
                if ("*".equals(fieldRule) || table.isAll()) {
                    flag = false;
                    log.warn("Business alert"+name+"] query forbidden");
                    break;
                } else if (table.existSameField(fieldRule)) {
                    flag = false;
                    break;
                }

            }
        }

        // Returns the blacklist check result (throws an exception if it is illegal)
        if(!flag){
            log.error(this.getError());
            throw new JeecgSqlInjectionException(this.getError());
        }
        return flag;
    }

    /**
     * Validate table and field names, or whether special strings may cause SQL injection
     * issues/4983 SQL Injection in 3.5.1 #4983
     * @return
     */
    private boolean checkTableAndFieldsName(List<QueryTable> list){
        boolean flag = true;
        for(QueryTable queryTable: list){
            String tableName = queryTable.getName();
            if(hasSpecialString(tableName)){
                flag = false;
                log.warn("Business alert"+tableName+"Business alert");
                break;
            }
            Set<String> fields = queryTable.getFields();
            for(String name: fields){
                if(hasSpecialString(name)){
                    flag = false;
                    log.warn("Business alert"+name+"Business alert");
                    break;
                } 
            }
        }
        return flag;
    }

    /**
     * Whether the string contains special characters
     * @param name
     * @return
     */
    private boolean hasSpecialString(String name){
        Matcher m = ILLEGAL_NAME_REG.matcher(name);
        if (m.find()) {
            return true;
        }
        return false;
    }
    

    /**
     * Queried table information
     */
    protected class QueryTable {
        //Table name
        private String name;
        //table alias
        private String alias;
        // Field name collection
        private Set<String> fields;
        // Whether to query all fields
        private boolean all;

        public QueryTable() {
        }

        public QueryTable(String name, String alias) {
            this.name = name;
            this.alias = alias;
            this.all = false;
            this.fields = new HashSet<>();
        }

        public void addField(String field) {
            this.fields.add(field);
        }

        public String getName() {
            return name;
        }

        public Set<String> getFields() {
            return new HashSet<>(fields);
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setFields(Set<String> fields) {
            this.fields = fields;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public boolean isAll() {
            return all;
        }

        public void setAll(boolean all) {
            this.all = all;
        }

        /**
         * Check for duplicate fields
         *
         * @param fieldString
         * @return
         */
        public boolean existSameField(String fieldString) {
            String[] controlFields = fieldString.split(",");
            for (String sqlField : fields) {
                for (String controlField : controlFields) {
                    if (sqlField.equals(controlField)) {
                        // Very specific column direct comparison
                        log.warn("Business alert"+name+"] field ["+controlField+"] query forbidden");
                        return true;
                    } else {
                        // Columns using expressions can only read strings containing
                        String aliasColumn = controlField;
                        if (StringUtils.isNotBlank(alias)) {
                            aliasColumn = alias + "." + controlField;
                        }
                        if (sqlField.indexOf(aliasColumn) != -1) {
                            log.warn("Business alert"+name+"] field ["+controlField+"] query forbidden");
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "QueryTable{" +
                    "name='" + name + '\'' +
                    ", alias='" + alias + '\'' +
                    ", fields=" + fields +
                    ", all=" + all +
                    '}';
        }
    }

    public String getError(){
        // TODO
        return "System security rules are enabled; querying sensitive tables and fields is forbidden. Contact an administrator for authorization!";
    }

}
