package org.jeecg.modules.monitor.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.alibaba.fastjson.JSONArray;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.swing.filechooser.FileSystemView;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.modules.monitor.domain.RedisInfo;
import org.jeecg.modules.monitor.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description: ActuatorRedisController
 *
 * @author: jeecg-boot
 */
@Slf4j
@RestController
@RequestMapping("/sys/actuator/redis")
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class ActuatorRedisController {

  @Autowired private RedisService redisService;

  /**
   * Redis details
   *
   * @return
   * @throws Exception
   */
  @PostMapping("/info")
  public Result<?> getRedisInfo() throws Exception {
    List<RedisInfo> infoList = this.redisService.getRedisInfo();
    // log.info(infoList.toString());
    return Result.ok(infoList);
  }

  // update-begin---author:chenrui ---date:20240514  for: [QQYUN-9247] System monitoring feature optimization ------------
  /**
   * Redis historical performance metrics (past hour)
   *
   * @return
   * @throws Exception
   * @author chenrui
   * @date 2024/5/14 14:56
   */
  @PostMapping(value = "/metrics/history")
  public Result<?> getMetricsHistory() throws Exception {
    Map<String, List<Map<String, Object>>> metricsHistory = this.redisService.getMetricsHistory();
    return Result.OK(metricsHistory);
  }

  // update-end---author:chenrui ---date:20240514  for: [QQYUN-9247] System monitoring feature optimization ------------

  @PostMapping("/keysSize")
  public Map<String, Object> getKeysSize() throws Exception {
    return redisService.getKeysSize();
  }

  /**
   * Get Redis key count for reports
   *
   * @return
   * @throws Exception
   */
  @PostMapping("/keysSizeForReport")
  public Map<String, JSONArray> getKeysSizeReport() throws Exception {
    return redisService.getMapForReport("1");
  }

  /**
   * Get Redis memory usage for reports
   *
   * @return
   * @throws Exception
   */
  @PostMapping("/memoryForReport")
  public Map<String, JSONArray> memoryForReport() throws Exception {
    return redisService.getMapForReport("2");
  }

  /**
   * Get full Redis info for reports
   *
   * @return
   * @throws Exception
   */
  @PostMapping("/infoForReport")
  public Map<String, JSONArray> infoForReport() throws Exception {
    return redisService.getMapForReport("3");
  }

  @PostMapping("/memoryInfo")
  public Map<String, Object> getMemoryInfo() throws Exception {
    return redisService.getMemoryInfo();
  }

  /**
   * CodePlan 36 / C2-3 / 36-ADM-4.2: admin Redis tool must call this SCAN API — never KEYS.
   *
   * @param pattern MATCH pattern (e.g. {@code sys:cache:dict*}); bare {@code *} rejected
   * @param limit max keys (default 200, hard-capped by RedisScanSupport)
   */
  @PostMapping("/scanKeys")
  public Result<?> scanKeys(
      @RequestParam("pattern") String pattern,
      @RequestParam(value = "limit", defaultValue = "200") long limit) {
    Set<String> keys = redisService.scanKeysByPattern(pattern, limit);
    Map<String, Object> body = new HashMap<>(4);
    body.put("pattern", pattern);
    body.put("count", keys == null ? 0 : keys.size());
    body.put("keys", keys == null ? List.of() : keys);
    body.put("scan", true);
    return Result.ok(body);
  }

  // update-begin--Author:zhangweijian  Date:20190425 for: Get disk info
  /**
   * @Function: Get disk info
   *
   * @param request
   * @param response
   * @return
   */
  @PostMapping("/queryDiskInfo")
  public Result<List<Map<String, Object>>> queryDiskInfo(
      HttpServletRequest request, HttpServletResponse response) {
    Result<List<Map<String, Object>>> res = new Result<>();
    try {
      // Current file system class
      FileSystemView fsv = FileSystemView.getFileSystemView();
      // List all Windows disks
      File[] fs = File.listRoots();
      log.info("Query disk info:" + fs.length + "item(s)");
      List<Map<String, Object>> list = new ArrayList<>();

      for (int i = 0; i < fs.length; i++) {
        if (fs[i].getTotalSpace() == 0) {
          continue;
        }
        Map<String, Object> map = new HashMap(5);
        map.put("name", fsv.getSystemDisplayName(fs[i]));
        map.put("max", fs[i].getTotalSpace());
        map.put("rest", fs[i].getFreeSpace());
        map.put(
            "restPPT",
            (fs[i].getTotalSpace() - fs[i].getFreeSpace()) * 100 / fs[i].getTotalSpace());
        list.add(map);
        log.info(map.toString());
      }
      res.setResult(list);
      res.success(I18nUtil.getMessage("common.querySuccess"));
    } catch (Exception e) {
      res.error500(I18nUtil.getMessage("common.queryFailed", new Object[] {e.getMessage()}));
    }
    return res;
  }
  // update-end--Author:zhangweijian  Date:20190425 for: Get disk info
}
