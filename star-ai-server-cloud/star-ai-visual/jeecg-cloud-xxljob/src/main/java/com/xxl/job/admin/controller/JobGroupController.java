package com.xxl.job.admin.controller;

import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobRegistry;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.dao.XxlJobGroupDao;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.admin.dao.XxlJobRegistryDao;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.enums.RegistryConfig;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * job group controller
 *
 * @author xuxueli 2016-10-02 20:52:56
 */
@Controller
@RequestMapping("/jobgroup")
public class JobGroupController {

  @Resource public XxlJobInfoDao xxlJobInfoDao;
  @Resource public XxlJobGroupDao xxlJobGroupDao;
  @Resource private XxlJobRegistryDao xxlJobRegistryDao;

  @GetMapping
  public String index(Model model) {
    return "jobgroup/jobgroup.index";
  }

  @PostMapping("/pageList")
  @ResponseBody
  public Map<String, Object> pageList(
      HttpServletRequest request,
      @RequestParam(required = false, defaultValue = "0") int start,
      @RequestParam(required = false, defaultValue = "10") int length,
      String appname,
      String title) {

    // page query
    List<XxlJobGroup> list = xxlJobGroupDao.pageList(start, length, appname, title);
    int list_count = xxlJobGroupDao.pageListCount(start, length, appname, title);

    // package result
    Map<String, Object> maps = new HashMap<String, Object>();
    maps.put("recordsTotal", list_count); // Total number of records
    maps.put("recordsFiltered", list_count); // Total number of records after filtering
    maps.put("data", list); // PaginationList
    return maps;
  }

  @PostMapping("/save")
  @ResponseBody
  public ReturnT<String> save(XxlJobGroup xxlJobGroup) {

    // valid
    if (xxlJobGroup.getAppname() == null || xxlJobGroup.getAppname().trim().length() == 0) {
      return new ReturnT<String>(500, (I18nUtil.getString("system_please_input") + "AppName"));
    }
    if (xxlJobGroup.getAppname().length() < 4 || xxlJobGroup.getAppname().length() > 64) {
      return new ReturnT<String>(500, I18nUtil.getString("jobgroup_field_appname_length"));
    }
    if (xxlJobGroup.getTitle() == null || xxlJobGroup.getTitle().trim().length() == 0) {
      return new ReturnT<String>(
          500,
          (I18nUtil.getString("system_please_input") + I18nUtil.getString("jobgroup_field_title")));
    }
    if (xxlJobGroup.getAddressType() != 0) {
      if (xxlJobGroup.getAddressList() == null
          || xxlJobGroup.getAddressList().trim().length() == 0) {
        return new ReturnT<String>(500, I18nUtil.getString("jobgroup_field_addressType_limit"));
      }
      String[] addresss = xxlJobGroup.getAddressList().split(",");
      for (String item : addresss) {
        if (item == null || item.trim().length() == 0) {
          return new ReturnT<String>(
              500, I18nUtil.getString("jobgroup_field_registryList_unvalid"));
        }
      }
    }

    int ret = xxlJobGroupDao.save(xxlJobGroup);
    return (ret > 0) ? com.xxl.job.admin.core.util.ReturnTCompat.success() : com.xxl.job.admin.core.util.ReturnTCompat.fail();
  }

  @PostMapping("/update")
  @ResponseBody
  public ReturnT<String> update(XxlJobGroup xxlJobGroup) {
    // valid
    if (xxlJobGroup.getAppname() == null || xxlJobGroup.getAppname().trim().length() == 0) {
      return new ReturnT<String>(500, (I18nUtil.getString("system_please_input") + "AppName"));
    }
    if (xxlJobGroup.getAppname().length() < 4 || xxlJobGroup.getAppname().length() > 64) {
      return new ReturnT<String>(500, I18nUtil.getString("jobgroup_field_appname_length"));
    }
    if (xxlJobGroup.getTitle() == null || xxlJobGroup.getTitle().trim().length() == 0) {
      return new ReturnT<String>(
          500,
          (I18nUtil.getString("system_please_input") + I18nUtil.getString("jobgroup_field_title")));
    }
    if (xxlJobGroup.getAddressType() == 0) {
      // 0=Automatic registration
      List<String> registryList = findRegistryByAppName(xxlJobGroup.getAppname());
      String addressListStr = null;
      if (registryList != null && !registryList.isEmpty()) {
        Collections.sort(registryList);
        addressListStr = "";
        for (String item : registryList) {
          addressListStr += item + ",";
        }
        addressListStr = addressListStr.substring(0, addressListStr.length() - 1);
      }
      xxlJobGroup.setAddressList(addressListStr);
    } else {
      // 1=Manual entry
      if (xxlJobGroup.getAddressList() == null
          || xxlJobGroup.getAddressList().trim().length() == 0) {
        return new ReturnT<String>(500, I18nUtil.getString("jobgroup_field_addressType_limit"));
      }
      String[] addresss = xxlJobGroup.getAddressList().split(",");
      for (String item : addresss) {
        if (item == null || item.trim().length() == 0) {
          return new ReturnT<String>(
              500, I18nUtil.getString("jobgroup_field_registryList_unvalid"));
        }
      }
    }

    int ret = xxlJobGroupDao.update(xxlJobGroup);
    return (ret > 0) ? com.xxl.job.admin.core.util.ReturnTCompat.success() : com.xxl.job.admin.core.util.ReturnTCompat.fail();
  }

  private List<String> findRegistryByAppName(String appnameParam) {
    HashMap<String, List<String>> appAddressMap = new HashMap<String, List<String>>();
    List<XxlJobRegistry> list = xxlJobRegistryDao.findAll(RegistryConfig.DEAD_TIMEOUT, new Date());
    if (list != null) {
      for (XxlJobRegistry item : list) {
        if (RegistryConfig.RegistType.EXECUTOR.name().equals(item.getRegistryGroup())) {
          String appname = item.getRegistryKey();
          List<String> registryList = appAddressMap.get(appname);
          if (registryList == null) {
            registryList = new ArrayList<String>();
          }

          if (!registryList.contains(item.getRegistryValue())) {
            registryList.add(item.getRegistryValue());
          }
          appAddressMap.put(appname, registryList);
        }
      }
    }
    return appAddressMap.get(appnameParam);
  }

  @PostMapping("/remove")
  @ResponseBody
  public ReturnT<String> remove(int id) {

    // valid
    int count = xxlJobInfoDao.pageListCount(0, 10, id, -1, null, null, null);
    if (count > 0) {
      return new ReturnT<String>(500, I18nUtil.getString("jobgroup_del_limit_0"));
    }

    List<XxlJobGroup> allList = xxlJobGroupDao.findAll();
    if (allList.size() == 1) {
      return new ReturnT<String>(500, I18nUtil.getString("jobgroup_del_limit_1"));
    }

    int ret = xxlJobGroupDao.remove(id);
    return (ret > 0) ? com.xxl.job.admin.core.util.ReturnTCompat.success() : com.xxl.job.admin.core.util.ReturnTCompat.fail();
  }

  @PostMapping("/loadById")
  @ResponseBody
  public ReturnT<XxlJobGroup> loadById(int id) {
    XxlJobGroup jobGroup = xxlJobGroupDao.load(id);
    return jobGroup != null
        ? com.xxl.job.admin.core.util.ReturnTCompat.success(jobGroup)
        : new ReturnT<XxlJobGroup>(ReturnT.FAIL_CODE, null);
  }
}
