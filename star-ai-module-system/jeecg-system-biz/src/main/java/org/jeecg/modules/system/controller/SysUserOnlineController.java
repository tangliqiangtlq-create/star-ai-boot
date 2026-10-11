package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.RedisScanSupport;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.system.service.impl.SysBaseApiImpl;
import org.jeecg.modules.system.vo.SysUserOnlineVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * @Description: Online users @Author: chenli @Date: 2020-06-07 @Version: V1.0*/
@RestController
@RequestMapping(value = "/sys/online", method = RequestMethod.POST)
@Slf4j
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class SysUserOnlineController {

  @Autowired private RedisUtil redisUtil;
  @Autowired public RedisTemplate redisTemplate;
  @Autowired public ISysUserService userService;
  @Autowired private SysBaseApiImpl sysBaseApi;

  @Resource private BaseCommonService baseCommonService;

  @RequestMapping(value = "/list", method = RequestMethod.POST)
  public Result<Page<SysUserOnlineVO>> list(
      @RequestParam(name = "username", required = false) String username,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {
    Collection<String> keys = RedisScanSupport.scanKeys(redisTemplate, CommonConstant.PREFIX_USER_TOKEN + "*");
    List<SysUserOnlineVO> onlineList = new ArrayList<SysUserOnlineVO>();

    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");

    for (String key : keys) {
      String token = (String) redisUtil.get(key);
      if (StringUtils.isNotEmpty(token)) {
        SysUserOnlineVO online = new SysUserOnlineVO();
        online.setToken(token);
        // TODO Change to one-shot query

        if (loginUser != null && !"_reserve_user_external".equals(loginUser.getUsername())) {
          // update-begin---author:wangshuai ---date:20220104  for: [JTC-382] Online user query invalid ------------
          // Verify username matches the passed username
          boolean isMatchUsername = true;
          // If username is empty and current loop user does not include passed username, set to false
          if (oConvertUtils.isNotEmpty(username) && !loginUser.getUsername().contains(username)) {
            isMatchUsername = false;
          }
          if (isMatchUsername) {
            BeanUtils.copyProperties(loginUser, online);
            onlineList.add(online);
          }
          // update-end---author:wangshuai ---date:20220104  for: [JTC-382] Online user query invalid ------------
        }
      }
    }
    Collections.reverse(onlineList);

    Page<SysUserOnlineVO> page = new Page<SysUserOnlineVO>(pageNo, pageSize);
    int count = onlineList.size();
    List<SysUserOnlineVO> pages = new ArrayList<>();
    // Compute index of first row on current page
    int currId = pageNo > 1 ? (pageNo - 1) * pageSize : 0;
    for (int i = 0; i < pageSize && i < count - currId; i++) {
      pages.add(onlineList.get(currId + i));
    }
    page.setSize(pageSize);
    page.setCurrent(pageNo);
    page.setTotal(count);
    // Compute total pagination pages
    page.setPages(count % 10 == 0 ? count / 10 : count / 10 + 1);
    page.setRecords(pages);

    Result<Page<SysUserOnlineVO>> result = new Result<Page<SysUserOnlineVO>>();
    result.setSuccess(true);
    result.setResult(page);
    return result;
  }

  /** Force logout user */
  @RequestMapping(value = "/forceLogout", method = RequestMethod.POST)
  public Result<Object> forceLogout(@RequestBody SysUserOnlineVO online) {
    // User logout logic
    if (oConvertUtils.isEmpty(online.getToken())) {
      return Result.error(I18nUtil.getMessage("sysUserOnline.logoutFailed"));
    }
    String username = StpUtil.getLoginIdAsString();
    LoginUser sysUser = sysBaseApi.getUserByName(username);
    if (sysUser != null) {
      baseCommonService.addLog(
          "Force: " + sysUser.getRealname() + "Logout succeeded!", CommonConstant.LOG_TYPE_1, null, sysUser);
      log.info(" Force  " + sysUser.getRealname() + "Logout succeeded! ");
      // Clear user login token cache
      redisUtil.del(CommonConstant.PREFIX_USER_TOKEN + online.getToken());
      // Clear user login Shiro permission cache
      redisUtil.del(CommonConstant.PREFIX_USER_SHIRO_CACHE + sysUser.getId());
      // Clear user cache info (including department), e.g. sys:cache:user::<username>
      redisUtil.del(String.format("%s::%s", CacheConstant.SYS_USERS_CACHE, sysUser.getUsername()));
      // Call Shiro logout

      return Result.ok(I18nUtil.getMessage("sysUserOnline.logoutSuccess"));
    } else {
      return Result.error(I18nUtil.getMessage("sysUserOnline.tokenInvalid"));
    }
  }
}
