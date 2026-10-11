//package org.jeecg.common.util.superSearch;
//
//import org.jeecg.common.util.oConvertUtils;
//
///**
// * Query rules constants
// * @Author Scott
// * @Date February 14, 2019
// */
//public enum QueryRuleEnum {
//
//    /**Query rules greater than*/
//    GT(">","greater than"),
//    /**Query rules greater than or equal to*/
//    GE(">=","greater than or equal to"),
//    /**Query rules less than*/
//    LT("<","less than"),
//    /**Query rules less than or equal to*/
//    LE("<=","Less than or equal to"),
//    /**Query rule equals*/
//    EQ("=","equal to"),
//    /**Query rules are not equal*/
//    NE("!=","not equal to"),
//    /**Query rules include*/
//    IN("IN","includes"),
//    /**Query rules fully fuzzy*/
//    LIKE("LIKE","Full Blur"),
//    /**Query rules left fuzzy*/
//    LEFT_LIKE("LEFT_LIKE","Left blur"),
//    /**Query rules right fuzzy*/
//    RIGHT_LIKE("RIGHT_LIKE","Right blur"),
//    /**Query rules Custom SQL fragment*/
//    SQL_RULES("EXTEND_SQL","Customized SQL fragment");
//
//    private String value;
//
//    private String msg;
//
//    QueryRuleEnum(String value, String msg){
//        this.value = value;
//        this.msg = msg;
//    }
//
//    public String getValue() {
//        return value;
//    }
//
//    public void setValue(String value) {
//        this.value = value;
//    }
//
//    public String getMsg() {
//        return msg;
//    }
//
//    public void setMsg(String msg) {
//        this.msg = msg;
//    }
//
//    public static QueryRuleEnum getByValue(String value){
//    	if(oConvertUtils.isEmpty(value)) {
//    		return null;
//    	}
//        for(QueryRuleEnum val :values()){
//            if (val.getValue().equals(value)){
//                return val;
//            }
//        }
//        return  null;
//    }
//}
