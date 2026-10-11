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
import org.jeecg.common.util.sqlparse.vo.SelectSqlInfo;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parse all Table name and Field classes
 */
@Slf4j
public class JSqlParserAllTableManager {

    private final String sql;
    private final Map<String, SelectSqlInfo> allTableMap = new HashMap<>();
    /**
     * Alias to actual table name.
     */
    private final Map<String, String> tableAliasMap = new HashMap<>();

    /**
     * Parsed sql
     */
    private String parsedSql = null;

    JSqlParserAllTableManager(String selectSql) {
        this.sql = selectSql;
    }

    /**
     * Start parsing
     *
     * @return
     * @throws JSQLParserException
     */
    public Map<String, SelectSqlInfo> parse() throws JSQLParserException {
        // 1. Create a parser
        CCJSqlParserManager mgr = new CCJSqlParserManager();
        // 2. Use a parser to parse sql to generate a java class with a hierarchical structure
        Statement stmt = mgr.parse(new StringReader(this.sql));
        if (stmt instanceof Select) {
            Select selectStatement = (Select) stmt;
            SelectBody selectBody = selectStatement.getSelectBody();
            this.parsedSql = selectBody.toString();
            // 3. Parse the information of select query sql
            if (selectBody instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) selectBody;
                // 4. Merge fromItems
                List<FromItem> fromItems = new ArrayList<>();
                fromItems.add(plainSelect.getFromItem());
                // 4.1 Processing join tables
                List<Join> joins = plainSelect.getJoins();
                if (joins != null) {
                    joins.forEach(join -> fromItems.add(join.getRightItem()));
                }
                // 5. Process fromItems
                for (FromItem fromItem : fromItems) {
                    // 5.1 From through Table name
                    if (fromItem instanceof Table) {
                        this.addSqlInfoByTable((Table) fromItem);
                    }
                    // 5.2 From via subquery
                    else if (fromItem instanceof SubSelect) {
                        this.handleSubSelect((SubSelect) fromItem);
                    }
                }
                // 6. Parse selectFields
                List<SelectItem> selectItems = plainSelect.getSelectItems();
                for (SelectItem selectItem : selectItems) {
                    // 6.1 All fields are queried
                    if (selectItem instanceof AllColumns) {
                        // When selectItem is AllColumns, fromItem must be Table
                        String tableName = plainSelect.getFromItem(Table.class).getName();
                        // This must not be empty, because when parsing fromItem, the Table name has been added to allTableMap
                        SelectSqlInfo sqlInfo = this.allTableMap.get(tableName);
                        assert sqlInfo != null;
                        // Select all fields
                        sqlInfo.setSelectAll(true);
                        sqlInfo.setSelectFields(null);
                        sqlInfo.setRealSelectFields(null);
                    }
                    // 6.2 Query all Fields with table aliases (u.*)
                    else if (selectItem instanceof AllTableColumns) {
                        AllTableColumns allTableColumns = (AllTableColumns) selectItem;
                        String aliasName = allTableColumns.getTable().getName();
                        // Get table name by alias
                        String tableName = this.tableAliasMap.get(aliasName);
                        if (tableName == null) {
                            tableName = aliasName;
                        }
                        SelectSqlInfo sqlInfo = this.allTableMap.get(tableName);
                        // If it is empty, it means that the Field is obtained through a subquery, so it does not need to be processed. Only the actual table needs to be processed.
                        if (sqlInfo != null) {
                            // Select all fields
                            sqlInfo.setSelectAll(true);
                            sqlInfo.setSelectFields(null);
                            sqlInfo.setRealSelectFields(null);
                        }
                    }
                    // 6.3 Various Field expression processing
                    else if (selectItem instanceof SelectExpressionItem) {
                        SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;
                        Expression expression = selectExpressionItem.getExpression();
                        Alias alias = selectExpressionItem.getAlias();
                        this.handleExpression(expression, alias, plainSelect.getFromItem());
                    }
                }
            } else {
                log.warn("SelectBody of this type not yet handled: {}", selectBody.getClass().getName());
                throw new JeecgBootException("SelectBody of this type not yet handled");
            }
        } else {
            // Non-SELECT SQL is left unchanged
            throw new JeecgBootException("Non-select query SQL; not processed");
        }
        return this.allTableMap;
    }

    /**
     * Process subquery
     *
     * @param subSelect
     */
    private void handleSubSelect(SubSelect subSelect) {
        try {
            String subSelectSql = subSelect.getSelectBody().toString();
            // Recursive call parsing
            Map<String, SelectSqlInfo> map = JSqlParserUtils.parseAllSelectTable(subSelectSql);
            if (map != null) {
                this.assignMap(map);
            }
        } catch (Exception e) {
            log.error("Failed to parse subquery", e);
        }
    }

    /**
     * Process query field expression
     *
     * @param expression
     */
    private void handleExpression(Expression expression, Alias alias, FromItem fromItem) {
        // Process functional fields e.g. CONCAT(name,'(',age,')')
        if (expression instanceof Function) {
            Function functionExp = (Function) expression;
            List<Expression> expressions = functionExp.getParameters().getExpressions();
            for (Expression expItem : expressions) {
                this.handleExpression(expItem, null, fromItem);
            }
            return;
        }
        // Process subqueries on fields
        if (expression instanceof SubSelect) {
            this.handleSubSelect((SubSelect) expression);
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

        // ProcessField
        if (expression instanceof Column) {
            Column column = (Column) expression;
            // Selected field name
            String fieldName = column.getColumnName();
            String aliasName = fieldName;
            if (alias != null) {
                aliasName = alias.getName();
            }
            String tableName;
            if (column.getTable() != null) {
                // Get sqlInfo through the Table name of the column
                // For example user.name, tableName here is user
                tableName = column.getTable().getName();
                // It may be an alias and needs to be converted to a real Table name.
                if (this.tableAliasMap.get(tableName) != null) {
                    tableName = this.tableAliasMap.get(tableName);
                }
            } else {
                // When the column table is empty, it means the Field in fromItem
                tableName = ((Table) fromItem).getName();
            }
            SelectSqlInfo $sqlInfo = this.allTableMap.get(tableName);
            if ($sqlInfo != null) {
                $sqlInfo.addSelectField(aliasName, fieldName);
            } else {
                log.warn("Unexpected situation: SelectSqlInfo for table name {} not found", tableName);
            }
        }
    }

    /**
     * Add sqlInfo by table name
     *
     * @param table
     */
    private void addSqlInfoByTable(Table table) {
        String tableName = table.getName();
        // Parse aliasName
        if (table.getAlias() != null) {
            this.tableAliasMap.put(table.getAlias().getName(), tableName);
        }
        SelectSqlInfo sqlInfo = new SelectSqlInfo(this.parsedSql);
        sqlInfo.setFromTableName(table.getName());
        this.allTableMap.put(sqlInfo.getFromTableName(), sqlInfo);
    }

    /**
     * Merge map
     *
     * @param source
     */
    private void assignMap(Map<String, SelectSqlInfo> source) {
        for (Map.Entry<String, SelectSqlInfo> entry : source.entrySet()) {
            SelectSqlInfo sqlInfo = this.allTableMap.get(entry.getKey());
            if (sqlInfo == null) {
                this.allTableMap.put(entry.getKey(), entry.getValue());
            } else {
                // merge
                if (sqlInfo.getSelectFields() == null) {
                    sqlInfo.setSelectFields(entry.getValue().getSelectFields());
                } else {
                    sqlInfo.getSelectFields().addAll(entry.getValue().getSelectFields());
                }
                if (sqlInfo.getRealSelectFields() == null) {
                    sqlInfo.setRealSelectFields(entry.getValue().getRealSelectFields());
                } else {
                    sqlInfo.getRealSelectFields().addAll(entry.getValue().getRealSelectFields());
                }
            }
        }
    }

}
