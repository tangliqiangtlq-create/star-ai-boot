package com.xxl.job.admin.controller;

import com.xxl.job.admin.controller.annotation.PermissionLimit;
import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.dao.XxlJobGroupDao;
import com.xxl.job.admin.dao.XxlJobUserDao;
import com.xxl.job.admin.service.LoginService;
import com.xxl.job.core.biz.model.ReturnT;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author xuxueli 2019-05-04 16:39:50
 */
@Controller
@RequestMapping("/user")
public class UserController {

  private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(12);

  @Resource private XxlJobUserDao xxlJobUserDao;
  @Resource private XxlJobGroupDao xxlJobGroupDao;

  @GetMapping
  @PermissionLimit(adminuser = true)
  public String index(Model model) {

    // Executor list
    List<XxlJobGroup> groupList = xxlJobGroupDao.findAll();
    model.addAttribute("groupList", groupList);

    return "user/user.index";
  }

  @PostMapping("/pageList")
  @ResponseBody
  @PermissionLimit(adminuser = true)
  public Map<String, Object> pageList(
      @RequestParam(required = false, defaultValue = "0") int start,
      @RequestParam(required = false, defaultValue = "10") int length,
      String username,
      int role) {

    // page list
    List<XxlJobUser> list = xxlJobUserDao.pageList(start, length, username, role);
    int list_count = xxlJobUserDao.pageListCount(start, length, username, role);

    // package result
    Map<String, Object> maps = new HashMap<String, Object>();
    maps.put("recordsTotal", list_count); // Total number of records
    maps.put("recordsFiltered", list_count); // Total number of records after filtering
    maps.put("data", list); // PaginationList
    return maps;
  }

  @PostMapping("/add")
  @ResponseBody
  @PermissionLimit(adminuser = true)
  public ReturnT<String> add(XxlJobUser xxlJobUser) {

    // valid username
    if (!StringUtils.hasText(xxlJobUser.getUsername())) {
      return new ReturnT<String>(
          ReturnT.FAIL_CODE,
          I18nUtil.getString("system_please_input") + I18nUtil.getString("user_username"));
    }
    xxlJobUser.setUsername(xxlJobUser.getUsername().trim());
    if (!(xxlJobUser.getUsername().length() >= 4 && xxlJobUser.getUsername().length() <= 20)) {
      return new ReturnT<String>(
          ReturnT.FAIL_CODE, I18nUtil.getString("system_lengh_limit") + "[4-20]");
    }
    // valid password
    if (!StringUtils.hasText(xxlJobUser.getPassword())) {
      return new ReturnT<String>(
          ReturnT.FAIL_CODE,
          I18nUtil.getString("system_please_input") + I18nUtil.getString("user_pwd"));
    }
    xxlJobUser.setPassword(xxlJobUser.getPassword().trim());
    if (!(xxlJobUser.getPassword().length() >= 4 && xxlJobUser.getPassword().length() <= 20)) {
      return new ReturnT<String>(
          ReturnT.FAIL_CODE, I18nUtil.getString("system_lengh_limit") + "[4-20]");
    }
    // bcrypt password
    xxlJobUser.setPassword(BCRYPT.encode(xxlJobUser.getPassword()));

    // check repeat
    XxlJobUser existUser = xxlJobUserDao.loadByUserName(xxlJobUser.getUsername());
    if (existUser != null) {
      return new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("user_username_repeat"));
    }

    // write
    xxlJobUserDao.save(xxlJobUser);
    return com.xxl.job.admin.core.util.ReturnTCompat.success();
  }

  @PostMapping("/update")
  @ResponseBody
  @PermissionLimit(adminuser = true)
  public ReturnT<String> update(HttpServletRequest request, XxlJobUser xxlJobUser) {

    // avoid opt login seft
    XxlJobUser loginUser = (XxlJobUser) request.getAttribute(LoginService.LOGIN_IDENTITY_KEY);
    if (loginUser.getUsername().equals(xxlJobUser.getUsername())) {
      return new ReturnT<String>(
          ReturnT.FAIL_CODE, I18nUtil.getString("user_update_loginuser_limit"));
    }

    // valid password
    if (StringUtils.hasText(xxlJobUser.getPassword())) {
      xxlJobUser.setPassword(xxlJobUser.getPassword().trim());
      if (!(xxlJobUser.getPassword().length() >= 4 && xxlJobUser.getPassword().length() <= 20)) {
        return new ReturnT<String>(
            ReturnT.FAIL_CODE, I18nUtil.getString("system_lengh_limit") + "[4-20]");
      }
      // bcrypt password
      xxlJobUser.setPassword(BCRYPT.encode(xxlJobUser.getPassword()));
    } else {
      xxlJobUser.setPassword(null);
    }

    // write
    xxlJobUserDao.update(xxlJobUser);
    return com.xxl.job.admin.core.util.ReturnTCompat.success();
  }

  @PostMapping("/remove")
  @ResponseBody
  @PermissionLimit(adminuser = true)
  public ReturnT<String> remove(HttpServletRequest request, int id) {

    // avoid opt login seft
    XxlJobUser loginUser = (XxlJobUser) request.getAttribute(LoginService.LOGIN_IDENTITY_KEY);
    if (loginUser.getId() == id) {
      return new ReturnT<String>(
          ReturnT.FAIL_CODE, I18nUtil.getString("user_update_loginuser_limit"));
    }

    xxlJobUserDao.delete(id);
    return com.xxl.job.admin.core.util.ReturnTCompat.success();
  }

  @PostMapping("/updatePwd")
  @ResponseBody
  public ReturnT<String> updatePwd(HttpServletRequest request, String password) {

    // valid password
    if (password == null || password.trim().length() == 0) {
      return new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("system_please_input") + I18nUtil.getString("user_password"));
    }
    password = password.trim();
    if (!(password.length() >= 4 && password.length() <= 20)) {
      return new ReturnT<String>(
          ReturnT.FAIL_CODE, I18nUtil.getString("system_lengh_limit") + "[4-20]");
    }

    // bcrypt password
    String bcryptPassword = BCRYPT.encode(password);

    // update pwd
    XxlJobUser loginUser = (XxlJobUser) request.getAttribute(LoginService.LOGIN_IDENTITY_KEY);

    // do write
    XxlJobUser existUser = xxlJobUserDao.loadByUserName(loginUser.getUsername());
    existUser.setPassword(bcryptPassword);
    xxlJobUserDao.update(existUser);

    return com.xxl.job.admin.core.util.ReturnTCompat.success();
  }
}
