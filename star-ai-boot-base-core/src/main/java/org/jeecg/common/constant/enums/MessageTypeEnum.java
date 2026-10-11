package org.jeecg.common.constant.enums;

import org.jeecg.common.system.annotation.EnumDict;
import org.jeecg.common.system.vo.DictModel;

import java.util.ArrayList;
import java.util.List;

/**
 * MessageType
 * @author: jeecg-boot
 */
@EnumDict("messageType")
public enum MessageTypeEnum {

    /** SystemMessage */
    XT("system",  "SystemMessage"),
    /** Email message */
    YJ("email",  "Email message"),
    /** DingTalk news */
    DD("dingtalk", "DingTalk message"),
    /** WeCom (Enterprise WeChat) */
    QYWX("wechat_enterprise", "WeCom");

    MessageTypeEnum(String type, String note){
        this.type = type;
        this.note = note;
    }

    /**
     * MessageType
     */
    String type;

    /**
     * Type description
     */
    String note;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }


    /**
     * GetDictionaryData
     * @return
     */
    public static List<DictModel> getDictList(){
        List<DictModel> list = new ArrayList<>();
        DictModel dictModel = null;
        for(MessageTypeEnum e: MessageTypeEnum.values()){
            dictModel = new DictModel();
            dictModel.setValue(e.getType());
            dictModel.setText(e.getNote());
            list.add(dictModel);
        }
        return list;
    }

    /**
     * Get enum by type
     *
     * @param type
     * @return
     */
    public static MessageTypeEnum valueOfType(String type) {
        for (MessageTypeEnum e : MessageTypeEnum.values()) {
            if (e.getType().equals(type)) {
                return e;
            }
        }
        return null;
    }

}
