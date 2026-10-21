package org.jeecg.modules.message.handle.impl;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.common.api.dto.message.MessageDTO;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.WebsocketConst;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.common.constant.enums.Vue3MessageHrefEnum;
import org.jeecg.modules.message.handle.ISendMsgHandle;
import org.jeecg.modules.message.websocket.WebSocket;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.Map;

/**
* @Description: Send system message
* @Author: wangshuai
* @Date: March 22, 2022 18:48:20
*/
@Component("systemSendMsgHandle")
public class SystemSendMsgHandle implements ISendMsgHandle {

    public static final String FROM_USER="system";



    /**
     * This method sends 3 types of messages: system message, WeChat Work, DingTalk
     * @param esReceiver sender
     * @param esTitle title
     * @param esContent content
     */
    @Override
    public void sendMsg(String esReceiver, String esTitle, String esContent) {
        if(oConvertUtils.isEmpty(esReceiver)){
            throw  new JeecgBootException("Recipient cannot be empty");
        }
        ISysBaseAPI sysBaseApi = SpringContextUtils.getBean(ISysBaseAPI.class);
        MessageDTO messageDTO = new MessageDTO(FROM_USER,esReceiver,esTitle,esContent);
    }

    /**
     * Send system message only.
     * @param messageDTO
     */
    @Override
    public void sendMessage(MessageDTO messageDTO) {
        String title = messageDTO.getTitle();
        String content = messageDTO.getContent();
        String fromUser = messageDTO.getFromUser();
        Map<String,Object> data = messageDTO.getData();
        String[] arr = messageDTO.getToUser().split(",");
        for(String username: arr){
            doSend(title, content, fromUser, username, data);
        }
    }

    private void doSend(String title, String msgContent, String fromUser, String toUser, Map<String, Object> data){
    }
}