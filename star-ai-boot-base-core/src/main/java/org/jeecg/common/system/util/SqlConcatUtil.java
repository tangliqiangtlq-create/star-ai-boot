package org.jeecg.common.system.util;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.DataBaseConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.query.QueryRuleEnum;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.oConvertUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: query filter,SQLThe splicing writing method is split into independent tool classes
 * @author:qinfeng
 * @date 20230904
 */
@Slf4j
public class SqlConcatUtil {

    /**
     * Get single query condition value
     * @param rule
     * @param field
     * @param value
     * @param isString
     * @return
     */
    public static String getSingleSqlByRule(QueryRuleEnum rule,String field,Object value,boolean isString) {
        return getSingleSqlByRule(rule, field, value, isString, null);
    }
    
    /**
     * Build report query conditions; supports multiple data sources
     * @param field
     * @param alias
     * @param value
     * @param isString
     * @param dataBaseType
     * @return
     */
    public static String getSingleQueryConditionSql(String field,String alias,Object value,boolean isString, String dataBaseType) {
        if (value == null) {
            return "";
        }
        field =  alias+oConvertUtils.camelToUnderline(field);
        QueryRuleEnum rule = QueryGenerator.convert2Rule(value);
        return getSingleSqlByRule(rule, field, value, isString, dataBaseType);
    }

    /**
     * Get single query condition value
     * @param rule
     * @param field
     * @param value
     * @param isString
     * @param dataBaseType
     * @return
     */
    private static String getSingleSqlByRule(QueryRuleEnum rule,String field,Object value,boolean isString, String dataBaseType) {
        String res = "";
        switch (rule) {
            case GT:
                res =field+rule.getValue()+getFieldConditionValue(value, isString, dataBaseType);
                break;
            case GE:
                res = field+rule.getValue()+getFieldConditionValue(value, isString, dataBaseType);
                break;
            case LT:
                res = field+rule.getValue()+getFieldConditionValue(value, isString, dataBaseType);
                break;
            case LE:
                res = field+rule.getValue()+getFieldConditionValue(value, isString, dataBaseType);
                break;
            case EQ:
                res = field+rule.getValue()+getFieldConditionValue(value, isString, dataBaseType);
                break;
            case EQ_WITH_ADD:
                res = field+" = "+getFieldConditionValue(value, isString, dataBaseType);
                break;
            case NE:
                res = field+" <> "+getFieldConditionValue(value, isString, dataBaseType);
                break;
            case IN:
                res = field + " in "+getInConditionValue(value, isString);
                break;
            case LIKE:
                res = field + " like "+getLikeConditionValue(value, QueryRuleEnum.LIKE);
                break;
            case LEFT_LIKE:
                res = field + " like "+getLikeConditionValue(value, QueryRuleEnum.LEFT_LIKE);
                break;
            case RIGHT_LIKE:
                res = field + " like "+getLikeConditionValue(value, QueryRuleEnum.RIGHT_LIKE);
                break;
            default:
                res = field+" = "+getFieldConditionValue(value, isString, dataBaseType);
                break;
        }
        return res;
    }

    /**
     * Get queryConditionvalue
     * @param value
     * @param isString
     * @param dataBaseType
     * @return
     */
    private static String getFieldConditionValue(Object value,boolean isString, String dataBaseType) {
        String str = value.toString().trim();
        if(str.startsWith(SymbolConstant.EXCLAMATORY_MARK)) {
            str = str.substring(1);
        }else if(str.startsWith(QueryRuleEnum.GE.getValue())) {
            str = str.substring(2);
        }else if(str.startsWith(QueryRuleEnum.LE.getValue())) {
            str = str.substring(2);
        }else if(str.startsWith(QueryRuleEnum.GT.getValue())) {
            str = str.substring(1);
        }else if(str.startsWith(QueryRuleEnum.LT.getValue())) {
            str = str.substring(1);
        }else if(str.indexOf(QueryGenerator.QUERY_COMMA_ESCAPE)>0) {
            str = str.replaceAll("\\+\\+", SymbolConstant.COMMA);
        }
        if(dataBaseType==null){
            dataBaseType = getDbType();
        }
        if(isString) {
            if(DataBaseConstant.DB_TYPE_SQLSERVER.equals(dataBaseType)){
                return " N'"+str+"' ";
            }else{
                return " '"+str+"' ";
            }
        }else {
            // If it is not a string there is a special case popupThe call follows this logic. The parameters passed may be "‘admin’”in this format
            if(DataBaseConstant.DB_TYPE_SQLSERVER.equals(dataBaseType) && str.endsWith(SymbolConstant.SINGLE_QUOTATION_MARK) && str.startsWith(SymbolConstant.SINGLE_QUOTATION_MARK)){
                return " N"+str;
            }
            return value.toString();
        }
    }

    private static String getInConditionValue(Object value,boolean isString) {
        //update-begin-author:taoyan date:20210628 for: QueryConditionIf you enter,lead tosqlReport an error
        String[] temp = value.toString().split(",");
        if(temp.length==0){
            return "('')";
        }
        if(isString) {
            List<String> res = new ArrayList<>();
            for (String string : temp) {
                if(DataBaseConstant.DB_TYPE_SQLSERVER.equals(getDbType())){
                    res.add("N'"+string+"'");
                }else{
                    res.add("'"+string+"'");
                }
            }
            return "("+String.join("," ,res)+")";
        }else {
            return "("+value.toString()+")";
        }
        //update-end-author:taoyan date:20210628 for: QueryConditionIf you enter,lead tosqlReport an error
    }

    /**
     * First determine left or right fuzzy match from the value;
     * if the value has no wildcard (* or %), then apply ruleEnum.
     * @param value
     * @param ruleEnum
     * @return
     */
    private static String getLikeConditionValue(Object value, QueryRuleEnum ruleEnum) {
        String str = value.toString().trim();
        if(str.startsWith(SymbolConstant.ASTERISK) && str.endsWith(SymbolConstant.ASTERISK)) {
            if(DataBaseConstant.DB_TYPE_SQLSERVER.equals(getDbType())){
                return "N'%"+str.substring(1,str.length()-1)+"%'";
            }else{
                return "'%"+str.substring(1,str.length()-1)+"%'";
            }
        }else if(str.startsWith(SymbolConstant.ASTERISK)) {
            if(DataBaseConstant.DB_TYPE_SQLSERVER.equals(getDbType())){
                return "N'%"+str.substring(1)+"'";
            }else{
                return "'%"+str.substring(1)+"'";
            }
        }else if(str.endsWith(SymbolConstant.ASTERISK)) {
            if(DataBaseConstant.DB_TYPE_SQLSERVER.equals(getDbType())){
                return "N'"+str.substring(0,str.length()-1)+"%'";
            }else{
                return "'"+str.substring(0,str.length()-1)+"%'";
            }
        }else {
            if(str.indexOf(SymbolConstant.PERCENT_SIGN)>=0) {
                if(DataBaseConstant.DB_TYPE_SQLSERVER.equals(getDbType())){
                    if(str.startsWith(SymbolConstant.SINGLE_QUOTATION_MARK) && str.endsWith(SymbolConstant.SINGLE_QUOTATION_MARK)){
                        return "N"+str;
                    }else{
                        return "N"+"'"+str+"'";
                    }
                }else{
                    if(str.startsWith(SymbolConstant.SINGLE_QUOTATION_MARK) && str.endsWith(SymbolConstant.SINGLE_QUOTATION_MARK)){
                        return str;
                    }else{
                        return "'"+str+"'";
                    }
                }
            }else {

                //update-begin-author:taoyan date:2022-6-30 for: issues/3810 Data permission rule issues
                // Go here to explain valueIdentification without any fuzzy query(*or%)
                if (ruleEnum == QueryRuleEnum.LEFT_LIKE) {
                    if (DataBaseConstant.DB_TYPE_SQLSERVER.equals(getDbType())) {
                        return "N'%" + str + "'";
                    } else {
                        return "'%" + str + "'";
                    }
                } else if (ruleEnum == QueryRuleEnum.RIGHT_LIKE) {
                    if (DataBaseConstant.DB_TYPE_SQLSERVER.equals(getDbType())) {
                        return "N'" + str + "%'";
                    } else {
                        return "'" + str + "%'";
                    }
                } else {
                    if (DataBaseConstant.DB_TYPE_SQLSERVER.equals(getDbType())) {
                        return "N'%" + str + "%'";
                    } else {
                        return "'%" + str + "%'";
                    }
                }
                //update-end-author:taoyan date:2022-6-30 for: issues/3810 Data permission rule issues

            }
        }
    }

    /**
     * GetSystemDatabaseType
     */
    private static String getDbType() {
        return CommonUtils.getDatabaseType();
    }
    
}
