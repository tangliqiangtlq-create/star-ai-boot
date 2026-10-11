package org.jeecg.common.util.sqlInjection.parse;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Throwables;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.exception.JeecgSqlInjectionException;

/**
 * Parse sql support
 */
@Slf4j
public class ParserSupport {
    /**
     * Parse the SELECT SQL statement. If the parsing fails or is not a SELECT statement, an exception will be thrown.
     *
     * @param sql
     * @return
     */
    public static Select parseSelect(String sql) {
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(checkNotNull(sql, "sql is null"));
        } catch (JSQLParserException e) {
            throw new JeecgBootException(e);
        }
        checkArgument(stmt instanceof Select, "%s is not  SELECT statment", sql);
        Select select = (Select) stmt;
        SelectBody selectBody = select.getSelectBody();
        // Currently, only simple SELECT xxxx FROM.... statements are supported and complex statements such as WITH are not supported.
        checkArgument(selectBody instanceof PlainSelect, "ONLY SUPPORT plain select statement %s", sql);
        return (Select) stmt;
    }

    /**
     * Parse the SELECT SQL statement. If the parsing fails or is a non-SELECT statement,
     *
     * @param sql
     * @return
     */
    public static Select parseSelectUnchecked(String sql) {
        try {
            return parseSelect(sql);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse SQL statements; on success returns the parsed {@link Statement},
     * and provides AST (abstract syntax tree) traversal of all nodes via the {@code visitor} parameter.
     *
     * @param sql                 SQL statement
     * @param visitor             {@link SimpleNodeVisitor} instance to traverse all nodes; ignored if {@code null}
     * @param sqlSyntaxNormalizer SQL syntax normalizer; ignored if {@code null}
     * @throws JSQLParserException when the input SQL has syntax errors
     * @see #parse0(String, CCJSqlParserVisitor, SqlSyntaxNormalizer)
     */
    public static Statement parse(String sql, CCJSqlParserVisitor visitor, SqlSyntaxNormalizer sqlSyntaxNormalizer) throws JSQLParserException {
        return parse0(sql, visitor, sqlSyntaxNormalizer).statement;
    }

    /**
     * See {@link CCJSqlParserUtil#parseAST(String)} and {@link CCJSqlParserUtil#parse(String)} for SQL parsing.
     * Returns a parsed {@link SqlParserInfo} on success,
     * and exposes AST node traversal via the {@code visitor} parameter.
     *
     * @param sql               SQL statement
     * @param visitor           {@link SimpleNodeVisitor} instance to traverse all nodes; ignored if {@code null}
     * @param sqlSyntaxAnalyzer SQL syntax analyzer/converter; ignored if {@code null}
     * @throws JSQLParserException when the input SQL has syntax errors
     * @see net.sf.jsqlparser.parser.Node#jjtAccept(SimpleNodeVisitor, Object)
     */
    public static SqlParserInfo parse0(String sql, CCJSqlParserVisitor visitor, SqlSyntaxNormalizer sqlSyntaxAnalyzer) throws JeecgSqlInjectionException {

        //Check whether it does not start with select, which is not supported yet.
        if(!sql.toLowerCase().trim().startsWith("select ")) {
            log.warn("SQL not starting with select passed; non-select parse not supported!");
            return null;
        }

        //Check whether the procedure is stored, which is not supported yet.
        if(sql.toLowerCase().trim().startsWith("call ")){
            log.warn("Stored procedure starting with call passed; procedure parse not supported!");
            return null;
        }

        //Check special characters with special semantics. Currently, three special semantic characters such as colon, $, and # are checked.
        String specialCharacters = "[:$#]";
        Pattern pattern = Pattern.compile(specialCharacters);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            sql = sql.replaceAll("[:$#]", "@");
        }

        checkArgument(null != sql, "sql is null");
        boolean allowComplexParsing = CCJSqlParserUtil.getNestingDepth(sql) <= CCJSqlParserUtil.ALLOWED_NESTING_DEPTH;

        CCJSqlParser parser = CCJSqlParserUtil.newParser(sql).withAllowComplexParsing(allowComplexParsing);
        Statement stmt;
        try {
            stmt = parser.Statement();
        } catch (Exception ex) {
            log.error("Note: SQL syntax may have issues---> {}");
            throw new JeecgSqlInjectionException("Note: SQL syntax may have issues:"+sql);
        }
        if (null != visitor) {
            parser.getASTRoot().jjtAccept(visitor, null);
        }
        if (null != sqlSyntaxAnalyzer) {
            stmt.accept(sqlSyntaxAnalyzer.resetChanged());
        }
        return new SqlParserInfo(stmt.toString(), stmt, (SimpleNode) parser.getASTRoot());
    }

    /**
     * Call {@link CCJSqlParser} to parse the SQL statement component and return the object generated by the parsing, such as {@code 'ORDER BY id DESC'}
     *
     * @param <T>
     * @param input
     * @param method specifies the {@link CCJSqlParser} parsing method called
     * @param targetType returned parsing object Type
     * @return
     * @since 3.18.3
     */
    public static <T> T parseComponent(String input, String method, Class<T> targetType) {
        try {
            CCJSqlParser parser = new CCJSqlParser(new StringProvider(input));
            try {
                return checkNotNull(targetType, "targetType is null").cast(parser.getClass().getMethod(method).invoke(parser));
            } catch (InvocationTargetException e) {
                Throwables.throwIfUnchecked(e.getTargetException());
                throw new RuntimeException(e.getTargetException());
            }
        } catch (IllegalAccessException | NoSuchMethodException | SecurityException e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * If {@link Column} has no table defined and the field name is true/false (case-insensitive), treat it as a boolean constant
     *
     * @param column
     */
    public static boolean isBoolean(Column column) {
        return null != column && null == column.getTable() &&
                Pattern.compile("(true|false)", Pattern.CASE_INSENSITIVE).matcher(column.getColumnName()).matches();
    }

    public static class SqlParserInfo {
        public String nativeSql;
        public Statement statement;
        public SimpleNode simpleNode;

        SqlParserInfo(String nativeSql, Statement statement, SimpleNode simpleNode) {
            this.nativeSql = nativeSql;
            this.statement = statement;
            this.simpleNode = simpleNode;
        }
    }
}

