package org.jeecg.modules.message.websocket;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MAN-20 / G28: upgrade path without {userId}. Identity comes only from Sa-Token
 * via {@link WebSocket.AuthConfigurator} (B8-2 handshake gate kept).
 */
@Component
@Slf4j
@ServerEndpoint(value = "/websocket", configurator = WebSocket.AuthConfigurator.class)
public class WebSocketNoPathUser {

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        String verifiedLoginId = (String) config.getUserProperties().get(WebSocket.USER_PRINCIPAL_KEY);
        if (verifiedLoginId == null) {
            log.warn("[System WebSocket] /websocket onOpen without verified loginId");
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Missing loginId"));
            } catch (Exception ignored) {
            }
            return;
        }
        session.getUserProperties().put(WebSocket.USER_PRINCIPAL_KEY, verifiedLoginId);
        WebSocket.bindSession(verifiedLoginId, session);
        log.debug("[System WebSocket] /websocket connected loginId={}", verifiedLoginId);
    }

    @OnClose
    public void onClose(Session session) {
        String verifiedLoginId = (String) session.getUserProperties().get(WebSocket.USER_PRINCIPAL_KEY);
        WebSocket.unbindSession(verifiedLoginId, session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.warn("[System WebSocket] /websocket error: {}", error == null ? "null" : error.toString());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // heartbeat / client ping — no business payload expected
    }
}
