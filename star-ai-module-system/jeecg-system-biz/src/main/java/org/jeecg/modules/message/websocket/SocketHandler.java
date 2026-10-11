package org.jeecg.modules.message.websocket;

import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.base.BaseMap;
import org.jeecg.common.constant.CommonSendStatus;
import org.jeecg.common.modules.redis.listener.JeecgRedisListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Message listener (via redis pub/sub for message push)
 * Solves cluster deployment: multi-instance nodes (sender publishes to redis; each node receives and triggers ws push)
 * @author: jeecg-boot
 */
@Slf4j
@Component(WebSocket.REDIS_TOPIC_NAME)
public class SocketHandler implements JeecgRedisListener {

    @Autowired
    private WebSocket webSocket;

    @Override
    public void onMessage(BaseMap map) {
        log.debug("[Redis pub/sub] redis Listener: {}, params: {}",WebSocket.REDIS_TOPIC_NAME, map.toString());

        String userId = map.get("userId");
        String message = map.get("message");
        if (ObjectUtil.isNotEmpty(userId)) {
            //PC message push to specific users
            webSocket.pushMessage(userId, message);
            //App message push to specific users
            webSocket.pushMessage(userId+CommonSendStatus.APP_SESSION_SUFFIX, message);
        } else {
            // Push all
            webSocket.pushMessage(message);
        }

    }
}