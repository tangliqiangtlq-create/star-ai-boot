package org.jeecg.common.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.handler.IFillRuleHandler;


/**
 * Rule valueAutomatically generate tool classes
 *
 * @author qinfeng
 * @Example: Automatically generate order number; automatically generateCurrent date
 */
@Slf4j
public class FillRuleUtil {

    /**
     * @param ruleCode ruleCode
     * @return
     */
    @SuppressWarnings("unchecked")
    public static Object executeRule(String ruleCode, JSONObject formData) {
        if (!StringUtils.isEmpty(ruleCode)) {
            try {
                // Get Service
                ServiceImpl impl = (ServiceImpl) SpringContextUtils.getBean("sysFillRuleServiceImpl");
                // according to ruleCode Query out the entity
                QueryWrapper queryWrapper = new QueryWrapper();
                queryWrapper.eq("rule_code", ruleCode);
                JSONObject entity = JSON.parseObject(JSON.toJSONString(impl.getOne(queryWrapper)));
                if (entity == null) {
                    log.warn("Fill rule: " + ruleCode + " does not exist");
                    return null;
                }
                // Get necessary parameters
                String ruleClass = entity.getString("ruleClass");
                JSONObject params = entity.getJSONObject("ruleParams");
                if (params == null) {
                    params = new JSONObject();
                }
                if (formData == null) {
                    formData = new JSONObject();
                }
                // Execute methods in configured classes through reflection
                IFillRuleHandler ruleHandler = (IFillRuleHandler) Class.forName(ruleClass).newInstance();
                return ruleHandler.execute(params, formData);
            } catch (Exception e) {
                log.error("operation failed", e);
            }
        }
        return null;
    }
}
