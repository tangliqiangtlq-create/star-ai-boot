package org.jeecg.common.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class JobUtil {

  @Autowired private RestTemplate restTemplate;

  @Autowired private Environment environment;

  /**
   * Prefer camelCase (legacy) then kebab-case (Nacos / B8-1 handbook {@code admin-addresses}).
   */
  @Value("${jeecg.xxljob.adminAddresses:${jeecg.xxljob.admin-addresses:}}")
  private String adminAddresses;

  @Value("${spring.application.name}")
  private String appName;

  /**
   * Nacos writes {@code jeecg.xxljob.access-token}; {@code @Value} does not relax-bind like
   * {@code @ConfigurationProperties}, so kebab-case must be listed first. Env fallback covers IDE
   * runs that inject {@code XXL_JOB_ACCESS_TOKEN} without Nacos placeholder resolution.
   */
  @Value("${jeecg.xxljob.access-token:${jeecg.xxljob.accessToken:}}")
  private String accessToken;

  private static final String XXL_JOB_ACCESS_TOKEN_HEADER = "XXL-JOB-ACCESS-TOKEN";

  @PostConstruct
  void resolveAccessTokenFallback() {
    if (!StringUtils.hasText(accessToken)) {
      accessToken =
          firstNonBlank(
              environment.getProperty("jeecg.xxljob.access-token"),
              environment.getProperty("jeecg.xxljob.accessToken"),
              environment.getProperty("XXL_JOB_ACCESS_TOKEN"),
              System.getenv("XXL_JOB_ACCESS_TOKEN"),
              System.getenv("JEECG_XXLJOB_ACCESS_TOKEN"));
    }
    if (!StringUtils.hasText(adminAddresses)) {
      adminAddresses =
          firstNonBlank(
              environment.getProperty("jeecg.xxljob.adminAddresses"),
              environment.getProperty("jeecg.xxljob.admin-addresses"),
              System.getenv("JEECG_XXLJOB_ADMIN_ADDRESSES"),
              "http://127.0.0.1:9000/xxl-job-admin");
    }
    if (!StringUtils.hasText(accessToken)) {
      log.warn(
          "[JobUtil] jeecg.xxljob.access-token / XXL_JOB_ACCESS_TOKEN is empty. "
              + "Admin API calls (pageList/add/update) will 302 to /toLogin with empty body. "
              + "Set the same token on DCS and xxljob.");
    } else {
      log.info(
          "[JobUtil] XXL Admin API token loaded (len={}), adminAddresses={}",
          accessToken.trim().length(),
          adminAddresses);
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value.trim();
      }
    }
    return null;
  }

  public String addJob(
      String jobGroup,
      String jobDesc,
      String jobCron,
      String executorHandler,
      String executorParam) {

    MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
    map.add("jobGroupName", jobGroup);
    map.add("jobDesc", jobDesc);
    map.add("executorRouteStrategy", "ROUND");
    map.add("jobCron", jobCron);
    map.add("scheduleConf", jobCron);
    map.add("glueType", "BEAN");
    map.add("executorHandler", executorHandler);
    map.add("executorBlockStrategy", "SERIAL_EXECUTION");
    map.add("executorTimeout", "0");
    map.add("executorFailRetryCount", "0");
    map.add("author", "admin");
    map.add("scheduleType", "CRON");
    map.add("misfireStrategy", "FIRE_ONCE_NOW");
    map.add("executorParam", executorParam);
    map.add("glueRemark", "GLUE code initialization");

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            adminAddresses + "/jobinfo/add", formEntity(map), String.class);

    JSONObject jsonObject = parseJobResponse("add", response);

    if (jsonObject.getInteger("code") == 200) {
      startJob(jsonObject.getString("content"));
      return jsonObject.getString("content");

    } else {

      throw new JeecgBootException(jsonObject.getString("msg"));
    }
  }

  public String addJob(
      String jobDesc, String jobCron, String executorHandler, String executorParam) {

    try {
      return addJob(appName, jobDesc, jobCron, executorHandler, executorParam);
    } catch (Exception e) {
      log.error("operation failed", e);
      log.error("Failed to add scheduled task, cron:{}", jobCron);
      throw new JeecgBootException(e.getMessage());
    }
  }

  public void startJob(String jobId) {

    MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
    map.add("id", jobId);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            adminAddresses + "/jobinfo/start", formEntity(map), String.class);

    JSONObject jsonObject = parseJobResponse("start", response);

    if (jsonObject.getInteger("code") != 200) {
      log.error(response.getBody());
      throw new JeecgBootException(jsonObject.getString("msg"));
    }
  }

  public void stopJob(String jobId) {
    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("id", jobId);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            adminAddresses + "/jobinfo/stop", formEntity(map), String.class);

    JSONObject jsonObject = parseJobResponse("stop", response);
    if (jsonObject.getInteger("code") != 200) {
      log.error(response.getBody());
      throw new JeecgBootException(
          StringUtils.hasText(jsonObject.getString("msg"))
              ? jsonObject.getString("msg")
              : "Failed to stop scheduled job");
    }
  }

  /**
   * Find XXL-JOB by executorHandler Exact match (jobGroup=0 / triggerStatus=-1 means no filtering).
   * If LIKE hits multiple LIKEs, the one with exactly equal handlers will be selected first.
   */
  public JSONObject findJobByHandler(String executorHandler) {
    if (!StringUtils.hasText(executorHandler)) {
      return null;
    }
    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("start", "0");
    map.add("length", "50");
    map.add("jobGroup", "0");
    map.add("triggerStatus", "-1");
    map.add("jobDesc", "");
    map.add("executorHandler", executorHandler.trim());
    map.add("author", "");

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            adminAddresses + "/jobinfo/pageList", formEntity(map), String.class);

    JSONObject page = parseJobResponse("pageList", response);
    JSONArray data = page.getJSONArray("data");
    if (data == null || data.isEmpty()) {
      return null;
    }
    JSONObject exact = null;
    for (int i = 0; i < data.size(); i++) {
      JSONObject item = data.getJSONObject(i);
      if (item == null) {
        continue;
      }
      if (executorHandler.trim().equals(item.getString("executorHandler"))) {
        exact = item;
        break;
      }
    }
    return exact != null ? exact : data.getJSONObject(0);
  }

  /** Write back cron using existing JobInfo Field (XXL update interface requires complete Field). */
  public void updateJobCron(JSONObject existingJob, String jobCron) {
    if (existingJob == null || existingJob.getInteger("id") == null) {
      throw new JeecgBootException("XXL-Job update missing job id");
    }
    if (!StringUtils.hasText(jobCron)) {
      throw new JeecgBootException("XXL-Job cron cannot be empty");
    }

    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("id", String.valueOf(existingJob.getInteger("id")));
    map.add("jobGroup", String.valueOf(existingJob.getInteger("jobGroup")));
    map.add("jobDesc", nullToEmpty(existingJob.getString("jobDesc")));
    map.add("author", StringUtils.hasText(existingJob.getString("author"))
        ? existingJob.getString("author")
        : "admin");
    map.add("alarmEmail", nullToEmpty(existingJob.getString("alarmEmail")));
    map.add(
        "executorRouteStrategy",
        StringUtils.hasText(existingJob.getString("executorRouteStrategy"))
            ? existingJob.getString("executorRouteStrategy")
            : "ROUND");
    map.add("executorHandler", nullToEmpty(existingJob.getString("executorHandler")));
    map.add("executorParam", nullToEmpty(existingJob.getString("executorParam")));
    map.add(
        "executorBlockStrategy",
        StringUtils.hasText(existingJob.getString("executorBlockStrategy"))
            ? existingJob.getString("executorBlockStrategy")
            : "SERIAL_EXECUTION");
    map.add(
        "executorTimeout",
        existingJob.get("executorTimeout") == null
            ? "0"
            : String.valueOf(existingJob.getInteger("executorTimeout")));
    map.add(
        "executorFailRetryCount",
        existingJob.get("executorFailRetryCount") == null
            ? "0"
            : String.valueOf(existingJob.getInteger("executorFailRetryCount")));
    map.add("childJobId", nullToEmpty(existingJob.getString("childJobId")));
    map.add("jobCron", jobCron);
    map.add("scheduleConf", jobCron);
    map.add("scheduleType", "CRON");
    if (StringUtils.hasText(existingJob.getString("glueType"))) {
      map.add("glueType", existingJob.getString("glueType"));
    }

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            adminAddresses + "/jobinfo/update", formEntity(map), String.class);

    JSONObject jsonObject = parseJobResponse("update", response);
    if (jsonObject.getInteger("code") == null || jsonObject.getInteger("code") != 200) {
      log.error(response.getBody());
      throw new JeecgBootException(
          StringUtils.hasText(jsonObject.getString("msg"))
              ? jsonObject.getString("msg")
              : "Update XXL-Job cron Failed");
    }
  }

  public void removeJob(Integer jobId) {

    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("id", jobId.toString());

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            adminAddresses + "/jobinfo/remove", formEntity(map), String.class);

    JSONObject jsonObject = parseJobResponse("remove", response);

    if (jsonObject.getInteger("code") != 200) {
      log.error(response.getBody());
      throw new JeecgBootException("Failed to delete scheduled task");
    }
  }

  private HttpEntity<MultiValueMap<String, String>> formEntity(MultiValueMap<String, String> map) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    String token = accessToken;
    if (!StringUtils.hasText(token)) {
      token =
          firstNonBlank(
              environment != null ? environment.getProperty("jeecg.xxljob.access-token") : null,
              environment != null ? environment.getProperty("jeecg.xxljob.accessToken") : null,
              System.getenv("XXL_JOB_ACCESS_TOKEN"));
    }
    if (StringUtils.hasText(token)) {
      headers.set(XXL_JOB_ACCESS_TOKEN_HEADER, token.trim());
    } else {
      log.warn(
          "[JobUtil] calling XXL Admin without {} header (token empty)",
          XXL_JOB_ACCESS_TOKEN_HEADER);
    }
    return new HttpEntity<>(map, headers);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private JSONObject parseJobResponse(String api, ResponseEntity<String> response) {
    String body = response.getBody();
    HttpStatusCode status = response.getStatusCode();
    if (body == null || body.trim().isEmpty()) {
      log.error(
          "XXL-Job {} response body empty, status={}, adminAddresses={}, tokenPresent={}",
          api,
          status,
          adminAddresses,
          StringUtils.hasText(accessToken));
      String detail =
          status != null && status.is3xxRedirection()
              ? " HTTP redirect to login (token missing or mismatch). Set the same XXL_JOB_ACCESS_TOKEN on DCS (jeecg.xxljob.access-token) and xxljob (xxl.job.accessToken)."
              : " Confirm xxljob is running and jeecg.xxljob.adminAddresses includes /xxl-job-admin context path.";
      throw new JeecgBootException(
          "XXL-Job " + api + " response empty." + detail + " admin=" + adminAddresses);
    }

    JSONObject jsonObject = JSONObject.parseObject(body);
    if (jsonObject == null) {
      log.error(
          "XXL-Job {} response not JSON, status={}, body={}",
          api,
          status,
          body.length() > 500 ? body.substring(0, 500) + "..." : body);
      throw new JeecgBootException(
          "XXL-Job response format abnormal. Check adminAddresses includes /xxl-job-admin and "
              + "jeecg.xxljob.access-token matches xxl.job.accessToken in xxljob service");
    }
    return jsonObject;
  }
}
