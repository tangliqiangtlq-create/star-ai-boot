package org.jeecg.common.handler;

import com.alibaba.fastjson.JSONObject;

/**
 * Value filling rule interface
 *
 * @author Yan_Dong
 * If you want to use the value filling rule function, the rule implementation class must implement this interface
 */
public interface IFillRuleHandler {

    /**
     * Fill value rule
     * @param params fixed page configuration parameters
     * @param formData  dynamic form parameters
     * @return
     */
    public Object execute(JSONObject params, JSONObject formData);

}

