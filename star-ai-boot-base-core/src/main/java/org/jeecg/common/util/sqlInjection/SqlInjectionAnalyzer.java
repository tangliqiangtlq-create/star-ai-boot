package org.jeecg.common.util.sqlInjection;

import org.jeecg.common.exception.JeecgSqlInjectionException;
import org.jeecg.common.util.sqlInjection.parse.ParserSupport;
;

/**
 * SQL injection attack analyzer
 *
 * @author guyadong
 * refer to:
 * https://blog.csdn.net/10km/article/details/127767358
 * https://gitee.com/l0km/sql2java/tree/dev/sql2java-manager/src/main/java/gu/sql2java/parser
 */
public class SqlInjectionAnalyzer {

    //Enable/disable injection attack checking
    private boolean injectCheckEnable = true;
    //Prevent SQL injection attack analysis and implementation
    private final InjectionSyntaxObjectAnalyzer injectionChecker;
    private final InjectionAstNodeVisitor injectionVisitor;

    public SqlInjectionAnalyzer() {
        this.injectionChecker = new InjectionSyntaxObjectAnalyzer();
        this.injectionVisitor = new InjectionAstNodeVisitor();
    }

    /**
     * Enable or disable injection attack checks; enabled by default
     *
     * @param enable
     * @return
     */
    public SqlInjectionAnalyzer injectCheckEnable(boolean enable) {
        injectCheckEnable = enable;
        return this;
    }

    /**
     * Analyze parsed SQL for injection attacks; throws {@link JeecgSqlInjectionException} if injection risk is detected
     *
     * @param sqlParserInfo
     * @throws JeecgSqlInjectionException
     */
    public ParserSupport.SqlParserInfo injectAnalyse(ParserSupport.SqlParserInfo sqlParserInfo) throws JeecgSqlInjectionException {
        if (null != sqlParserInfo && injectCheckEnable) {
            /** SQL injection attack check */
            sqlParserInfo.statement.accept(injectionChecker);
            sqlParserInfo.simpleNode.jjtAccept(injectionVisitor, null);
        }
        return sqlParserInfo;
    }

    /**
     * sqlValidate
     */
    public static void checkSql(String sql,boolean check){
        SqlInjectionAnalyzer sqlInjectionAnalyzer = new SqlInjectionAnalyzer();
        sqlInjectionAnalyzer.injectCheckEnable(check);
        ParserSupport.SqlParserInfo sqlParserInfo = ParserSupport.parse0(sql, null,null);
        sqlInjectionAnalyzer.injectAnalyse(sqlParserInfo);
    }
}


