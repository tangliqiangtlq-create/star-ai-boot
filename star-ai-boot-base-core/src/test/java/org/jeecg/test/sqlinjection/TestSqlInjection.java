package org.jeecg.test.sqlinjection;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlInjectionUtils;
import org.jeecg.common.util.SqlInjectionUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @Description: SQL injection test class
 * @author: scott
 * @date: August 14, 2023 9:55
 */
public class TestSqlInjection {
  private static final Logger log = LoggerFactory.getLogger(TestSqlInjection.class);


    
    /**
     * Table name with alias, also contains HTML-encoded characters
     */
    @Test
    public void testSpecialSQL() {
        String tableName = "sys_user    t";
        // Fix tableName=sys_user t& retest; vulnerability still present
        if (tableName.contains(" ")) {
            tableName = tableName.substring(0, tableName.indexOf(" "));
        }
        //【issues/4393】 sys_user , (sys_user), sys_user%20, %60sys_user%60
        String reg = "\\s+|\\(|\\)|`";
        tableName = tableName.replaceAll(reg, "");
        log.debug(String.valueOf(tableName));
    }


    /**
     * Test whether SQL has SQL injection risk
     * <p>
     * MyBatis Plus method
     */
    @Test
    public void sqlInjectionCheck() {
        String sql = "select * from sys_user";
        log.debug(String.valueOf(SqlInjectionUtils.check(sql)));
    }


    /**
     * Test whether SQL has SLEEP risk
     * <p>
     *  mybatisPlus method
     */
    @Test
    public void sqlSleepCheck() {
        SqlInjectionUtil.checkSqlAnnotation("(SELECT 6240 FROM (SELECT(SLEEP(5))and 1=2)vidl)");
    }

    /**
     * Test whether SQL has SQL injection risk
     * <p>
     * Custom method
     */
    @Test
    public void sqlInjectionCheck2() {
        String sql = "select * from sys_user";
        SqlInjectionUtil.specialFilterContentForOnlineReport(sql);
    }

    /**
     * Field names must be letters, digits, and underscores only (no spaces, escape sequences, etc.)
     * <p>
     * Check whether the field name conforms to the specification
     */
    @Test
    public void testFieldSpecification() {
        List<String> list = new ArrayList();
        list.add("Hello World!");
        list.add("Hello%20World!");
        list.add("HelloWorld!");
        list.add("Hello World");
        list.add("age");
        list.add("user_name");
        list.add("user_name%20");
        list.add("user_name%20 ");

        for (String input : list) {
            boolean containsSpecialChars = isValidString(input);
            log.debug("input:" + input + " ,contains spaces and special characters: " + containsSpecialChars);
        }
    }

    /**
     * Field names must be letters, digits, and underscores only (no spaces, escape sequences, etc.)
     *
     * @param input
     * @return
     */
    private static boolean isValidString(String input) {
        Pattern pattern = Pattern.compile("^[a-zA-Z0-9_]+$");
        return pattern.matcher(input).matches();
    }

}
