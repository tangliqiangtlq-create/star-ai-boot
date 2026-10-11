package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.Api;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.system.entity.SysGatewayRoute;
import org.jeecg.modules.system.service.ISysGatewayRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Description: gateway route management @Author: jeecg-boot @Date: 2020-05-26 @Version: V1.0
 */
@Api(tags = "Gateway route management")
@RestController
@RequestMapping("/sys/gatewayRoute")
@Slf4j
@SaCheckRole(value = {"admin", SecurityRoleConstants.ROLE_OPS_ADMIN}, mode = SaMode.OR)
public class SysGatewayRouteController
    extends JeecgController<SysGatewayRoute, ISysGatewayRouteService> {

  @Autowired private ISysGatewayRouteService sysGatewayRouteService;

  @PostMapping(value = "/updateAll")
  public Result<?> updateAll(@RequestBody JSONObject json) {
    sysGatewayRouteService.updateAll(json);
    return Result.ok(I18nUtil.getMessage("sysGatewayRoute.operationSuccess"));
  }

  @PostMapping(value = "/list")
  public Result<?> queryPageList(SysGatewayRoute sysGatewayRoute) {
    LambdaQueryWrapper<SysGatewayRoute> query = new LambdaQueryWrapper<>();
    List<SysGatewayRoute> ls = sysGatewayRouteService.list(query);
    JSONArray array = new JSONArray();
    for (SysGatewayRoute rt : ls) {
      JSONObject obj = (JSONObject) JSONObject.toJSON(rt);
      if (oConvertUtils.isNotEmpty(rt.getPredicates())) {
        obj.put("predicates", JSONArray.parseArray(rt.getPredicates()));
      }
      if (oConvertUtils.isNotEmpty(rt.getFilters())) {
        obj.put("filters", JSONArray.parseArray(rt.getFilters()));
      }
      array.add(obj);
    }
    return Result.ok(array);
  }

  @PostMapping(value = "/clearRedis")
  public Result<?> clearRedis() {
    sysGatewayRouteService.clearRedis();
    return Result.ok(I18nUtil.getMessage("sysGatewayRoute.clearSuccess"));
  }

  /**
   * Delete by id
   *
   * @param id
   * @return
   */
  @SaCheckPermission("system:getway:delete")
  @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    sysGatewayRouteService.deleteById(id);
    return Result.ok(I18nUtil.getMessage("sysGatewayRoute.deleteRouteSuccess"));
  }
}
