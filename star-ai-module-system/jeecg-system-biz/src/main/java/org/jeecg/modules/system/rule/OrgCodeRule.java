package org.jeecg.modules.system.rule;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.netty.util.internal.StringUtil;
import org.jeecg.common.handler.IFillRuleHandler;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.YouBianCodeUtil;
import org.jeecg.modules.system.entity.SysDepart;
import org.jeecg.modules.system.service.ISysDepartService;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author scott
 * @Date 2019/12/9 11:33
 * @Description: Org-code generation rule
 */
public class OrgCodeRule implements IFillRuleHandler {

    @Override
    public Object execute(JSONObject params, JSONObject formData) {
        ISysDepartService sysDepartService = (ISysDepartService) SpringContextUtils.getBean("sysDepartServiceImpl");

        LambdaQueryWrapper<SysDepart> query = new LambdaQueryWrapper<SysDepart>();
        LambdaQueryWrapper<SysDepart> query1 = new LambdaQueryWrapper<SysDepart>();
        // Create a List to hold all queried SysDepart objects
        List<SysDepart> departList = new ArrayList<>();
        String[] strArray = new String[2];
        //Define department type
        String orgType = "";
        // Define new code string
        String newOrgCode = "";
        // Define old code string
        String oldOrgCode = "";

        String parentId = null;
        if (formData != null && formData.size() > 0) {
            Object obj = formData.get("parentId");
            if (obj != null) {
                parentId = obj.toString();
            }
        } else {
            if (params != null) {
                Object obj = params.get("parentId");
                if (obj != null) {
                    parentId = obj.toString();
                }
            }
        }

        //If top level, query sibling org_codes, generate code via util and return
        if (StringUtil.isNullOrEmpty(parentId)) {
            // First check whether table is empty; if empty return initial code
            //Get department with max code
            //update-begin---author:wangshuai ---date:20230211  for: [QQYUN-4209] cannot create dept under tenant isolation ------------
            Page<SysDepart> page = new Page<>(1,1);
            IPage<SysDepart> pageList = sysDepartService.getMaxCodeDepart(page,"");
            List<SysDepart> records = pageList.getRecords();
            if (null==records || records.size()==0) {
            //update-end---author:wangshuai ---date:20230211  for: [QQYUN-4209] cannot create dept under tenant isolation ------------
                strArray[0] = YouBianCodeUtil.getNextYouBianCode(null);
                strArray[1] = "1";
                return strArray;
            } else {
                SysDepart depart = records.get(0);
                oldOrgCode = depart.getOrgCode();
                orgType = depart.getOrgType();
                newOrgCode = YouBianCodeUtil.getNextYouBianCode(oldOrgCode);
            }
        } else {//Else query sibling departments; two cases: has siblings or not
            //Get own department max orgCode info
            //update-begin---author:wangshuai ---date:20230211  for: [QQYUN-4209] cannot create dept under tenant isolation ------------
            Page<SysDepart> page = new Page<>(1,1);
            IPage<SysDepart> pageList = sysDepartService.getMaxCodeDepart(page,parentId);
            List<SysDepart> records = pageList.getRecords();
            // Query parent department
            SysDepart depart = sysDepartService.getDepartById(parentId);
            //update-end---author:wangshuai ---date:20230211  for: [QQYUN-4209] cannot create dept under tenant isolation ------------
            // Get parent department code
            String parentCode = depart.getOrgCode();
            // Derive current department type from parent type
            orgType = String.valueOf(Integer.valueOf(depart.getOrgType()) + 1);
            // Handle null sibling departments
            if (null == records || records.size()==0) {
                // Directly generate and return current department code
                newOrgCode = YouBianCodeUtil.getSubYouBianCode(parentCode, null);
            } else { //Handle case with sibling departments
                // Get sibling department codes via util
                String subCode = records.get(0).getOrgCode();
                // Return generated current department code
                newOrgCode = YouBianCodeUtil.getSubYouBianCode(parentCode, subCode);
            }
        }
        // Return array wrapping department code and type
        strArray[0] = newOrgCode;
        strArray[1] = orgType;
        return strArray;
    }
}
