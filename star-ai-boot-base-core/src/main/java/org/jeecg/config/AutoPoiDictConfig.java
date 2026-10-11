package org.jeecg.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Resource;

import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.util.oConvertUtils;
import org.jeecgframework.dict.service.AutoPoiDictServiceI;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Description: AutoPoi Excel annotation supports dictionary parameter settings
 *  Example: @Excel(name = "Gender", width = 15, dicCode = "sex")
 * 1. On export, values 1,2 are translated per dictionary config to Male, Female;
 * 2. On import, Male, Female are translated to 1,2 for DB storage;
 * 
 * @Author:scott 
 * @since：2019-04-09 
 * @Version:1.0
 */
@Slf4j
@Service
public class AutoPoiDictConfig implements AutoPoiDictServiceI {
	final static String EXCEL_SPLIT_TAG = "_";
	final static String TEMP_EXCEL_SPLIT_TAG = "---";

	@Lazy
	@Resource
	private CommonAPI commonApi;

	/**
	 * Query easypoi dict text via dictionary lookup
	 * 
	 * @Author:scott 
	 * @since: 2019-04-09
	 * @return
	 */
	@Override
	public String[] queryDict(String dicTable, String dicCode, String dicText) {
		List<String> dictReplaces = new ArrayList<String>();
		List<DictModel> dictList = null;
		// step.1 use system dictionary if dictionary table is missing
		if (oConvertUtils.isEmpty(dicTable)) {
			dictList = commonApi.queryDictItemsByCode(dicCode);
		} else {
			try {
				dicText = oConvertUtils.getString(dicText, dicCode);
				dictList = commonApi.queryTableDictItemsByCode(dicTable, dicText, dicCode);
			} catch (Exception e) {
				log.error("operation failed", e);
			}
		}


		for (DictModel t : dictList) {
			//update-begin---author:liusq   Date:20230517  for: [issues/4917] excel export exception---
			if(t!=null && t.getText()!=null && t.getValue()!=null){
			//update-end---author:liusq     Date:20230517  for: [issues/4917] excel export exception---
				//update-begin---author:scott   Date:20211220  for: [issues/I4MBB3] import fails to parse @Excel dicText when value contains underscore---
				if(t.getValue().contains(EXCEL_SPLIT_TAG)){
					String val = t.getValue().replace(EXCEL_SPLIT_TAG,TEMP_EXCEL_SPLIT_TAG);
					dictReplaces.add(t.getText() + EXCEL_SPLIT_TAG + val);
				}else{
					dictReplaces.add(t.getText() + EXCEL_SPLIT_TAG + t.getValue());
				}
				//update-end---author:20211220     Date:20211220  for: [issues/I4MBB3] import fails to parse @Excel dicText when value contains underscore---
			}
		}
		if (dictReplaces != null && dictReplaces.size() != 0) {
			log.info("---AutoPoi--Get_DB_Dict------"+ dictReplaces.toString());
			return dictReplaces.toArray(new String[dictReplaces.size()]);
		}
		return null;
	}
}
