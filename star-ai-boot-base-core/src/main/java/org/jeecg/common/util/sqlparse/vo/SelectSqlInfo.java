package org.jeecg.common.util.sqlparse.vo;

import lombok.Data;
import net.sf.jsqlparser.statement.select.SelectBody;

import java.util.HashSet;
import java.util.Set;

/**
 * select query sql information
 */
@Data
public class SelectSqlInfo {

    /**
     * Queried table name; null if this is a subquery
     */
    private String fromTableName;
    /**
     * table alias
     */
    private String fromTableAliasName;
    /**
     * Table information obtained through subquery, for example: select name from (select * from user) u
     * null if not a subquery
     */
    private SelectSqlInfo fromSubSelect;
    /**
     * Queried field set; null if *, alias if alias is set
     */
    private Set<String> selectFields;
    /**
     * Actual query field set; null if *, original field name if alias is set
     */
    private Set<String> realSelectFields;
    /**
     * Whether all fields are queried
     */
    private boolean selectAll;

    /**
     * SQL after parsing (keywords are all uppercase)
     */
    private final String parsedSql;

    public SelectSqlInfo(String parsedSql) {
        this.parsedSql = parsedSql;
    }

    public SelectSqlInfo(SelectBody selectBody) {
        this.parsedSql = selectBody.toString();
    }

    public void addSelectField(String selectField, String realSelectField) {
        if (this.selectFields == null) {
            this.selectFields = new HashSet<>();
        }
        if (this.realSelectFields == null) {
            this.realSelectFields = new HashSet<>();
        }
        this.selectFields.add(selectField);
        this.realSelectFields.add(realSelectField);
    }

    /**
     * Get all fields, including those in subqueries.
     *
     * @return
     */
    public Set<String> getAllRealSelectFields() {
        Set<String> fields = new HashSet<>();
        // To get all Fields recursively, give an intuitive method called:
        this.recursiveGetAllFields(this, fields);
        return fields;
    }

    /**
     * Get all Fields recursively
     */
    private void recursiveGetAllFields(SelectSqlInfo sqlInfo, Set<String> fields) {
        if (!sqlInfo.isSelectAll() && sqlInfo.getRealSelectFields() != null) {
            fields.addAll(sqlInfo.getRealSelectFields());
        }
        if (sqlInfo.getFromSubSelect() != null) {
            recursiveGetAllFields(sqlInfo.getFromSubSelect(), fields);
        }
    }

    @Override
    public String toString() {
        return "SelectSqlInfo{" +
                "fromTableName='" + fromTableName + '\'' +
                ", fromSubSelect=" + fromSubSelect +
                ", aliasName='" + fromTableAliasName + '\'' +
                ", selectFields=" + selectFields +
                ", realSelectFields=" + realSelectFields +
                ", selectAll=" + selectAll +
                "}";
    }

}
