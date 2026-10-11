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
        if(data!=null){
            // Summary info
            Object msgAbstract = data.get(CommonConstant.NOTICE_MSG_SUMMARY);
            if(msgAbstract!=null){
                announcement.setMsgAbstract(msgAbstract.toString());
            }
            // Task node ID
            Object taskId = data.get(CommonConstant.NOTICE_MSG_BUS_ID);
            if(taskId!=null){
                announcement.setBusId(taskId.toString());
                announcement.setBusType(Vue3MessageHrefEnum.BPM_TASK.getBusType());
            }

            // In-flow message node passes a busType when sending
            Object busType = data.get(CommonConstant.NOTICE_MSG_BUS_TYPE);
            if(busType!=null){
                announcement.setBusType(busType.toString());
            }
        }
        announcement.setTitile(title);
        announcement.setMsgContent(msgContent);
        announcement.setSender(fromUser);
        announcement.setPriority(CommonConstant.PRIORITY_M);
        announcement.setMsgType(CommonConstant.MSG_TYPE_UESR);
        announcement.setSendStatus(CommonConstant.HAS_SEND);
        announcement.setSendTime(new Date());
        //SystemMessage
        announcement.setMsgCategory("2");
        announcement.setDelFlag(String.valueOf(CommonConstant.DEL_FLAG_0));
        sysAnnouncementMapper.insert(announcement);
        // 2. Insert user announcement read marker record
        String userId = toUser;
        String[] userIds = userId.split(",");
        String anntId = announcement.getId();
        for(int i=0;i<userIds.length;i++) {
            if(oConvertUtils.isNotEmpty(userIds[i])) {
                SysUser sysUser = userMapper.getUserByName(userIds[i]);
                if(sysUser==null) {
                    continue;
                }
                announcementSend.setAnntId(anntId);
                announcementSend.setUserId(sysUser.getId());
                announcementSend.setReadFlag(CommonConstant.NO_READ_FLAG);
                sysAnnouncementSendMapper.insert(announcementSend);
                JSONObject obj = new JSONObject();
                obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_USER);
                obj.put(WebsocketConst.MSG_USER_ID, sysUser.getId());
                obj.put(WebsocketConst.MSG_ID, announcement.getId());
                obj.put(WebsocketConst.MSG_TXT, announcement.getTitile());
                webSocket.sendMessage(sysUser.getId(), obj.toJSONString());
            }
        }
    }
}