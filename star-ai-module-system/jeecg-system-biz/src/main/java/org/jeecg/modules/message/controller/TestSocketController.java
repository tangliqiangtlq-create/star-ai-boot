package org.jeecg.modules.message.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import org.jeecg.common.api.vo.Result;
import org.springframework.context.annotation.Profile;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.constant.WebsocketConst;
import org.jeecg.modules.message.websocket.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;

/**
 * @Description: TestSocketController
 *
 * @author: jeecg-boot
 */
@Profile("dev")
@RestController
@RequestMapping(value = "/sys/socketTest", method = RequestMethod.POST)
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class TestSocketController {

  @Autowired private WebSocket webSocket;

  @PostMapping("/sendAll")
  public Result<String> sendAll(@RequestBody JSONObject jsonObject) {
    Result<String> result = new Result<String>();
    String message = jsonObject.getString("message");
    JSONObject obj = new JSONObject();
    obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_TOPIC);
    obj.put(WebsocketConst.MSG_ID, "M0001");
    obj.put(WebsocketConst.MSG_TXT, message);
    webSocket.sendMessage(obj.toJSONString());
    result.setResult("Mass send!");
    return result;
  }

  @PostMapping("/sendUser")
  public Result<String> sendUser(@RequestBody JSONObject jsonObject) {
    Result<String> result = new Result<String>();
    String userId = jsonObject.getString("userId");
    String message = jsonObject.getString("message");
    JSONObject obj = new JSONObject();
    obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_USER);
    obj.put(WebsocketConst.MSG_USER_ID, userId);
    obj.put(WebsocketConst.MSG_ID, "M0001");
    obj.put(WebsocketConst.MSG_TXT, message);
    webSocket.sendMessage(userId, obj.toJSONString());
    result.setResult("Single send");
    return result;
  }
}
