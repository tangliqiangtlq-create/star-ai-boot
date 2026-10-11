package org.jeecg.modules.message.websocket;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.websocket.*;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.common.base.BaseMap;
import org.jeecg.common.constant.WebsocketConst;
import org.jeecg.common.modules.redis.client.JeecgRedisClient;
import org.jeecg.config.filter.WebsocketFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author scott
 * @Date 2019/11/29 9:41
 * @Description: equivalent to setting the access URL
 *
 * <p>B8-2 hardening: the {@link AuthConfigurator} extracts & validates the Sa-Token before accepting the
 * 101 Upgrade handshake; @OnOpen performs an additional <code>loginId == userId</code> path IDOR check so
 * User A cannot subscribe to /websocket/{userB}.
 */
@Component
@Slf4j
@ServerEndpoint(value = "/websocket/{userId}", configurator = WebSocket.AuthConfigurator.class)
public class WebSocket {
    
    /** Thread-safe Map */
    static final ConcurrentHashMap<String, Session> sessionPool = new ConcurrentHashMap<>();

    /** MAN-20: bind session by verified loginId (no path userId). */
    static void bindSession(String loginId, Session session) {
        if (loginId != null && session != null) sessionPool.put(loginId, session);
    }

    static void unbindSession(String loginId, Session session) {
        if (loginId == null) return;
        Session cur = sessionPool.get(loginId);
        if (cur == null || cur == session) sessionPool.remove(loginId);
    }

    /**
     * Redis trigger listener name
     */
    public static final String REDIS_TOPIC_NAME = "socketHandler";

    public static final String USER_PRINCIPAL_KEY = "__jeecg_ws_loginId";

    // Avoid null pointer on first invocation
    private static JeecgRedisClient jeecgRedisClient;
    @Autowired
    private void setJeecgRedisClient(JeecgRedisClient jeecgRedisClient){
        WebSocket.jeecgRedisClient = jeecgRedisClient;
    }


    //==========[WebSocket receive/push methods — specific service node pushes WS messages]========================================================================================
    @OnOpen
    public void onOpen(Session session, EndpointConfig config, @PathParam(value = "userId") String userId) {
        try {
            String verifiedLoginId = (String) config.getUserProperties().get(USER_PRINCIPAL_KEY);
            if (verifiedLoginId == null) {
                // Legacy guard: AuthConfigurator should have populated this; missing → reject
                log.warn("[System WebSocket] onOpen without verified loginId, close userId={}", userId);
                closeQuietly(session, 4001, "Missing verified loginId");
                return;
            }
            // B8-2 IDOR: caller MUST only connect to their OWN userId path.
            if (!verifiedLoginIdMatchesPathUserId(verifiedLoginId, userId)) {
                log.warn("[System WebSocket] IDOR attempt detected: verifiedLoginId={} tried /websocket/{}", verifiedLoginId, userId);
                closeQuietly(session, 4003, "Forbidden: wrong userId path");
                return;
            }
            sessionPool.put(userId, session);
            log.debug("[System WebSocket] New connection, total count:" + sessionPool.size());
        } catch (Exception e) {
            log.error("[System WebSocket] onOpen failed userId=" + userId, e);
            closeQuietly(session, 4000, e.getMessage());
        }
    }

    @OnClose
    public void onClose(@PathParam("userId") String userId) {
        try {
            sessionPool.remove(userId);
            log.debug("[System WebSocket] Connection closed, total count:" + sessionPool.size());
        } catch (Exception e) {
            log.error("operation failed", e);
        }
    }

    /**
     * WebSocket: push message.
     *
     * @param userId
     * @param message
     */
    public void pushMessage(String userId, String message) {
        for (Map.Entry<String, Session> item : sessionPool.entrySet()) {
            //userId key = {user id + "_" + login token md5}
            //TODO vue2 key rule not updated yet; logic unaffected for now
            if (item.getKey().contains(userId)) {
                Session session = item.getValue();
                try {
                    //update-begin-author:taoyan date:20211012 for: websocket error https://gitee.com/jeecg/jeecg-boot/issues/I4C0MU
                    synchronized (session){
                        log.debug("[System WebSocket] Push message to single user:" + message);
                        session.getBasicRemote().sendText(message);
                    }
                    //update-end-author:taoyan date:20211012 for: websocket error https://gitee.com/jeecg/jeecg-boot/issues/I4C0MU
                } catch (Exception e) {
                    log.error("operation failed", e);
                }
            }
        }
    }

    /**
     * WebSocket: broadcast message to all connections.
     */
    public void pushMessage(String message) {
        try {
            for (Map.Entry<String, Session> item : sessionPool.entrySet()) {
                try {
                    item.getValue().getAsyncRemote().sendText(message);
                } catch (Exception e) {
                    log.error("operation failed", e);
                }
            }
            log.debug("[System WebSocket] Broadcast message:" + message);
        } catch (Exception e) {
            log.error("operation failed", e);
        }
    }


    /**
     * WebSocket: receive client messages.
     */
    @OnMessage
    public void onMessage(String message, @PathParam(value = "userId") String userId) {
        if(!"ping".equals(message) && !WebsocketConst.CMD_CHECK.equals(message)){
            log.debug("[System WebSocket] Received client message:" + message);
        }else{
            log.debug("[System WebSocket] Received client message:" + message);
            //update-begin---author:wangshuai---date:2024-05-07---for:[issues/1161] front-end WebSocket listener broken due to heartbeat---
            this.sendMessage(userId, "ping");
            //update-end---author:wangshuai---date:2024-05-07---for:[issues/1161] front-end WebSocket listener broken due to heartbeat---
        }
        
//        //------------------------------------------------------------------------------
//        JSONObject obj = new JSONObject();
//        //BusinessType
//        obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_CHECK);
//        // Message content
//        obj.put(WebsocketConst.MSG_TXT, "Heartbeat response");
//        this.pushMessage(userId, obj.toJSONString());
//        //------------------------------------------------------------------------------
    }

    /**
     * ConfigErrorInfoProcess
     *
     * @param session
     * @param t
     */
    @OnError
    public void onError(Session session, Throwable t) {
        log.warn("[System WebSocket] Message error");
        log.error("operation failed", t);
    }
    //==========[System WebSocket receive/push methods — specific service node pushes WS messages]========================================================================================
    

    //==========[Redis pub/sub mode — push messages]========================================================================================
    /**
     * Push a message from the backend to Redis
     *
     * @param message
     */
    public void sendMessage(String message) {
        //log.debug("[System WebSocket] broadcast message:" + message);
        BaseMap baseMap = new BaseMap();
        baseMap.put("userId", "");
        baseMap.put("message", message);
        jeecgRedisClient.sendMessage(WebSocket.REDIS_TOPIC_NAME, baseMap);
    }

    /**
     * Single-point message redis
     *
     * @param userId
     * @param message
     */
    public void sendMessage(String userId, String message) {
        BaseMap baseMap = new BaseMap();
        baseMap.put("userId", userId);
        baseMap.put("message", message);
        jeecgRedisClient.sendMessage(WebSocket.REDIS_TOPIC_NAME, baseMap);
    }

    /**
     * Single-point message (multiple users) redis
     *
     * @param userIds
     * @param message
     */
    public void sendMessage(String[] userIds, String message) {
        for (String userId : userIds) {
            sendMessage(userId, message);
        }
    }
    //=======[Redis pub/sub mode — push messages]==========================================================================================
    

    //==========[B8-2 helpers: AuthConfigurator handshake + utility]========================================================================
    private static boolean verifiedLoginIdMatchesPathUserId(String verifiedLoginId, String pathUserId) {
        if (verifiedLoginId == null || pathUserId == null) return false;
        // Jeecg sessionPool key rule is either raw userId OR userId + "_" + md5(token) (legacy vue2 variant).
        // Allow exact match, or match the pathUserId starts with verifiedLoginId + "_" suffix.
        if (verifiedLoginId.equals(pathUserId)) return true;
        return pathUserId.startsWith(verifiedLoginId + "_");
    }

    private static void closeQuietly(Session session, int code, String reason) {
        if (session == null || !session.isOpen()) return;
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.getCloseCode(code) == null
                ? CloseReason.CloseCodes.VIOLATED_POLICY
                : CloseReason.CloseCodes.getCloseCode(code),
                reason == null ? "" : reason));
        } catch (IOException ignore) {}
    }

    /**
     * Runs BEFORE the server accepts the 101 Upgrade response. Extracts the Sa-Token from the same
     * carriers as {@link WebsocketFilter}, validates via StpUtil, then stashes verified loginId on
     * EndpointConfig.userProperties so @OnOpen can re-assert path == pathUserId binding.
     */
    public static class AuthConfigurator extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            super.modifyHandshake(sec, request, response);
            // Prefer loginId already verified by WebsocketFilter (same thread, Cookie/token-name carriers).
            String preVerified = WebsocketFilter.peekVerifiedLoginId();
            if (cn.hutool.core.util.StrUtil.isNotBlank(preVerified)) {
                sec.getUserProperties().put(USER_PRINCIPAL_KEY, preVerified);
                return;
            }
            String token = resolveTokenFromHandshake(request);
            if (cn.hutool.core.util.StrUtil.isBlank(token)) {
                throw new AuthenticationRequiredException("WebSocket Sa-Token missing");
            }
            Object loginId;
            try {
                loginId = cn.dev33.satoken.stp.StpUtil.getLoginIdByToken(token);
            } catch (Exception ex) {
                throw new AuthenticationRequiredException("WebSocket Sa-Token invalid");
            }
            if (loginId == null) {
                throw new AuthenticationRequiredException("WebSocket Sa-Token invalid");
            }
            sec.getUserProperties().put(USER_PRINCIPAL_KEY, String.valueOf(loginId));
        }

        /**
         * Must mirror {@link WebsocketFilter#extractSaToken}: Protocol / Bearer /
         * configured {@code sa-token.token-name} (often {@code token}) header+query+cookie /
         * legacy {@code satoken}. Cookie-only clients pass Filter but fail here if Cookie is ignored.
         */
        private static String resolveTokenFromHandshake(HandshakeRequest req) {
            // carrier #1: Sec-WebSocket-Protocol header (Jeecg convention)
            String fromProto = firstHeaderToken(req, "Sec-WebSocket-Protocol", true);
            if (fromProto != null) {
                return fromProto;
            }
            // carrier #2: Authorization Bearer
            List<String> auth = headerValues(req, "Authorization");
            if (auth != null) {
                for (String a : auth) {
                    if (a != null && a.regionMatches(true, 0, "Bearer ", 0, 7)) {
                        String t = a.substring(7).trim();
                        if (!t.isEmpty()) {
                            return t;
                        }
                    }
                }
            }

            String configuredName = resolveConfiguredTokenName();
            // carrier #3: configured token-name header / query / cookie
            String fromConfigured = firstNonBlank(
                    firstHeaderToken(req, configuredName, false),
                    firstQueryParam(req, configuredName),
                    cookieValue(req, configuredName));
            if (fromConfigured != null) {
                return fromConfigured;
            }
            // carrier #4: legacy satoken
            if (!"satoken".equalsIgnoreCase(configuredName)) {
                String fromLegacy = firstNonBlank(
                        firstHeaderToken(req, "satoken", false),
                        firstQueryParam(req, "satoken"),
                        cookieValue(req, "satoken"));
                if (fromLegacy != null) {
                    return fromLegacy;
                }
            }
            return null;
        }

        private static String resolveConfiguredTokenName() {
            try {
                String name = cn.dev33.satoken.stp.StpUtil.getTokenName();
                if (cn.hutool.core.util.StrUtil.isNotBlank(name)) {
                    return name.trim();
                }
            } catch (Exception ignore) {
            }
            return "satoken";
        }

        private static List<String> headerValues(HandshakeRequest req, String name) {
            if (req == null || cn.hutool.core.util.StrUtil.isBlank(name)) {
                return null;
            }
            Map<String, List<String>> headers = req.getHeaders();
            if (headers == null) {
                return null;
            }
            List<String> direct = headers.get(name);
            if (direct != null) {
                return direct;
            }
            // Some containers keep header names case-sensitively
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue();
                }
            }
            return null;
        }

        /** @param splitCsv true for Sec-WebSocket-Protocol comma-separated list */
        private static String firstHeaderToken(HandshakeRequest req, String name, boolean splitCsv) {
            List<String> values = headerValues(req, name);
            if (values == null) {
                return null;
            }
            for (String v : values) {
                if (cn.hutool.core.util.StrUtil.isBlank(v)) {
                    continue;
                }
                if (splitCsv) {
                    for (String part : v.split(",")) {
                        String t = part.trim();
                        if (!t.isEmpty()) {
                            return t;
                        }
                    }
                } else {
                    return v.trim();
                }
            }
            return null;
        }

        private static String firstQueryParam(HandshakeRequest req, String name) {
            Map<String, List<String>> params = req.getParameterMap();
            if (params == null || cn.hutool.core.util.StrUtil.isBlank(name)) {
                return null;
            }
            List<String> p = params.get(name);
            if (p == null || p.isEmpty()) {
                // case-insensitive fallback
                for (Map.Entry<String, List<String>> e : params.entrySet()) {
                    if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                        p = e.getValue();
                        break;
                    }
                }
            }
            if (p != null && !p.isEmpty() && cn.hutool.core.util.StrUtil.isNotBlank(p.get(0))) {
                return p.get(0).trim();
            }
            return null;
        }

        private static String cookieValue(HandshakeRequest req, String name) {
            if (cn.hutool.core.util.StrUtil.isBlank(name)) {
                return null;
            }
            List<String> cookieHeaders = headerValues(req, "Cookie");
            if (cookieHeaders == null) {
                return null;
            }
            for (String header : cookieHeaders) {
                if (cn.hutool.core.util.StrUtil.isBlank(header)) {
                    continue;
                }
                for (String part : header.split(";")) {
                    String pair = part.trim();
                    int eq = pair.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String cname = pair.substring(0, eq).trim();
                    if (name.equalsIgnoreCase(cname)) {
                        String cval = pair.substring(eq + 1).trim();
                        if (!cval.isEmpty()) {
                            return cval;
                        }
                    }
                }
            }
            return null;
        }

        private static String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            }
            for (String v : values) {
                if (cn.hutool.core.util.StrUtil.isNotBlank(v)) {
                    return v.trim();
                }
            }
            return null;
        }
    }

    /**
     * Throwing a RuntimeException subclass from modifyHandshake aborts the 101 Upgrade with 4xx. Jakarta
     * WebSocket impl will convert to 500 or close frame depending on container; we rely on the upstream
     * {@link WebsocketFilter} as the primary 401 gate and this exception as the secondary defense.
     */
    public static class AuthenticationRequiredException extends RuntimeException {
        public AuthenticationRequiredException(String msg) { super(msg); }
    }

}