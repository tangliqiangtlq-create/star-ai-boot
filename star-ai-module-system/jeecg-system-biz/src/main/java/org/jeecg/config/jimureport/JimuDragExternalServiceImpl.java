package org.jeecg.config.jimureport;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.dto.LogDTO;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.drag.service.IOnlDragExternalService;
import org.jeecg.modules.drag.vo.DragDictModel;
import org.jeecg.modules.drag.vo.DragLogDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: DictionaryProcess
 * @Author: lsq
 * @Date:2023-01-09
 * @Version:V1.0
 */
@Slf4j
@Service("onlDragExternalServiceImpl")
public class JimuDragExternalServiceImpl implements IOnlDragExternalService {

    @Autowired
    @Lazy
    private BaseCommonService baseCommonService;

    @Autowired
    @Lazy
    private ISysBaseAPI sysBaseApi;
    /**
     *  Query multiple dict items by multiple dict codes
     * @param codeList
     * @return key = dictCode; value = corresponding dict items
     */
    @Override
    public Map<String, List<DragDictModel>> getManyDictItems(List<String> codeList) {
        Map<String, List<DragDictModel>> manyDragDictItems  = new HashMap<>();
        Map<String, List<DictModel>> dictItemsMap = sysBaseApi.getManyDictItems(codeList);
        dictItemsMap.forEach((k,v)->{
            List<DragDictModel> dictItems  = new ArrayList<>();
            v.forEach(dictItem->{
                DragDictModel dictModel = new DragDictModel();
                BeanUtils.copyProperties(dictItem,dictModel);
                dictItems.add(dictModel);
            });
            manyDragDictItems.put(k,dictItems);
        });
        return manyDragDictItems;
    }

    /**
     *
     * @param dictCode
     * @return
     */
    @Override
    public List<DragDictModel> getDictItems(String dictCode) {
        List<DragDictModel> dictItems  = new ArrayList<>();
        if(oConvertUtils.isNotEmpty(dictCode)){
            List<DictModel> dictItemsList = sysBaseApi.getDictItems(dictCode);
            dictItemsList.forEach(dictItem->{
                DragDictModel dictModel = new DragDictModel();
                BeanUtils.copyProperties(dictItem,dictModel);
                dictItems.add(dictModel);
            });
        }
        return dictItems;
    }

    /**
     * AddLog
     * @param dragLogDTO
     */
    @Override
    public void addLog(DragLogDTO dragLogDTO) {
        if(oConvertUtils.isNotEmpty(dragLogDTO)){
            LogDTO dto = new LogDTO();
            BeanUtils.copyProperties(dragLogDTO,dto);
            baseCommonService.addLog(dto);
        }
    }

    /**
     * SaveLog
     * @param logMsg
     * @param logType
     * @param operateType
     */
    @Override
    public void addLog(String logMsg, int logType, int operateType) {
        baseCommonService.addLog(logMsg,logType,operateType);
    }
}