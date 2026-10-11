package org.jeecg.common.system.query;

import org.jeecg.common.util.oConvertUtils;

/**
 * Query rules constants
 * @Author Scott
 * @Date 2019Year02moon14day
 */
public enum QueryRuleEnum {

    /**Query rules greater than*/
    GT(">","gt","Greater than"),
    /**Query rules greater than or equal to*/
    GE(">=","ge","Greater than or equal to"),
    /**Query rules less than*/
    LT("<","lt","Less than"),
    /**Query rules less than or equal to*/
    LE("<=","le","Less than or equal to"),
    /**Query rule equals*/
    EQ("=","eq","Equals"),
    /**Query rule is not equal to*/
    NE("!=","ne","Not equal to"),
    /**Query rules include*/
    IN("IN","in","Contains"),
    /**Query rules are completely fuzzy*/
    LIKE("LIKE","like","Full fuzzy match"),
    /**Query rules do not include fuzzy*/
    NOT_LIKE("NOT_LIKE","not_like","Does not fuzzy contain"),
    /**Query rules left fuzzy*/
    LEFT_LIKE("LEFT_LIKE","left_like","Left fuzzy match"),
    /**Query rules right fuzzy*/
    RIGHT_LIKE("RIGHT_LIKE","right_like","Right fuzzy match"),
    /**Query rule with plus sign equals*/
    EQ_WITH_ADD("EQWITHADD","eq_with_add","Plus-equals"),
    /**Query rules multi-word fuzzy matching(and)*/
    LIKE_WITH_AND("LIKEWITHAND","like_with_and","Multi-word fuzzy match — not used yet"),
    /**Query rules multi-word fuzzy matching(or)*/
    LIKE_WITH_OR("LIKEWITHOR","like_with_or","Multi-word fuzzy match (or)"),
    /**Query rules customizationSQLfragment*/
    SQL_RULES("USE_SQL_RULES","ext","Custom SQL fragment"),

    /** query worksheet */
    LINKAGE("LINKAGE","linkage","Query worksheet"),

    // ------- Form designer specific -------
    /**Query rules do not end with...*/
    NOT_LEFT_LIKE("NOT_LEFT_LIKE","not_left_like","Does not end with…"),
    /**Query rules do not start with...*/
    NOT_RIGHT_LIKE("NOT_RIGHT_LIKE","not_right_like","Does not start with…"),
    /** Value is empty */
    EMPTY("EMPTY","empty","Value is empty"),
    /** value is not empty */
    NOT_EMPTY("NOT_EMPTY","not_empty","Value is not empty"),
    /**Query rules do not include*/
    NOT_IN("NOT_IN","not_in","Does not contain"),
    /**Query rules multi-word matching*/
    ELE_MATCH("ELE_MATCH","elemMatch","Multi-word match"),
    /**Query rules range query*/
    RANGE("RANGE","range","Range query"),
    /**Query rules do not query within the range*/
    NOT_RANGE("NOT_RANGE","not_range","Not in range query"),
    /** Customizemongodbquery statement */
    CUSTOM_MONGODB("CUSTOM_MONGODB","custom_mongodb","Custom MongoDB query statement");
    // ------- Form designer specific -------

    private String value;
    
    private String condition; 

    private String msg;

    QueryRuleEnum(String value, String condition, String msg){
        this.value = value;
        this.condition = condition;
        this.msg = msg;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getCondition() {
		return condition;
	}

	public void setCondition(String condition) {
		this.condition = condition;
	}

	public static QueryRuleEnum getByValue(String value){
    	if(oConvertUtils.isEmpty(value)) {
    		return null;
    	}
        for(QueryRuleEnum val :values()){
            if (val.getValue().equals(value) || val.getCondition().equalsIgnoreCase(value)){
                return val;
            }
        }
        return  null;
    }
}
