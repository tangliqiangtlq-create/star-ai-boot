package org.jeecg.common.util.sqlparse;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.common.util.sqlparse.vo.SelectSqlInfo;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

@Slf4j
public class JSqlParserUtils {

    /**
     * Parse query (select) sql information,
     * This method will expand all subqueries into a map.
     * The key only stores the real Table name. If the query does not have a real Table name, it will be ignored.
     * value only stores the real Field name. If the query does not have a real Field name, it will be ignored.
     * <p>
     * For example: SELECT a.*,d.age,(SELECT count(1) FROM sys_depart) AS count FROM (SELECT username AS foo, realname FROM sys_user) a, demo d
     * The parsed result is: {sys_user=[username, realname], demo=[age], sys_depart=[]}
     *
     * @param selectSql
     * @return
     */
    public static Map<String, SelectSqlInfo> parseAllSelectTable(String selectSql) throws JSQLParserException {
        if (oConvertUtils.isEmpty(selectSql)) {
            return null;
        }
        // log.info("Parsed select Sql: {}", selectSql);
        JSqlParserAllTableManager allTableManager = new JSqlParserAllTableManager(selectSql);
        return allTableManager.parse();
    }

    /**
     * Parse query (select) sql information, nested subqueries
     *
     * @param selectSql
     * @return
     */
    public static SelectSqlInfo parseSelectSqlInfo(String selectSql) throws JSQLParserException {
        if (oConvertUtils.isEmpty(selectSql)) {
            return null;
        }
        // log.info("Parsed select Sql: {}", selectSql);
        // Use JSqlParer to parse sql
        // 1. Create a parser
        CCJSqlParserManager mgr = new CCJSqlParserManager();
        // 2. Use a parser to parse sql to generate a java class with a hierarchical structure
        Statement stmt = mgr.parse(new StringReader(selectSql));
        if (stmt instanceof Select) {
            Select selectStatement = (Select) stmt;
            // 3. Parse the information of select query sql
            return JSqlParserUtils.parseBySelectBody(selectStatement.getSelectBody());
        } else {
            // Non-SELECT SQL is left unchanged
            throw new JeecgBootException("Non-select query SQL; not processed");
        }
    }

    /**
     * Parse select query sql information
     *
     * @param selectBody
     * @return
     */
    private static SelectSqlInfo parseBySelectBody(SelectBody selectBody) {
        // Simple select query
        if (selectBody instanceof PlainSelect) {
            SelectSqlInfo sqlInfo = new SelectSqlInfo(selectBody);
            PlainSelect plainSelect = (PlainSelect) selectBody;
            FromItem fromItem = plainSelect.getFromItem();
            // Parse aliasName
            if (fromItem.getAlias() != null) {
                sqlInfo.setFromTableAliasName(fromItem.getAlias().getName());
            }
            // Parse Table name
            if (fromItem instanceof Table) {
                // By the way of Table name from
                Table fromTable = (Table) fromItem;
                sqlInfo.setFromTableName(fromTable.getName());
            } else if (fromItem instanceof SubSelect) {
                // By subquery from
                SubSelect fromSubSelect = (SubSelect) fromItem;
                SelectSqlInfo subSqlInfo = JSqlParserUtils.parseBySelectBody(fromSubSelect.getSelectBody());
                sqlInfo.setFromSubSelect(subSqlInfo);
            }
            // Parse selectFields
            List<SelectItem> selectItems = plainSelect.getSelectItems();
            for (SelectItem selectItem : selectItems) {
                if (selectItem instanceof AllColumns || selectItem instanceof AllTableColumns) {
                    // AllField
                    sqlInfo.setSelectAll(true);
                    sqlInfo.setSelectFields(null);
                    sqlInfo.setRealSelectFields(null);
                    break;
                } else if (selectItem instanceof SelectExpressionItem) {
                    // Get a single Selected field name
                    SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;
                    Expression expression = selectExpressionItem.getExpression();
                    Alias alias = selectExpressionItem.getAlias();
                    JSqlParserUtils.handleExpression(sqlInfo, expression, alias);
                }
            }
            return sqlInfo;
        } else {
            log.warn("SelectBody of this type not yet handled: {}", selectBody.getClass().getName());
            throw new JeecgBootException("SelectBody of this type not yet handled");
        }
    }

    /**
     * Process query field expression
     *
     * @param sqlInfo
     * @param expression
     * @param alias      alias if present; pass null if none
     */
    private static void handleExpression(SelectSqlInfo sqlInfo, Expression expression, Alias alias) {
        // Process functional fields e.g. CONCAT(name,'(',age,')')
        if (expression instanceof Function) {
            JSqlParserUtils.handleFunctionExpression((Function) expression, sqlInfo);
            return;
        }
        // Process subqueries on fields
        if (expression instanceof SubSelect) {
            SubSelect subSelect = (SubSelect) expression;
            SelectSqlInfo subSqlInfo = JSqlParserUtils.parseBySelectBody(subSelect.getSelectBody());
            // Note: The subquery on the Field must only query one Field, otherwise an error will be reported, so you can safely merge it
            sqlInfo.getSelectFields().addAll(subSqlInfo.getSelectFields());
            sqlInfo.getRealSelectFields().addAll(subSqlInfo.getAllRealSelectFields());
            return;
        }
        // Do not process literals
        if (expression instanceof StringValue ||
                expression instanceof NullValue ||
                expression instanceof LongValue ||
                expression instanceof DoubleValue ||
                expression instanceof HexValue ||
                expression instanceof DateValue ||
                expression instanceof TimestampValue ||
                expression instanceof TimeValue
        ) {
            return;
        }

        // Selected field name
        String selectField = expression.toString();
        // ActualSelected field name
        String realSelectField = selectField;
        // Determine whether there is an alias
        if (alias != null) {
            selectField = alias.getName();
        }
        // Get the real Field name
        if (expression instanceof Column) {
            Column column = (Column) expression;
            realSelectField = column.getColumnName();
        }
        sqlInfo.addSelectField(selectField, realSelectField);
    }

    /**
     * Process functional field
     *
     * @param functionExp
     * @param sqlInfo
     */
    private static void handleFunctionExpression(Function functionExp, SelectSqlInfo sqlInfo) {
        List<Expression> expressions = functionExp.getParameters().getExpressions();
        for (Expression expression : expressions) {
            JSqlParserUtils.handleExpression(sqlInfo, expression, null);
        }
    }

}
