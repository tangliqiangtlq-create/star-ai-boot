package org.jeecg.modules.system.listener;

import cn.hutool.json.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.mq.RocketMQMessageListener;
import org.jeecg.common.mq.RocketMQListener;
import org.jeecg.common.constant.WebsocketConst;
import org.jeecg.modules.message.websocket.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
    topic = "saveAnnouncement_topic",
    consumerGroup = "saveAnnouncement_topic_group")
public class SaveAnnouncementTopic implements RocketMQListener<JSONObject> {


  @Override
  public void onMessage(JSONObject message) {
    sysAnnouncementService.save(sysAnnouncement);

    String userId = sysAnnouncement.getUserIds();
    String[] userIds = userId.substring(0, (userId.length() - 1)).split(",");
    com.alibaba.fastjson.JSONObject obj = new com.alibaba.fastjson.JSONObject();
    obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_USER);
    obj.put(WebsocketConst.MSG_ID, sysAnnouncement.getId());
    obj.put(WebsocketConst.MSG_TXT, sysAnnouncement.getTitile());
    webSocket.sendMessage(userIds, obj.toJSONString());
  }
}
