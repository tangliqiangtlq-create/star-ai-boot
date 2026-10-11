package org.jeecg.common.constant.enums;

import org.jeecg.common.system.annotation.EnumDict;
import org.jeecg.common.system.vo.DictModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Message jump【vue3】
 * @Author taoYan
 * @Date 2022/8/19 20:41
 **/
@EnumDict("messageHref")
public enum Vue3MessageHrefEnum {

    /**
     * Process reminder
     */
    BPM("bpm", "/task/myHandleTaskInfo"),
    
    /**
     * SystemMessageNotification
     */
    BPM_SYSTEM_MSG("bpm_msg_node", ""),
    
    /**
     * Process CC task
     */
    BPM_VIEW("bpm_cc", "/task/myHandleTaskInfo"),

    /**
     * Node notification
     */
    BPM_TASK("bpm_task", "/task/myHandleTaskInfo"),

    /**
     * Email message
     */
    EMAIL("email", "/eoa/email");
    
    String busType;
    
    String path;

    Vue3MessageHrefEnum(String busType, String path) {
        this.busType = busType;
        this.path = path;
    }

    public String getBusType() {
        return busType;
    }

    public String getPath() {
        return path;
    }

    /**
     * GetDictionaryData
     * @return
     */
    public static List<DictModel> getDictList(){
        List<DictModel> list = new ArrayList<>();
        DictModel dictModel = null;
        for(Vue3MessageHrefEnum e: Vue3MessageHrefEnum.values()){
            dictModel = new DictModel();
            dictModel.setValue(e.getBusType());
            dictModel.setText(e.getPath());
            list.add(dictModel);
        }
        return list;
    }
    
}
