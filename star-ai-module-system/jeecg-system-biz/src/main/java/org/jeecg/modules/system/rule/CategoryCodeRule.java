package org.jeecg.modules.system.rule;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.handler.IFillRuleHandler;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.YouBianCodeUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.system.entity.SysCategory;
import org.jeecg.modules.system.mapper.SysCategoryMapper;

import java.util.List;

/**
 * @Author scott
 * @Date 2019/12/9 11:32
 * @Description: Category-dict code generation rule
 */
@Slf4j
public class CategoryCodeRule implements IFillRuleHandler {

    public static final String ROOT_PID_VALUE = "0";

    @Override
    public Object execute(JSONObject params, JSONObject formData) {
        log.info("System custom encoding rule [category_code_rule], params: {}, formData: {}", params, formData);

        String categoryPid = ROOT_PID_VALUE;
        String categoryCode = null;

        if (formData != null && formData.size() > 0) {
            Object obj = formData.get("pid");
            if (oConvertUtils.isNotEmpty(obj)) {
                categoryPid = obj.toString();
            }
        } else {
            if (params != null) {
                Object obj = params.get("pid");
                if (oConvertUtils.isNotEmpty(obj)) {
                    categoryPid = obj.toString();
                }
            }
        }

        /*
         * Three scenarios:
         * 1. No data in database: call YouBianCodeUtil.getNextYouBianCode(null);
         * 2. Add child node with no siblings: YouBianCodeUtil.getSubYouBianCode(parentCode,null);
         * 3. Add child node with siblings: YouBianCodeUtil.getNextYouBianCode(lastCode);
         * */
        //Find peers to determine previous max code
        SysCategoryMapper baseMapper = (SysCategoryMapper) SpringContextUtils.getBean("sysCategoryMapper");
        //update-begin---author:wangshuai ---date:20230424  for: [issues/4846] tenant admin add category-dict error under SAAS ------------
        Page<SysCategory> page = new Page<>(1,1);
        List<SysCategory> list = baseMapper.getMaxCategoryCodeByPage(page,categoryPid);
        //update-end---author:wangshuai ---date:20230424  for: [issues/4846] tenant admin add category-dict error under SAAS ------------
        if (list == null || list.size() == 0) {
            if (ROOT_PID_VALUE.equals(categoryPid)) {
                //Case 1
                categoryCode = YouBianCodeUtil.getNextYouBianCode(null);
            } else {
                //Case 2
                //update-begin---author:wangshuai ---date:20230424  for: [issues/4846] tenant admin add category-dict error under SAAS ------------
                SysCategory parent = (SysCategory) baseMapper.selectSysCategoryById(categoryPid);
                //update-end---author:wangshuai ---date:20230424  for: [issues/4846] tenant admin add category-dict error under SAAS ------------
                categoryCode = YouBianCodeUtil.getSubYouBianCode(parent.getCode(), null);
            }
        } else {
            //Case 3
            categoryCode = YouBianCodeUtil.getNextYouBianCode(list.get(0).getCode());
        }
        return categoryCode;
    }
}
