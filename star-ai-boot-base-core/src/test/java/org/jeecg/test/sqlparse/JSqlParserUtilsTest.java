package org.jeecg.test.sqlparse;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sf.jsqlparser.JSQLParserException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.common.util.sqlparse.JSqlParserUtils;
import org.jeecg.common.util.sqlparse.vo.SelectSqlInfo;
import org.junit.Test;

import java.util.Map;

/**
 * Unit tests for JSqlParserUtils
 */
public class JSqlParserUtilsTest {
  private static final Logger log = LoggerFactory.getLogger(JSqlParserUtilsTest.class);


    private static final String[] sqlList = new String[]{
            "select * from sys_user",
            "select u.* from sys_user u",
            "select u.*, c.name from sys_user u, demo c",
            "select u.age, c.name from sys_user u, demo c",
            "select sex, age, c.name from sys_user, demo c",
            // Alias test
            "select username as realname from sys_user",
            "select username as realname, u.realname as aaa, u.id bbb from sys_user u",
            // No real query field exists
            "select count(1) from sys_user",
            // Functional fields
            "select max(sex), id from sys_user",
            // Complex nested functional fields
            "select CONCAT(CONCAT(' _ ', sex), ' - ' , birthday) as info, id from sys_user",
            // More complex nested functional fields
            "select CONCAT(CONCAT(101,'_',NULL, DATE(create_time),'_',sex),' - ',birthday) as info, id from sys_user",
            // Subquery SQL
            "select u.name1 as name2 from (select username as name1 from sys_user) u",
            // Multi-level nested subquery SQL
            "select u2.name2 as name3 from (select u1.name1 as name2 from (select username as name1 from sys_user) u1) u2",
            // Field subquery SQL
            "select id, (select username as name1 from sys_user u2 where u1.id = u2.id) as name2 from sys_user u1",
            // SQL with conditions (does not parse where fields but does not affect query field parsing)
            "select username as name1 from sys_user where realname LIKE '%Zhang%'",
            // Parse complex multi-table joins involving: sys_user, sys_depart, sys_dict_item, demo
            "" +
                    "SELECT " +
                    "    u.*, d.age, sd.item_text AS sex, (SELECT count(sd.id) FROM sys_depart sd) AS count " +
                    "FROM " +
                    "    (SELECT sd.username AS foo, sd.realname FROM sys_user sd) u, " +
                    "    demo d " +
                    "LEFT JOIN sys_dict_item AS sd ON d.sex = sd.item_value " +
                    "WHERE sd.dict_id = '3d9a351be3436fbefb1307d4cfb49bf2'",
    };

    @Test
    public void testParseSelectSql() {
        log.debug("-----------------------------------------");
        for (String sql : sqlList) {
            log.debug("SQL to be tested: " + sql);
            try {
                // Parse all table names; key=table name, value=parsed SQL info
                Map<String, SelectSqlInfo> parsedMap = JSqlParserUtils.parseAllSelectTable(sql);
                assert parsedMap != null;
                for (Map.Entry<String, SelectSqlInfo> entry : parsedMap.entrySet()) {
                    log.debug("Table name: " + entry.getKey());
                    this.printSqlInfo(entry.getValue(), 1);
                }
            } catch (JSQLParserException e) {
                log.debug("SQL parsing exception: " + "[redacted]");
            }
            log.debug("-----------------------------------------");
        }
    }

    private void printSqlInfo(SelectSqlInfo sqlInfo, int level) {
        String beforeStr = this.getBeforeStr(level);
        if (sqlInfo.getFromTableName() == null) {
            // Subquery
            log.debug(String.valueOf(beforeStr + "Subquery: " + sqlInfo.getFromSubSelect().getParsedSql()));
            this.printSqlInfo(sqlInfo.getFromSubSelect(), level + 1);
        } else {
            // Not a subquery
            log.debug(String.valueOf(beforeStr + "Queried table name: " + sqlInfo.getFromTableName()));
        }
        if (oConvertUtils.isNotEmpty(sqlInfo.getFromTableAliasName())) {
            log.debug(String.valueOf(beforeStr + "Queried table alias: " + sqlInfo.getFromTableAliasName()));
        }
        if (sqlInfo.isSelectAll()) {
            log.debug(String.valueOf(beforeStr + "Queried fields: *"));
        } else {
            log.debug(String.valueOf(beforeStr + "Queried fields: " + sqlInfo.getSelectFields()));
            log.debug(String.valueOf(beforeStr + "Actual field: " + sqlInfo.getRealSelectFields()));
            if (sqlInfo.getFromTableName() == null) {
                log.debug(String.valueOf(beforeStr + "All fields (including subqueries): " + sqlInfo.getAllRealSelectFields()));
            }
        }
    }

    // Print prefix by hierarchy level
    private String getBeforeStr(int level) {
        if (level == 0) {
            return "";
        }
        StringBuilder beforeStr = new StringBuilder();
        for (int i = 0; i < level; i++) {
            beforeStr.append("  ");
        }
        beforeStr.append("- ");
        return beforeStr.toString();
    }

}
