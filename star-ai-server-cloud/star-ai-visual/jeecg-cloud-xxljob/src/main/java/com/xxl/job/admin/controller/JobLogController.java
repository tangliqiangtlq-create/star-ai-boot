package com.xxl.job.admin.controller;

import com.xxl.job.admin.core.exception.XxlJobException;
import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.scheduler.XxlJobScheduler;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.dao.XxlJobGroupDao;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.admin.dao.XxlJobLogDao;
import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.model.KillParam;
import com.xxl.job.core.biz.model.LogParam;
import com.xxl.job.core.biz.model.LogResult;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * index controller
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@Controller
@RequestMapping("/joblog")
public class JobLogController {
  private static Logger logger = LoggerFactory.getLogger(JobLogController.class);

  @Resource private XxlJobGroupDao xxlJobGroupDao;
  @Resource public XxlJobInfoDao xxlJobInfoDao;
  @Resource public XxlJobLogDao xxlJobLogDao;

  @GetMapping
  public String index(
      HttpServletRequest request,
      Model model,
      @RequestParam(required = false, defaultValue = "0") Integer jobId) {

    // Executor list
    List<XxlJobGroup> jobGroupList_all = xxlJobGroupDao.findAll();

    // filter group
    List<XxlJobGroup> jobGroupList =
        JobInfoController.filterJobGroupByRole(request, jobGroupList_all);
    if (jobGroupList == null || jobGroupList.size() == 0) {
      throw new XxlJobException(I18nUtil.getString("jobgroup_empty"));
    }

    model.addAttribute("JobGroupList", jobGroupList);

    // Job
    if (jobId > 0) {
      XxlJobInfo jobInfo = xxlJobInfoDao.loadById(jobId);
      if (jobInfo == null) {
        throw new RuntimeException(
            I18nUtil.getString("jobinfo_field_id") + I18nUtil.getString("system_unvalid"));
      }

      model.addAttribute("jobInfo", jobInfo);

      // valid permission
      JobInfoController.validPermission(request, jobInfo.getJobGroup());
    }

    return "joblog/joblog.index";
  }

  @PostMapping("/getJobsByGroup")
  @ResponseBody
  public ReturnT<List<XxlJobInfo>> getJobsByGroup(int jobGroup) {
    List<XxlJobInfo> list = xxlJobInfoDao.getJobsByGroup(jobGroup);
    return com.xxl.job.admin.core.util.ReturnTCompat.success(list);
  }

  @PostMapping("/pageList")
  @ResponseBody
  public Map<String, Object> pageList(
      HttpServletRequest request,
      @RequestParam(required = false, defaultValue = "0") int start,
      @RequestParam(required = false, defaultValue = "10") int length,
      int jobGroup,
      int jobId,
      int logStatus,
      String filterTime) {

    // valid permission
    JobInfoController.validPermission(request, jobGroup); // Only administrators support querying all; ordinary users only support querying jobGroups that have permissions.

    // parse param
    Date triggerTimeStart = null;
    Date triggerTimeEnd = null;
    if (filterTime != null && filterTime.trim().length() > 0) {
      String[] temp = filterTime.split(" - ");
      if (temp.length == 2) {
        triggerTimeStart = DateUtil.parseDateTime(temp[0]);
        triggerTimeEnd = DateUtil.parseDateTime(temp[1]);
      }
    }

    // page query
    List<XxlJobLog> list =
        xxlJobLogDao.pageList(
            start, length, jobGroup, jobId, triggerTimeStart, triggerTimeEnd, logStatus);
    int list_count =
        xxlJobLogDao.pageListCount(
            start, length, jobGroup, jobId, triggerTimeStart, triggerTimeEnd, logStatus);

    // package result
    Map<String, Object> maps = new HashMap<String, Object>();
    maps.put("recordsTotal", list_count); // Total number of records
    maps.put("recordsFiltered", list_count); // Total number of records after filtering
    maps.put("data", list); // PaginationList
    return maps;
  }

  @GetMapping("/logDetailPage")
  public String logDetailPage(int id, Model model) {

    // base check
    XxlJobLog jobLog = xxlJobLogDao.load(id);
    if (jobLog == null) {
      throw new RuntimeException(I18nUtil.getString("joblog_logid_unvalid"));
    }

    model.addAttribute("triggerCode", jobLog.getTriggerCode());
    model.addAttribute("handleCode", jobLog.getHandleCode());
    model.addAttribute("executorAddress", jobLog.getExecutorAddress());
    model.addAttribute("triggerTime", jobLog.getTriggerTime().getTime());
    model.addAttribute("logId", jobLog.getId());
    return "joblog/joblog.detail";
  }

  @PostMapping("/logDetailCat")
  @ResponseBody
  public ReturnT<LogResult> logDetailCat(
      String executorAddress, long triggerTime, long logId, int fromLineNum) {
    try {
      ExecutorBiz executorBiz = XxlJobScheduler.getExecutorBiz(executorAddress);
      ReturnT<LogResult> logResult = executorBiz.log(new LogParam(triggerTime, logId, fromLineNum));

      // is end
      if (logResult.getContent() != null
          && logResult.getContent().getFromLineNum() > logResult.getContent().getToLineNum()) {
        XxlJobLog jobLog = xxlJobLogDao.load(logId);
        if (jobLog.getHandleCode() > 0) {
          logResult.getContent().setEnd(true);
        }
      }

      return logResult;
    } catch (Exception e) {
      logger.error("Query job log detail failed", e);
      return new ReturnT<LogResult>(ReturnT.FAIL_CODE, "query log detail failed");
    }
  }

  @PostMapping("/logKill")
  @ResponseBody
  public ReturnT<String> logKill(int id) {
    // base check
    XxlJobLog log = xxlJobLogDao.load(id);
    XxlJobInfo jobInfo = xxlJobInfoDao.loadById(log.getJobId());
    if (jobInfo == null) {
      return new ReturnT<String>(500, I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
    }
    if (ReturnT.SUCCESS_CODE != log.getTriggerCode()) {
      return new ReturnT<String>(500, I18nUtil.getString("joblog_kill_log_limit"));
    }

    // request of kill
    ReturnT<String> runResult = null;
    try {
      ExecutorBiz executorBiz = XxlJobScheduler.getExecutorBiz(log.getExecutorAddress());
      runResult = executorBiz.kill(new KillParam(jobInfo.getId()));
    } catch (Exception e) {
      logger.error("Kill job log execution failed", e);
      runResult = new ReturnT<String>(500, "kill execution failed");
    }

    if (ReturnT.SUCCESS_CODE == runResult.getCode()) {
      log.setHandleCode(ReturnT.FAIL_CODE);
      log.setHandleMsg(
          I18nUtil.getString("joblog_kill_log_byman")
              + ":"
              + (runResult.getMsg() != null ? runResult.getMsg() : ""));
      log.setHandleTime(new Date());
      xxlJobLogDao.updateHandleInfo(log);
      return com.xxl.job.admin.core.util.ReturnTCompat.success(runResult.getMsg());
    } else {
      return new ReturnT<String>(500, runResult.getMsg());
    }
  }

  @PostMapping("/clearLog")
  @ResponseBody
  public ReturnT<String> clearLog(int jobGroup, int jobId, int type) {

    Date clearBeforeTime = null;
    int clearBeforeNum = 0;
    if (type == 1) {
      clearBeforeTime = DateUtil.addMonths(new Date(), -1); // cleanup log data from one month ago
    } else if (type == 2) {
      clearBeforeTime = DateUtil.addMonths(new Date(), -3); // cleanup log data three months ago
    } else if (type == 3) {
      clearBeforeTime = DateUtil.addMonths(new Date(), -6); // cleanup log data six months ago
    } else if (type == 4) {
      clearBeforeTime = DateUtil.addYears(new Date(), -1); // cleanup log data from one year ago
    } else if (type == 5) {
      clearBeforeNum = 1000; // cleanup a thousand previous log data
    } else if (type == 6) {
      clearBeforeNum = 10000; // cleanup 10,000 previous log data
    } else if (type == 7) {
      clearBeforeNum = 30000; // Cleanup 30,000 previous log data
    } else if (type == 8) {
      clearBeforeNum = 100000; // Cleanup 100,000 previous log data
    } else if (type == 9) {
      clearBeforeNum = 0; // cleanup all log data
    } else {
      return new ReturnT<String>(
          ReturnT.FAIL_CODE, I18nUtil.getString("joblog_clean_type_unvalid"));
    }

    List<Long> logIds = null;
    do {
      logIds = xxlJobLogDao.findClearLogIds(jobGroup, jobId, clearBeforeTime, clearBeforeNum, 1000);
      if (logIds != null && logIds.size() > 0) {
        xxlJobLogDao.clearLog(logIds);
      }
    } while (logIds != null && logIds.size() > 0);

    return com.xxl.job.admin.core.util.ReturnTCompat.success();
  }
}
