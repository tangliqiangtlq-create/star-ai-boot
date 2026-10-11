package org.jeecg.modules.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.base.Joiner;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.ProgressListener;
import org.jeecg.common.api.dto.DataLogDTO;
import org.jeecg.common.api.dto.DenyAuditDTO;
import org.jeecg.common.api.dto.FileBytesUploadDTO;
import org.jeecg.common.api.dto.LogDTO;
import org.jeecg.common.api.dto.OnlineAuthDTO;
import org.jeecg.common.api.dto.message.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.UrlMatchEnum;
import org.jeecg.common.constant.*;
import org.jeecg.common.constant.enums.EmailTemplateEnum;
import org.jeecg.common.constant.enums.MessageTypeEnum;
import org.jeecg.common.constant.enums.SysAnnmentTypeEnum;
import org.jeecg.common.desensitization.util.SensitiveInfoUtil;
import org.jeecg.common.enem.UsageScenarios;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.query.QueryCondition;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.query.QueryRuleEnum;
import org.jeecg.common.system.vo.*;
import org.jeecg.common.util.ByteArrayMultipartFile;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.FileNameUtil;
import org.jeecg.common.util.FilePreviewUtils;
import org.jeecg.common.util.LogoLoader;
import org.jeecg.common.util.HTMLUtils;
import org.jeecg.common.util.RequestUtils;
import org.jeecg.common.util.YouBianCodeUtil;
import org.jeecg.common.util.dynamic.db.FreemarkerParseFactory;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.config.firewall.SqlInjection.IDictTableWhiteListHandler;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.base.mapper.BaseCommonMapper;
import org.jeecg.modules.message.entity.SysMessageTemplate;
import org.jeecg.modules.message.handle.impl.DdSendMsgHandle;
import org.jeecg.modules.message.handle.impl.EmailSendMsgHandle;
import org.jeecg.modules.message.handle.impl.QywxSendMsgHandle;
import org.jeecg.modules.message.handle.impl.SystemSendMsgHandle;
import org.jeecg.modules.message.service.ISysMessageTemplateService;
import org.jeecg.modules.message.websocket.WebSocket;
import org.jeecg.modules.oss.entity.OssFile;
import org.jeecg.modules.oss.service.IOssFileService;
import org.jeecg.modules.system.entity.*;
import org.jeecg.modules.system.mapper.*;
import org.jeecg.modules.system.service.*;
import org.jeecg.modules.system.util.SecurityUtil;
import org.jeecg.modules.system.vo.lowapp.SysDictVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.PathMatcher;
import org.springframework.web.multipart.MultipartFile;

/**
 * @ Description: Underlying common business API with other independent module calls @ Author: scott @ Date: 2019-4-20 @ Version: v1.0
 */
@Slf4j
@Service
public class SysBaseApiImpl implements ISysBaseAPI {
  /** Current system database type */
  private static String DB_TYPE = "";

  @Autowired private ISysMessageTemplateService sysMessageTemplateService;
  @Resource private SysUserMapper userMapper;
  @Resource private SysUserRoleMapper sysUserRoleMapper;
  @Autowired private ISysDepartService sysDepartService;
  @Autowired private ISysDictService sysDictService;
  // NOTE: cleaned - @Resource private SysAnnouncementMapper sysAnnouncementMapper;
  // NOTE: cleaned - @Resource private SysAnnouncementSendMapper sysAnnouncementSendMapper;
  @Resource private WebSocket webSocket;
  @Resource private SysRoleMapper roleMapper;
  @Resource private SysDepartMapper departMapper;
  @Resource private SysCategoryMapper categoryMapper;
  @Autowired private ISysUserDepartService sysUserDepartService;
  @Resource private SysPermissionMapper sysPermissionMapper;
  @Autowired private ISysPermissionDataRuleService sysPermissionDataRuleService;
  // NOTE: cleaned - @Autowired private ThirdAppWechatEnterpriseServiceImpl wechatEnterpriseService;
  // NOTE: cleaned - @Autowired private ThirdAppDingtalkServiceImpl dingtalkService;
  @Autowired ISysCategoryService sysCategoryService;
  @Autowired private ISysUserService sysUserService;
  @Autowired private ISysDataLogService sysDataLogService;
  @Autowired private ISysRoleService sysRoleService;

  /** Shard owner of sys_deny_audit (present only in the system service). */

  /** Schema probe used by the H6 deny-audit gate; optional so DB-less unit tests can build this bean. */

  @Value("${oss_domain}")
  private String fileDomain;

  @Override
  // @SensitiveDecode
  public LoginUser getUserByName(String username) {
    // update-begin-author: taoyan date: 2022-6-6 for: VUEN-1276 [v3 flowchart] Test bug
    // 1. View the history and flowchart preview questions through the process or process instance I initiated
    if (oConvertUtils.isEmpty(username)) {
      return null;
    }
    // update-end-author: taoyan date: 2022-6-6 for: VUEN-1276 [v3 flowchart] Test bug
    // 1. View the history and flowchart preview questions through the process or process instance I initiated
    LoginUser user = sysUserService.getEncodeUserInfo(username);

    // When calling between methods in the same class, desensitization and decryption Aop will fail. It is too important to obtain user information. Here, the native decryption method is used, and the @ SensitiveDecodeAble annotation method is not used.
    try {
      SensitiveInfoUtil.handlerObject(user, false);
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }

    return user;
  }

  /**
   * P3 (CodePlan-SensitiveColumnLeakage-Fix): in-service password verification for trusted S2S
   * callers (dcs). The caller supplies a plaintext password; we return a boolean so the stored
   * hash never has to travel over Feign. Every failure mode (blank input, missing user,
   * malformed/unsupported hash, exception) collapses to {@code false} — fail-closed.
   *
   * <p>The credential projection ({@code SysUserMapper.selectCredentialByUsername}) is pinned
   * in {@code SensitiveTableBlacklistInterceptor} and is C6-allowed only for {@code s2s:*}
   * sessions; the controller endpoint lives under /sys/api (S2S namespace, @SaCheckLogin).
   */
  @Override
  public boolean verifyPassword(String username, String rawPassword) {
    if (oConvertUtils.isEmpty(username)
        || !org.jeecg.common.util.CredentialGuard.isPresentLoginPassword(rawPassword)) {
      return false;
    }
    try {
      SysUser credential = userMapper.selectCredentialByUsername(username);
      if (credential == null
          || !org.jeecg.common.util.CredentialGuard.hasStoredPasswordHash(
              credential.getPassword())) {
        log.info("S2S verifyPassword: no credential for username=[{}]", username);
        return false;
      }
      boolean matches =
          org.jeecg.common.util.crypto.SmCryptoUtil.verifyPasswordWithLegacyFallback(
              username, rawPassword, credential.getSalt(), credential.getPassword());
      log.info(
          "S2S verifyPassword: username=[{}] result={}", username, matches);
      return matches;
    } catch (Exception e) {
      // Never leak exception detail to the caller; denial is the safe default.
      log.warn("S2S verifyPassword error for username=[{}]: {}", username, e.toString());
      return false;
    }
  }

  @Override
  @Cacheable(cacheNames = CommonConstant.SYS_USER_ID_MAPPING_CACHE, key = "#username")
  public String getUserIdByName(String username) {
    if (oConvertUtils.isEmpty(username)) {
      return null;
    }
    String userId = userMapper.getUserIdByName(username);
    return userId;
  }

  @Override
  public String translateDictFromTable(String table, String text, String code, String key) {
    return sysDictService.queryTableDictTextByKey(table, text, code, key);
  }

  @Override
  public String translateDict(String code, String key) {
    return sysDictService.queryDictTextByKey(code, key);
  }

  @Override
  public List<SysPermissionDataRuleModel> queryPermissionDataRule(
      String component, String requestPath, String username) {
    List<SysPermission> currentSyspermission = null;
    if (oConvertUtils.isNotEmpty(component)) {
      // 1. Get the menu by annotating the property pageComponent
      LambdaQueryWrapper<SysPermission> query = new LambdaQueryWrapper<SysPermission>();
      query.eq(SysPermission::getDelFlag, 0);
      query.eq(SysPermission::getComponent, component);
      currentSyspermission = sysPermissionMapper.selectList(query);
    } else {
      // 1. Request the address query menu directly from the front-end
      LambdaQueryWrapper<SysPermission> query = new LambdaQueryWrapper<SysPermission>();
      query.eq(SysPermission::getMenuType, 2);
      query.eq(SysPermission::getDelFlag, 0);

      // update-begin-author: taoyan date: 2023-2-21 for: Solving parameter order problems
      List<String> allPossiblePaths = this.getOnlinePossiblePaths(requestPath);
      log.info("Retrieved menu URL = {}", allPossiblePaths.toString());
      if (allPossiblePaths.size() == 1) {
        query.eq(SysPermission::getUrl, requestPath);
      } else {
        query.in(SysPermission::getUrl, allPossiblePaths);
      }
      // update-end-author: taoyan date: 2023-2-21 for: Solving parameter order problems

      currentSyspermission = sysPermissionMapper.selectList(query);
      // 2. No custom matching URLs found to get menus
      if (currentSyspermission == null || currentSyspermission.size() == 0) {
        // Match menus by custom URL rules (menu-configured data rules applied on data APIs)
        String userMatchUrl = UrlMatchEnum.getMatchResultByUrl(requestPath);
        LambdaQueryWrapper<SysPermission> queryQserMatch = new LambdaQueryWrapper<SysPermission>();
        // update-begin-author:taoyan date:20211027 for: online top-level menu missed permission; drop menuType=1 filter
        // queryQserMatch.eq(SysPermission::getMenuType, 1);
        // update-end-author:taoyan date:20211027 for: online top-level menu missed permission; drop menuType=1 filter
        queryQserMatch.eq(SysPermission::getDelFlag, 0);
        queryQserMatch.eq(SysPermission::getUrl, userMatchUrl);
        if (oConvertUtils.isNotEmpty(userMatchUrl)) {
          currentSyspermission = sysPermissionMapper.selectList(queryQserMatch);
        }
      }
      // 3.if not found, match menu via regex
      if (currentSyspermission == null || currentSyspermission.size() == 0) {
        // Match permission config via regex
        String regUrl = getRegexpUrl(requestPath);
        if (regUrl != null) {
          currentSyspermission =
              sysPermissionMapper.selectList(
                  new LambdaQueryWrapper<SysPermission>()
                      .eq(SysPermission::getMenuType, 2)
                      .eq(SysPermission::getUrl, regUrl)
                      .eq(SysPermission::getDelFlag, 0));
        }
      }
    }
    if (currentSyspermission != null && currentSyspermission.size() > 0) {
      List<SysPermissionDataRuleModel> dataRules = new ArrayList<SysPermissionDataRuleModel>();
      for (SysPermission sysPermission : currentSyspermission) {
        // update-begin--Author:scott Date:20191119 for：data-rule code naming conflict with same package/class #722
        List<SysPermissionDataRule> temp =
            sysPermissionDataRuleService.queryPermissionDataRules(username, sysPermission.getId());
        if (temp != null && temp.size() > 0) {
          // dataRules.addAll(temp);
          dataRules = oConvertUtils.entityListToModelList(temp, SysPermissionDataRuleModel.class);
        }
        // update-end--Author:scott Date:20191119 for：data-rule code naming conflict with same package/class #722
      }
      return dataRules;
    }
    return null;
  }

  /** Match front-end URL; on success return regex path. AntPathMatcher: ()* zero+ chars, ()** zero+ directories */
  private String getRegexpUrl(String url) {
    List<String> list = sysPermissionMapper.queryPermissionUrlWithStar();
    if (list != null && list.size() > 0) {
      for (String p : list) {
        PathMatcher matcher = new AntPathMatcher();
        if (matcher.match(p, url)) {
          return p;
        }
      }
    }
    return null;
  }

  @Override
  public SysUserCacheInfo getCacheUser(String username) {
    SysUserCacheInfo info = new SysUserCacheInfo();
    info.setOneDepart(true);
    LoginUser user = this.getUserByName(username);

    //		try {
    //			Handle AOP failure when @SensitiveDecodeAble decrypt is called within the same class
    //			SensitiveInfoUtil.handlerObject(user, false);
    //		} catch (IllegalAccessException e) {
    //			e.printStackTrace();
    //		}

    if (user != null) {
      info.setSysUserId(user.getId());
      info.setSysUserCode(user.getUsername());
      info.setSysUserName(user.getRealname());
      info.setSysOrgCode(user.getOrgCode());
      info.setSysOrgId(user.getOrgId());
      info.setSysRoleCode(user.getRoleCode());
    } else {
      return null;
    }
    // Multi-department support in query
    List<SysDepart> list = departMapper.queryUserDeparts(user.getId());
    List<String> sysMultiOrgCode = new ArrayList<String>();
    if (list == null || list.size() == 0) {
      // The current user has no department
      // sysMultiOrgCode.add("0");
    } else if (list.size() == 1) {
      sysMultiOrgCode.add(list.get(0).getOrgCode());
    } else {
      info.setOneDepart(false);
      for (SysDepart dpt : list) {
        sysMultiOrgCode.add(dpt.getOrgCode());
      }
    }
    info.setSysMultiOrgCode(sysMultiOrgCode);
    return info;
  }

  @Override
  public LoginUser getUserById(String id) {
    if (oConvertUtils.isEmpty(id)) {
      return null;
    }
    LoginUser loginUser = new LoginUser();
    // P2 (CodePlan §4.3.1): C6-safe select — whitelisted columns only, so the query passes the
    // firewall for authenticated non-admin callers (incl. S2S/Feign) and never materializes
    // password/salt/reason/... in the first place.
    SysUser sysUser = userMapper.selectInfoById(id);
    if (sysUser == null) {
      return null;
    }
    BeanUtils.copyProperties(sysUser, loginUser);
    // Remove sensitive user information (P0: clear all sensitive fields, not just password)
    loginUser.clearSensitiveForResponse();
    loginUser.setRelTenantIds(null);
    loginUser.setDepartIds(null);
    return loginUser;
  }

  @Override
  public List<String> getRolesByUsername(String username) {
    return sysUserRoleMapper.getRoleByUserName(username);
  }

  @Override
  public List<String> getRolesByUserId(String userId) {
    return sysUserRoleMapper.getRoleCodeByUserId(userId);
  }

  @Override
  public List<String> getDepartIdsByUsername(String username) {
    List<SysDepart> list = sysDepartService.queryDepartsByUsername(username);
    List<String> result = new ArrayList<>(list.size());
    for (SysDepart depart : list) {
      result.add(depart.getId());
    }
    return result;
  }

  @Override
  public List<String> getDepartIdsByUserId(String userId) {
    return sysDepartService.queryDepartsByUserId(userId);
  }

  @Override
  public Set<String> getDepartParentIdsByUsername(String username) {
    List<SysDepart> list = sysDepartService.queryDepartsByUsername(username);
    Set<String> result = new HashSet<>(list.size());
    for (SysDepart depart : list) {
      result.add(depart.getParentId());
    }
    return result;
  }

  @Override
  public Set<String> getDepartParentIdsByDepIds(Set<String> depIds) {
    LambdaQueryWrapper<SysDepart> departQuery =
        new LambdaQueryWrapper<SysDepart>().in(SysDepart::getId, depIds);
    List<SysDepart> departList = departMapper.selectList(departQuery);

    if (CollectionUtils.isEmpty(departList)) {
      return null;
    }
    Set<String> parentIds =
        departList.stream().map(SysDepart::getParentId).collect(Collectors.toSet());
    return parentIds;
  }

  @Override
  public List<String> getDepartNamesByUsername(String username) {
    List<SysDepart> list = sysDepartService.queryDepartsByUsername(username);
    List<String> result = new ArrayList<>(list.size());
    for (SysDepart depart : list) {
      result.add(depart.getDepartName());
    }
    return result;
  }

  @Override
  public DictModel getParentDepartId(String departId) {
    SysDepart depart = departMapper.getParentDepartId(departId);
    DictModel model = new DictModel(depart.getId(), depart.getParentId());
    return model;
  }

  @Override
  @Cacheable(value = CacheConstant.SYS_DICT_CACHE, key = "#code", unless = "#result == null ")
  public List<DictModel> queryDictItemsByCode(String code) {
    return sysDictService.queryDictItemsByCode(code);
  }

  @Override
  @Cacheable(
      value = CacheConstant.SYS_ENABLE_DICT_CACHE,
      key = "#code",
      unless = "#result == null ")
  public List<DictModel> queryEnableDictItemsByCode(String code) {
    return sysDictService.queryEnableDictItemsByCode(code);
  }

  @Override
  public List<DictModel> queryTableDictItemsByCode(
      String tableFilterSql, String text, String code) {
    // [Online+ System] Dictionary table plus permission control mechanism logic, good idea LOWCOD-799
    if (tableFilterSql.indexOf(SymbolConstant.SYS_VAR_PREFIX) >= 0) {
      tableFilterSql = QueryGenerator.getSqlRuleValue(tableFilterSql);
    }
    return sysDictService.queryTableDictItemsByCode(tableFilterSql, text, code);
  }

  @Override
  public List<DictModel> queryAllDepartBackDictModel() {
    return sysDictService.queryAllDepartBackDictModel();
  }

  @Override
  // NOTE: cleaned - public void sendSysAnnouncement(MessageDTO message) {
    // NOTE: cleaned - this.sendSysAnnouncement(
        message.getFromUser(),
        message.getToUser(),
        message.getTitle(),
        message.getContent(),
        message.getCategory());
    try {
      // Sync and send third-party app messages
      // NOTE: cleaned - wechatEnterpriseService.sendMessage(message, true);
      // NOTE: cleaned - dingtalkService.sendMessage(message, true);
    } catch (Exception e) {
      log.error("Failed to sync and send third-party APP message!", e);
    }
  }

  @Override
  public void sendBusAnnouncement(BusMessageDTO message) {
    sendBusAnnouncement(
        message.getFromUser(),
        message.getToUser(),
        message.getTitle(),
        message.getContent(),
        message.getCategory(),
        message.getBusType(),
        message.getBusId());
    try {
      // Sync and send third-party app messages
      // NOTE: cleaned - wechatEnterpriseService.sendMessage(message, true);
      // NOTE: cleaned - dingtalkService.sendMessage(message, true);
    } catch (Exception e) {
      log.error("Failed to sync and send third-party APP message!", e);
    }
  }

  @Override
  public void sendTemplateAnnouncement(TemplateMessageDTO message) {
    String templateCode = message.getTemplateCode();
    String title = message.getTitle();
    Map<String, String> tmplateParam = message.getTemplateParam();
    String fromUser = message.getFromUser();
    String toUser = message.getToUser();

    List<SysMessageTemplate> sysSmsTemplates = sysMessageTemplateService.selectByCode(templateCode);
    if (sysSmsTemplates == null || sysSmsTemplates.size() == 0) {
      throw new JeecgBootException("Message template does not exist, template code: " + templateCode);
    }
    SysMessageTemplate sysSmsTemplate = sysSmsTemplates.get(0);
    // Template title
    title = title == null ? sysSmsTemplate.getTemplateName() : title;
    // Template content
    String content = sysSmsTemplate.getTemplateContent();
    if (tmplateParam != null) {
      for (Map.Entry<String, String> entry : tmplateParam.entrySet()) {
        String str = "${" + entry.getKey() + "}";
        if (oConvertUtils.isNotEmpty(title)) {
          title = title.replace(str, entry.getValue());
        }
        content = content.replace(str, entry.getValue());
      }
    }
    String mobileOpenUrl = null;
    if (tmplateParam != null
        && oConvertUtils.isNotEmpty(tmplateParam.get(CommonConstant.MSG_HREF_URL))) {
      mobileOpenUrl = tmplateParam.get(CommonConstant.MSG_HREF_URL);
    }
    // NOTE: cleaned - SysAnnouncement announcement = new SysAnnouncement();
    announcement.setTitile(title);
    announcement.setMsgContent(content);
    announcement.setSender(fromUser);
    announcement.setPriority(CommonConstant.PRIORITY_M);
    announcement.setMsgType(CommonConstant.MSG_TYPE_UESR);
    announcement.setSendStatus(CommonConstant.HAS_SEND);
    announcement.setSendTime(new Date());
    announcement.setMsgCategory(CommonConstant.MSG_CATEGORY_2);
    announcement.setDelFlag(String.valueOf(CommonConstant.DEL_FLAG_0));
    sysAnnouncementMapper.insert(announcement);
    // 2. Insert user announcement read-mark records
    String userId = toUser;
    String[] userIds = userId.split(",");
    String anntId = announcement.getId();
    for (int i = 0; i < userIds.length; i++) {
      if (oConvertUtils.isNotEmpty(userIds[i])) {
        SysUser sysUser = userMapper.getUserByName(userIds[i]);
        if (sysUser == null) {
          continue;
        }
        // NOTE: cleaned - SysAnnouncementSend announcementSend = new SysAnnouncementSend();
        announcementSend.setAnntId(anntId);
        announcementSend.setUserId(sysUser.getId());
        announcementSend.setReadFlag(CommonConstant.NO_READ_FLAG);
        sysAnnouncementSendMapper.insert(announcementSend);
        JSONObject obj = new JSONObject();
        obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_USER);
        obj.put(WebsocketConst.MSG_USER_ID, sysUser.getId());
        obj.put(WebsocketConst.MSG_ID, announcement.getId());
        obj.put(WebsocketConst.MSG_TXT, announcement.getTitile());
        webSocket.sendMessage(sysUser.getId(), obj.toJSONString());
      }
    }
    try {
      // Sync WeCom and DingTalk message notifications
      // NOTE: cleaned - dingtalkService.sendActionCardMessage(announcement, mobileOpenUrl, true);
      // NOTE: cleaned - wechatEnterpriseService.sendTextCardMessage(announcement, true);
    } catch (Exception e) {
      log.error("Failed to sync and send third-party APP message!", e);
    }
  }

  @Override
  public void sendBusTemplateAnnouncement(BusTemplateMessageDTO message) {
    String templateCode = message.getTemplateCode();
    String title = message.getTitle();
    Map<String, String> tmplateParam = message.getTemplateParam();
    String fromUser = message.getFromUser();
    String toUser = message.getToUser();
    String busId = message.getBusId();
    String busType = message.getBusType();

    List<SysMessageTemplate> sysSmsTemplates = sysMessageTemplateService.selectByCode(templateCode);
    if (sysSmsTemplates == null || sysSmsTemplates.size() == 0) {
      throw new JeecgBootException("Message template does not exist, template code: " + templateCode);
    }
    SysMessageTemplate sysSmsTemplate = sysSmsTemplates.get(0);
    // Template title
    title = title == null ? sysSmsTemplate.getTemplateName() : title;
    // Template content
    String content = sysSmsTemplate.getTemplateContent();
    if (tmplateParam != null) {
      for (Map.Entry<String, String> entry : tmplateParam.entrySet()) {
        String str = "${" + entry.getKey() + "}";
        if (entry.getValue() != null) {
          title = title.replace(str, entry.getValue());
          content = content.replace(str, entry.getValue());
        }
      }
    }
    // NOTE: cleaned - SysAnnouncement announcement = new SysAnnouncement();
    announcement.setTitile(title);
    announcement.setMsgContent(content);
    announcement.setSender(fromUser);
    announcement.setPriority(CommonConstant.PRIORITY_M);
    announcement.setMsgType(CommonConstant.MSG_TYPE_UESR);
    announcement.setSendStatus(CommonConstant.HAS_SEND);
    announcement.setSendTime(new Date());

    if (tmplateParam != null
        && oConvertUtils.isNotEmpty(tmplateParam.get(CommonSendStatus.MSG_ABSTRACT_JSON))) {
      announcement.setMsgAbstract(tmplateParam.get(CommonSendStatus.MSG_ABSTRACT_JSON));
    }
    String mobileOpenUrl = null;
    if (tmplateParam != null
        && oConvertUtils.isNotEmpty(tmplateParam.get(CommonConstant.MSG_HREF_URL))) {
      mobileOpenUrl = tmplateParam.get(CommonConstant.MSG_HREF_URL);
    }

    announcement.setMsgCategory(CommonConstant.MSG_CATEGORY_2);
    announcement.setDelFlag(String.valueOf(CommonConstant.DEL_FLAG_0));
    announcement.setBusId(busId);
    announcement.setBusType(busType);
    announcement.setOpenType(SysAnnmentTypeEnum.getByType(busType).getOpenType());
    announcement.setOpenPage(SysAnnmentTypeEnum.getByType(busType).getOpenPage());
    sysAnnouncementMapper.insert(announcement);
    // 2. Insert user announcement read-mark records
    String userId = toUser;
    String[] userIds = userId.split(",");
    String anntId = announcement.getId();
    for (int i = 0; i < userIds.length; i++) {
      if (oConvertUtils.isNotEmpty(userIds[i])) {
        SysUser sysUser = userMapper.getUserByName(userIds[i]);
        if (sysUser == null) {
          continue;
        }
        // NOTE: cleaned - SysAnnouncementSend announcementSend = new SysAnnouncementSend();
        announcementSend.setAnntId(anntId);
        announcementSend.setUserId(sysUser.getId());
        announcementSend.setReadFlag(CommonConstant.NO_READ_FLAG);
        sysAnnouncementSendMapper.insert(announcementSend);
        JSONObject obj = new JSONObject();
        obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_USER);
        obj.put(WebsocketConst.MSG_USER_ID, sysUser.getId());
        obj.put(WebsocketConst.MSG_ID, announcement.getId());
        obj.put(WebsocketConst.MSG_TXT, announcement.getTitile());
        webSocket.sendMessage(sysUser.getId(), obj.toJSONString());
      }
    }
    try {
      // DingTalk Message Notification
      // NOTE: cleaned - dingtalkService.sendActionCardMessage(announcement, mobileOpenUrl, true);
      // Enterprise WeChat Notifications
      // NOTE: cleaned - wechatEnterpriseService.sendTextCardMessage(announcement, true);
    } catch (Exception e) {
      log.error("Failed to sync and send third-party APP message!", e);
    }
  }

  @Override
  public String parseTemplateByCode(TemplateDTO templateDTO) {
    String templateCode = templateDTO.getTemplateCode();
    Map<String, String> map = templateDTO.getTemplateParam();
    List<SysMessageTemplate> sysSmsTemplates = sysMessageTemplateService.selectByCode(templateCode);
    if (sysSmsTemplates == null || sysSmsTemplates.size() == 0) {
      throw new JeecgBootException("Message template does not exist, template code: " + templateCode);
    }
    SysMessageTemplate sysSmsTemplate = sysSmsTemplates.get(0);
    // Template content
    String content = sysSmsTemplate.getTemplateContent();
    if (map != null) {
      for (Map.Entry<String, String> entry : map.entrySet()) {
        String str = "${" + entry.getKey() + "}";
        content = content.replace(str, entry.getValue());
      }
    }
    return content;
  }

  @Override
  public void updateSysAnnounReadFlag(String busType, String busId) {
    // NOTE: cleaned - SysAnnouncement announcement =
        sysAnnouncementMapper.selectOne(
            // NOTE: cleaned - new QueryWrapper<SysAnnouncement>().eq("bus_type", busType).eq("bus_id", busId));
    if (announcement != null) {
      LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
      String userId = sysUser.getId();
      // NOTE: cleaned - LambdaUpdateWrapper<SysAnnouncementSend> updateWrapper = new UpdateWrapper().lambda();
      // NOTE: cleaned - updateWrapper.set(SysAnnouncementSend::getReadFlag, CommonConstant.HAS_READ_FLAG);
      // NOTE: cleaned - updateWrapper.set(SysAnnouncementSend::getReadTime, new Date());
      // NOTE: cleaned - updateWrapper.eq(SysAnnouncementSend::getAnntId, announcement.getId());
      // NOTE: cleaned - updateWrapper.eq(SysAnnouncementSend::getUserId, userId);
      // updateWrapper.last("where annt_id ='"+announcement.getId()+"' and user_id ='"+userId+"'");
      // NOTE: cleaned - SysAnnouncementSend announcementSend = new SysAnnouncementSend();
      sysAnnouncementSendMapper.update(announcementSend, updateWrapper);
    }
  }

  /**
   * Get database type
   *
   * @param dataSource
   * @return
   * @throws SQLException
   */
  private String getDatabaseTypeByDataSource(DataSource dataSource) throws SQLException {
    if ("".equals(DB_TYPE)) {
      Connection connection = dataSource.getConnection();
      try {
        DatabaseMetaData md = connection.getMetaData();
        String dbType = md.getDatabaseProductName().toLowerCase();
        if (dbType.indexOf(DataBaseConstant.DB_TYPE_MYSQL.toLowerCase()) >= 0) {
          DB_TYPE = DataBaseConstant.DB_TYPE_MYSQL;
        } else if (dbType.indexOf(DataBaseConstant.DB_TYPE_ORACLE.toLowerCase()) >= 0) {
          DB_TYPE = DataBaseConstant.DB_TYPE_ORACLE;
        } else if (dbType.indexOf(DataBaseConstant.DB_TYPE_SQLSERVER.toLowerCase()) >= 0
            || dbType.indexOf(DataBaseConstant.DB_TYPE_SQL_SERVER_BLANK) >= 0) {
          DB_TYPE = DataBaseConstant.DB_TYPE_SQLSERVER;
        } else if (dbType.indexOf(DataBaseConstant.DB_TYPE_POSTGRESQL.toLowerCase()) >= 0) {
          DB_TYPE = DataBaseConstant.DB_TYPE_POSTGRESQL;
        } else if (dbType.indexOf(DataBaseConstant.DB_TYPE_MARIADB.toLowerCase()) >= 0) {
          DB_TYPE = DataBaseConstant.DB_TYPE_MARIADB;
        } else {
          log.error("DatabaseType:[" + dbType + "] not recognized!");
          // throw new JeecgBootException("Unrecognized database type:["+dbType+"]!");
        }
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      } finally {
        connection.close();
      }
    }
    return DB_TYPE;
  }

  @Override
  public List<DictModel> queryAllDict() {
    // Query and sort
    QueryWrapper<SysDict> queryWrapper = new QueryWrapper<SysDict>();
    queryWrapper.orderByAsc("create_time");
    List<SysDict> dicts = sysDictService.list(queryWrapper);
    // Packaged as a model
    List<DictModel> list = new ArrayList<DictModel>();
    for (SysDict dict : dicts) {
      list.add(new DictModel(dict.getDictCode(), dict.getDictName()));
    }

    return list;
  }

  @Override
  public List<SysCategoryModel> queryAllSysCategory() {
    List<SysCategory> ls = categoryMapper.selectList(null);
    List<SysCategoryModel> res = oConvertUtils.entityListToModelList(ls, SysCategoryModel.class);
    return res;
  }

  @Override
  public List<DictModel> queryFilterTableDictInfo(String table, String text, String code) {
    return sysDictService.queryTableDictItemsByCodeAndFilter(table, text, code);
  }

  @Override
  public List<String> queryTableDictByKeys(
      String table, String text, String code, String[] keyArray) {
    return sysDictService.queryTableDictByKeys(table, text, code, Joiner.on(",").join(keyArray));
  }

  @Override
  public List<ComboModel> queryAllUserBackCombo() {
    List<ComboModel> list = new ArrayList<ComboModel>();
    // P2 (CodePlan §4.3.2): whitelisted columns only (id/username/realname are what combo needs).
    List<SysUser> userList =
        userMapper.selectList(SysUserSafeQuery.wrapper().eq("status", 1).eq("del_flag", 0));
    for (SysUser user : userList) {
      ComboModel model = new ComboModel();
      model.setTitle(user.getRealname());
      model.setId(user.getId());
      model.setUsername(user.getUsername());
      list.add(model);
    }
    return list;
  }

  @Override
  public JSONObject queryAllUser(String userIds, Integer pageNo, Integer pageSize) {
    JSONObject json = new JSONObject();
    // P2 (CodePlan §4.3.2): whitelisted columns only.
    QueryWrapper<SysUser> queryWrapper =
        SysUserSafeQuery.wrapper().eq("status", 1).eq("del_flag", 0);
    List<ComboModel> list = new ArrayList<ComboModel>();
    Page<SysUser> page = new Page<SysUser>(pageNo, pageSize);
    IPage<SysUser> pageList = userMapper.selectPage(page, queryWrapper);
    for (SysUser user : pageList.getRecords()) {
      ComboModel model = new ComboModel();
      model.setUsername(user.getUsername());
      model.setTitle(user.getRealname());
      model.setId(user.getId());
      model.setEmail(user.getEmail());
      if (oConvertUtils.isNotEmpty(userIds)) {
        String[] temp = userIds.split(",");
        for (int i = 0; i < temp.length; i++) {
          if (temp[i].equals(user.getId())) {
            model.setChecked(true);
          }
        }
      }
      list.add(model);
    }
    json.put("list", list);
    json.put("total", pageList.getTotal());
    return json;
  }

  @Override
  public List<ComboModel> queryAllRole() {
    List<ComboModel> list = new ArrayList<ComboModel>();
    List<SysRole> roleList = roleMapper.selectList(new QueryWrapper<SysRole>());
    for (SysRole role : roleList) {
      ComboModel model = new ComboModel();
      model.setTitle(role.getRoleName());
      model.setId(role.getId());
      list.add(model);
    }
    return list;
  }

  @Override
  public List<ComboModel> queryAllRole(String[] roleIds) {
    List<ComboModel> list = new ArrayList<ComboModel>();
    List<SysRole> roleList = roleMapper.selectList(new QueryWrapper<SysRole>());
    for (SysRole role : roleList) {
      ComboModel model = new ComboModel();
      model.setTitle(role.getRoleName());
      model.setId(role.getId());
      model.setRoleCode(role.getRoleCode());
      if (oConvertUtils.isNotEmpty(roleIds)) {
        for (int i = 0; i < roleIds.length; i++) {
          if (roleIds[i].equals(role.getId())) {
            model.setChecked(true);
          }
        }
      }
      list.add(model);
    }
    return list;
  }

  @Override
  public List<String> getRoleIdsByUsername(String username) {
    return sysUserRoleMapper.getRoleIdByUserName(username);
  }

  @Override
  public List<String> getRoleIdsByUserId(String userId) {
    return sysUserRoleMapper.getRoleIdByUserId(userId);
  }

  @Override
  public String getDepartIdsByOrgCode(String orgCode) {
    return departMapper.queryDepartIdByOrgCode(orgCode);
  }

  @Override
  public List<SysDepartModel> getAllSysDepart() {
    List<SysDepartModel> departModelList = new ArrayList<SysDepartModel>();
    List<SysDepart> departList =
        departMapper.selectList(new QueryWrapper<SysDepart>().eq("del_flag", "0"));
    for (SysDepart depart : departList) {
      SysDepartModel model = new SysDepartModel();
      BeanUtils.copyProperties(depart, model);
      departModelList.add(model);
    }
    return departModelList;
  }

  @Override
  public DynamicDataSourceModel getDynamicDbSourceById(String dbSourceId) {
    if (dbSource != null && StringUtils.isNotBlank(dbSource.getDbPassword())) {
      String dbPassword = dbSource.getDbPassword();
      String decodedStr = SecurityUtil.jiemi(dbPassword);
      dbSource.setDbPassword(decodedStr);
    }
    return new DynamicDataSourceModel(dbSource);
  }

  @Override
  public DynamicDataSourceModel getDynamicDbSourceByCode(String dbSourceCode) {
    if (dbSource != null && StringUtils.isNotBlank(dbSource.getDbPassword())) {
      String dbPassword = dbSource.getDbPassword();
      String decodedStr = SecurityUtil.jiemi(dbPassword);
      dbSource.setDbPassword(decodedStr);
    }
    return new DynamicDataSourceModel(dbSource);
  }

  @Override
  public List<String> getDeptHeadByDepId(String deptId) {
    // P2 (CodePlan §4.3.2): whitelisted columns only (usernames are all that is returned).
    QueryWrapper<SysUser> queryWrapper =
        SysUserSafeQuery.wrapper().eq("status", 1).eq("del_flag", 0);
    // Supports comma-separated passing of multiple department IDs
    if (oConvertUtils.isNotEmpty(deptId) && deptId.contains(SymbolConstant.COMMA)) {
      String[] vals = deptId.split(SymbolConstant.COMMA);
      queryWrapper.and(
          andWrapper -> {
            for (int i = 0; i < vals.length; i++) {
              andWrapper.like("depart_ids", vals[i]);
              andWrapper.or();
            }
          });
    } else {
      queryWrapper.like("depart_ids", deptId);
    }

    List<SysUser> userList = userMapper.selectList(queryWrapper);
    List<String> list = new ArrayList<>();
    for (SysUser user : userList) {
      list.add(user.getUsername());
    }
    return list;
  }

  @Override
  public void sendWebSocketMsg(String[] userIds, String cmd) {
    JSONObject obj = new JSONObject();
    obj.put(WebsocketConst.MSG_CMD, cmd);
    webSocket.sendMessage(userIds, obj.toJSONString());
  }

  @Override
  public List<UserAccountInfo> queryAllUserByIds(String[] userIds) {
    // P2 (CodePlan §4.3.2): whitelisted columns only; UserAccountInfo copies id/username/
    // realname/email/avatar/activitiSync/phone, all of which are whitelisted.
    QueryWrapper<SysUser> queryWrapper =
        SysUserSafeQuery.wrapper().eq("status", 1).eq("del_flag", 0);
    queryWrapper.in("id", userIds);
    List<UserAccountInfo> loginUsers = new ArrayList<>();
    List<SysUser> sysUsers = userMapper.selectList(queryWrapper);
    for (SysUser user : sysUsers) {
      UserAccountInfo loginUser = new UserAccountInfo();
      BeanUtils.copyProperties(user, loginUser);
      loginUsers.add(loginUser);
    }
    return loginUsers;
  }

  /**
   * Push Sign-In Person Information
   *
   * @param userId
   */
  @Override
  public void meetingSignWebsocket(String userId) {
    JSONObject obj = new JSONObject();
    obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_SIGN);
    obj.put(WebsocketConst.MSG_USER_ID, userId);
    // Todo is currently pushing all, modify later
    webSocket.sendMessage(obj.toJSONString());
  }

  @Override
  public List<UserAccountInfo> queryUserByNames(String[] userNames) {
    // P2 (CodePlan §4.3.2): whitelisted columns only.
    QueryWrapper<SysUser> queryWrapper =
        SysUserSafeQuery.wrapper().eq("status", 1).eq("del_flag", 0);
    queryWrapper.in("username", userNames);
    List<UserAccountInfo> loginUsers = new ArrayList<>();
    List<SysUser> sysUsers = userMapper.selectList(queryWrapper);
    for (SysUser user : sysUsers) {
      UserAccountInfo loginUser = new UserAccountInfo();
      BeanUtils.copyProperties(user, loginUser);
      loginUsers.add(loginUser);
    }
    return loginUsers;
  }

  @Override
  public List<JSONObject> queryUserBySuperQuery(String superQuery, String matchType) {
    List<JSONObject> result = new ArrayList<>();
    Map<String, String[]> parameterMap = new HashMap<>();
    parameterMap.put("superQueryMatchType", new String[] {matchType});
    parameterMap.put("superQueryParams", new String[] {superQuery});
    SysUser sysUser = new SysUser();
    QueryWrapper<SysUser> queryWrapper = QueryGenerator.initQueryWrapper(sysUser, parameterMap);
    // P2 (CodePlan §4.3.2): users are JSON-serialized into the response — whitelist columns only.
    queryWrapper.select(SysUserSafeQuery.COLUMNS);
    List<SysUser> list = sysUserService.list(queryWrapper);
    if (ObjectUtils.isNotEmpty(list)) {
      list.forEach(SysUser::clearSensitiveForResponse);

      // update-begin-author: taoyan date: 2023-5-19 for: QQYUN-5326 [Brief Stream] Get organization staff single/multiple filters No department filters
      String departKey = "depart";
      QueryCondition departCondition = null;
      try {
        String temp = URLDecoder.decode(superQuery, "UTF-8");
        List<QueryCondition> conditions = JSON.parseArray(temp, QueryCondition.class);
        for (QueryCondition condition : conditions) {
          if (departKey.equals(condition.getField())) {
            departCondition = condition;
            break;
          }
        }
      } catch (UnsupportedEncodingException e) {
        log.error("Query user info, query condition JSON conversion failed", e);
      }

      for (SysUser user : list) {
        JSONObject userJson = JSONObject.parseObject(JSONObject.toJSONString(user));
        List<SysDepart> departList = sysDepartService.queryDepartsByUsername(user.getUsername());
        List<String> departIds = null;
        if (departList != null && departList.size() > 0) {
          departIds = departList.stream().map(i -> i.getId()).collect(Collectors.toList());
          List<String> departNames =
              departList.stream().map(i -> i.getDepartName()).collect(Collectors.toList());
          userJson.put(departKey, oConvertUtils.list2JSONArray(departIds));
          userJson.put(departKey + "_dictText", String.join(",", departNames));
        }
        boolean flag = getDepartConditionResult(departCondition, departIds);
        if (flag) {
          result.add(userJson);
        }
        // update-end-author: taoyan date: 2023-5-19 for: QQYUN-5326 [Brief Stream] Get organization staff single/multiple filters No department filters

      }
    }
    return result;
  }

  /**
   * Determine if the user's department meets the criteria - Equal to/Not Equal to/In/Not In - Medium/Empty/Not Empty QQYUN-5326 [Brief Stream] Get organization staff single/multiple filters No department filters
   *
   * @param departCondition
   * @param departIds
   * @return
   */
  private boolean getDepartConditionResult(QueryCondition departCondition, List<String> departIds) {
    if (departCondition == null) {
      return true;
    }
    QueryRuleEnum rule = QueryRuleEnum.getByValue(departCondition.getRule());
    String conditionVal = departCondition.getVal();
    if (rule == QueryRuleEnum.EMPTY) {
      if (departIds == null || departIds.size() == 0) {
        return true;
      }
    } else if (rule == QueryRuleEnum.NOT_EMPTY) {
      if (departIds != null && departIds.size() > 0) {
        return true;
      }
    } else {
      if (oConvertUtils.isEmpty(conditionVal)) {
        return false;
      }
      if (departIds == null || departIds.size() == 0) {
        return false;
      }
      List<String> conditionList;
      if (conditionVal.startsWith("[") && conditionVal.endsWith("]")) {
        conditionList = JSONArray.parseArray(conditionVal, String.class);
      } else {
        conditionList = new ArrayList<String>(Arrays.asList(conditionVal.split(",")));
      }
      if (rule == QueryRuleEnum.EQ) {
        if (oConvertUtils.isEqList(conditionList, departIds)) {
          return true;
        }
      } else if (rule == QueryRuleEnum.NE) {
        if (!oConvertUtils.isEqList(conditionList, departIds)) {
          return true;
        }
      } else if (rule == QueryRuleEnum.IN) {
        if (oConvertUtils.isInList(departIds, conditionList)) {
          return true;
        }
      } else if (rule == QueryRuleEnum.NOT_IN) {
        if (!oConvertUtils.isInList(departIds, conditionList)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public JSONObject queryUserById(String id) {
    // P2 (CodePlan §4.3.2): whitelisted columns only — the full user is JSON-serialized to callers.
    QueryWrapper<SysUser> queryWrapper = SysUserSafeQuery.wrapper();
    queryWrapper.lambda().eq(true, SysUser::getId, id);
    SysUser sysUser = sysUserService.getOne(queryWrapper);
    if (ObjectUtils.isNotEmpty(sysUser)) {
      sysUser.clearSensitiveForResponse();
      return JSONObject.parseObject(JSONObject.toJSONString(sysUser));
    }
    return null;
  }

  @Override
  public List<JSONObject> queryDeptBySuperQuery(String superQuery, String matchType) {
    List<JSONObject> result = new ArrayList<>();
    Map<String, String[]> parameterMap = new HashMap<>();
    parameterMap.put("superQueryMatchType", new String[] {matchType});
    parameterMap.put("superQueryParams", new String[] {superQuery});
    SysDepart sysDepart = new SysDepart();
    QueryWrapper<SysDepart> queryWrapper = QueryGenerator.initQueryWrapper(sysDepart, parameterMap);
    List<SysDepart> list = sysDepartService.list(queryWrapper);
    if (ObjectUtils.isNotEmpty(list)) {
      for (SysDepart depart : list) {
        result.add(JSONObject.parseObject(JSONObject.toJSONString(depart)));
      }
    }
    return result;
  }

  @Override
  public List<JSONObject> queryRoleBySuperQuery(String superQuery, String matchType) {
    List<JSONObject> result = new ArrayList<>();
    Map<String, String[]> parameterMap = new HashMap<>();
    parameterMap.put("superQueryMatchType", new String[] {matchType});
    parameterMap.put("superQueryParams", new String[] {superQuery});
    SysRole sysDepart = new SysRole();
    QueryWrapper<SysRole> queryWrapper = QueryGenerator.initQueryWrapper(sysDepart, parameterMap);
    List<SysRole> list = sysRoleService.list(queryWrapper);
    if (ObjectUtils.isNotEmpty(list)) {
      for (SysRole role : list) {
        result.add(JSONObject.parseObject(JSONObject.toJSONString(role)));
      }
    }
    return result;
  }

  @Override
  public List<String> selectUserIdByTenantId(String tenantId) {
    QueryWrapper queryWrapper = new QueryWrapper<SysUserTenant>();
    queryWrapper.select("user_id");
    queryWrapper.eq("tenant_id", tenantId);
    return sysUserTenantService.listObjs(queryWrapper, e -> e.toString());
  }

  @Override
  public SysDepartModel selectAllById(String id) {
    SysDepart sysDepart = sysDepartService.getById(id);
    SysDepartModel sysDepartModel = new SysDepartModel();
    BeanUtils.copyProperties(sysDepart, sysDepartModel);
    return sysDepartModel;
  }

  @Override
  public List<String> queryDeptUsersByUserId(String userId) {
    List<String> userIds = new ArrayList<>();
    List<SysUserDepart> userDepartList =
        sysUserDepartService.list(new QueryWrapper<SysUserDepart>().eq("user_id", userId));
    if (userDepartList != null) {
      // Find your company
      String orgCodes = "";
      StringBuilder orgCodesBuilder = new StringBuilder();
      orgCodesBuilder.append(orgCodes);
      for (SysUserDepart userDepart : userDepartList) {
        // Query belonging company code
        SysDepart depart = sysDepartService.getById(userDepart.getDepId());
        int length = YouBianCodeUtil.ZHANWEI_LENGTH;
        String compyOrgCode = "";
        if (depart != null && depart.getOrgCode() != null) {
          compyOrgCode = depart.getOrgCode().substring(0, length);
          if (orgCodes.indexOf(compyOrgCode) == -1) {
            orgCodesBuilder.append(SymbolConstant.COMMA).append(compyOrgCode);
          }
        }
      }
      orgCodes = orgCodesBuilder.toString();
      if (oConvertUtils.isNotEmpty(orgCodes)) {
        orgCodes = orgCodes.substring(1);
        List<String> listIds = departMapper.getSubDepIdsByOrgCodes(orgCodes.split(","));
        List<SysUserDepart> userList =
            sysUserDepartService.list(new QueryWrapper<SysUserDepart>().in("dep_id", listIds));
        for (SysUserDepart userDepart : userList) {
          if (!userIds.contains(userDepart.getUserId())) {
            userIds.add(userDepart.getUserId());
          }
        }
      }
    }
    return userIds;
  }

  /**
   * Query the set of roles owned by the user
   *
   * @param username
   * @return
   */
  @Override
  public Set<String> getUserRoleSet(String username) {
    // Query the set of roles owned by the user
    List<String> roles = sysUserRoleMapper.getRoleByUserName(username);
    log.info(
        "-------Fetch user role Rules from database------username: "
            + username
            + ",Roles size: "
            + (roles == null ? 0 : roles.size()));
    return new HashSet<>(roles);
  }

  /**
   * Query the set of roles owned by the user
   *
   * @param useId
   * @return
   */
  @Override
  public Set<String> getUserRoleSetById(String useId) {
    // Query the set of roles owned by the user
    List<String> roles = sysUserRoleMapper.getRoleCodeByUserId(useId);
    log.info(
        "-------Fetch user role Rules from database------useId: "
            + useId
            + ",Roles size: "
            + (roles == null ? 0 : roles.size()));
    return new HashSet<>(roles);
  }

  /**
   * Query permissions owned by user
   *
   * @param userId
   * @return
   */
  @Override
  public Set<String> getUserPermissionSet(String userId) {
    Set<String> permissionSet = new HashSet<>();
    List<SysPermission> permissionList = sysPermissionMapper.queryByUser(userId);
    // ================= begin When enabling tenant, if no test role exists, add test role by default ================
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      if (permissionList == null) {
        permissionList = new ArrayList<>();
      }
      List<SysPermission> testRoleList = sysPermissionMapper.queryPermissionByTestRoleId();
      permissionList.addAll(testRoleList);
    }
    // ================= end When enabling tenant, if no test role exists, add test role by default ================
    for (SysPermission po : permissionList) {
      //			TODO URL rule issue?
      //			if (oConvertUtils.isNotEmpty(po.getUrl())) {
      //				permissionSet.add(po.getUrl());
      //			}
      if (oConvertUtils.isNotEmpty(po.getPerms())) {
        permissionSet.add(po.getPerms());
      }
    }
    log.info(
        "-------Fetch user permissions Perms from database------userId: "
            + userId
            + ",Perms size: "
            + (permissionSet == null ? 0 : permissionSet.size()));
    return permissionSet;
  }

  /**
   * Check whether online menu is permitted
   *
   * @param onlineAuthDTO
   * @return
   */
  @Override
  public boolean hasOnlineAuth(OnlineAuthDTO onlineAuthDTO) {
    String username = onlineAuthDTO.getUsername();
    List<String> possibleUrl = onlineAuthDTO.getPossibleUrl();
    String onlineFormUrl = onlineAuthDTO.getOnlineFormUrl();
    // QueryMenu
    LambdaQueryWrapper<SysPermission> query = new LambdaQueryWrapper<SysPermission>();
    query.eq(SysPermission::getDelFlag, 0);
    query.in(SysPermission::getUrl, possibleUrl);
    List<SysPermission> permissionList = sysPermissionMapper.selectList(query);
    if (permissionList == null || permissionList.size() == 0) {
      // No menu configured; look up online form menu URL
      SysPermission sysPermission = new SysPermission();
      sysPermission.setUrl(onlineFormUrl);
      int count = sysPermissionMapper.queryCountByUsername(username, sysPermission);
      if (count <= 0) {
        // update-begin---author:chenrui ---date:20240123
        // for: [QQYUN-7992][online] The online form under the work order application is not configured with the online form development menu, and the operation error is reported without permission------------
        sysPermission.setUrl(onlineAuthDTO.getOnlineWorkOrderUrl());
        count = sysPermissionMapper.queryCountByUsername(username, sysPermission);
        if (count <= 0) {
          return false;
        }
        // update-end---author:chenrui ---date:20240123
        // for: [QQYUN-7992][online] The online form under the work order application is not configured with the online form development menu, and the operation error is reported without permission------------
      }
    } else {
      // Found the menu
      boolean has = false;
      for (SysPermission p : permissionList) {
        int count = sysPermissionMapper.queryCountByUsername(username, p);
        has = has || (count > 0);
      }
      return has;
    }
    return true;
  }

  /**
   * Query the role collection owned by the user and the interface implementation in the common api
   *
   * @param username
   * @return
   */
  @Override
  public Set<String> queryUserRoles(String username) {
    return getUserRoleSet(username);
  }

  @Override
  public Set<String> queryUserRolesById(String userId) {
    return getUserRoleSetById(userId);
  }

  /**
   * Query the permission set owned by the user and the interface implementation in the common api
   *
   * @param userId
   * @return
   */
  @Override
  public Set<String> queryUserAuths(String userId) {
    return getUserPermissionSet(userId);
  }

  /**
   * 36 Query and return multiple users by multiple user accounts (comma-separated)
   *
   * @param usernames
   * @return
   */
  @Override
  public List<JSONObject> queryUsersByUsernames(String usernames) {
    // P2 (CodePlan §4.3.3): whitelisted columns at SQL source; clearSensitiveForResponse below is
    // kept as defense-in-depth.
    QueryWrapper<SysUser> queryWrapper =
        SysUserSafeQuery.wrapper().in("username", Arrays.asList(usernames.split(",")));
    List<SysUser> users = userMapper.selectList(queryWrapper);
    // P2 (CodePlan §4.3.3): strip sensitive columns before JSON serialization so
    // password/salt/reason/walletAddress/financialState/kycAttachment*/lastPasswordChangeTime/
    // isSender never reach the JSON response body.
    users.forEach(SysUser::clearSensitiveForResponse);
    return JSON.parseArray(JSON.toJSONString(users)).toJavaList(JSONObject.class);
  }

  @Override
  public List<JSONObject> queryUsersByIds(String ids) {
    // P2 (CodePlan §4.3.3): whitelisted columns at SQL source.
    QueryWrapper<SysUser> queryWrapper =
        SysUserSafeQuery.wrapper().in("id", Arrays.asList(ids.split(",")));
    List<SysUser> users = userMapper.selectList(queryWrapper);
    // P2 (CodePlan §4.3.3): strip sensitive columns before JSON serialization.
    users.forEach(SysUser::clearSensitiveForResponse);
    return JSON.parseArray(JSON.toJSONString(users)).toJavaList(JSONObject.class);
  }

  /**
   * 37 According to multiple department codes (comma separated), the query returns multiple department information
   *
   * @param orgCodes
   * @return
   */
  @Override
  public List<JSONObject> queryDepartsByOrgcodes(String orgCodes) {
    LambdaQueryWrapper<SysDepart> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.in(SysDepart::getOrgCode, orgCodes.split(","));
    return JSON.parseArray(JSON.toJSONString(sysDepartService.list(queryWrapper)))
        .toJavaList(JSONObject.class);
  }

  @Override
  public List<JSONObject> queryDepartsByIds(String ids) {
    LambdaQueryWrapper<SysDepart> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.in(SysDepart::getId, ids.split(","));
    return JSON.parseArray(JSON.toJSONString(sysDepartService.list(queryWrapper)))
        .toJavaList(JSONObject.class);
  }

  /**
   * Send message
   *
   * @param fromUser
   * @param toUser
   * @param title
   * @param msgContent
   * @param setMsgCategory
   */
  // NOTE: cleaned - private void sendSysAnnouncement(
      String fromUser, String toUser, String title, String msgContent, String setMsgCategory) {
    // NOTE: cleaned - SysAnnouncement announcement = new SysAnnouncement();
    announcement.setTitile(title);
    announcement.setMsgContent(msgContent);
    announcement.setSender(fromUser);
    announcement.setPriority(CommonConstant.PRIORITY_M);
    announcement.setMsgType(CommonConstant.MSG_TYPE_UESR);
    announcement.setSendStatus(CommonConstant.HAS_SEND);
    announcement.setSendTime(new Date());
    announcement.setMsgCategory(setMsgCategory);
    announcement.setDelFlag(String.valueOf(CommonConstant.DEL_FLAG_0));
    sysAnnouncementMapper.insert(announcement);
    // 2. Insert user announcement read-mark records
    String userId = toUser;
    String[] userIds = userId.split(",");
    String anntId = announcement.getId();
    for (int i = 0; i < userIds.length; i++) {
      if (oConvertUtils.isNotEmpty(userIds[i])) {
        SysUser sysUser = userMapper.getUserByName(userIds[i]);
        if (sysUser == null) {
          continue;
        }
        // NOTE: cleaned - SysAnnouncementSend announcementSend = new SysAnnouncementSend();
        announcementSend.setAnntId(anntId);
        announcementSend.setUserId(sysUser.getId());
        announcementSend.setReadFlag(CommonConstant.NO_READ_FLAG);
        sysAnnouncementSendMapper.insert(announcementSend);
        JSONObject obj = new JSONObject();
        obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_USER);
        obj.put(WebsocketConst.MSG_USER_ID, sysUser.getId());
        obj.put(WebsocketConst.MSG_ID, announcement.getId());
        obj.put(WebsocketConst.MSG_TXT, announcement.getTitile());
        webSocket.sendMessage(sysUser.getId(), obj.toJSONString());
      }
    }
  }

  /**
   * Send message with business params
   *
   * @param fromUser
   * @param toUser
   * @param title
   * @param msgContent
   * @param setMsgCategory
   * @param busType
   * @param busId
   */
  private void sendBusAnnouncement(
      String fromUser,
      String toUser,
      String title,
      String msgContent,
      String setMsgCategory,
      String busType,
      String busId) {
    // NOTE: cleaned - SysAnnouncement announcement = new SysAnnouncement();
    announcement.setTitile(title);
    announcement.setMsgContent(msgContent);
    announcement.setSender(fromUser);
    announcement.setPriority(CommonConstant.PRIORITY_M);
    announcement.setMsgType(CommonConstant.MSG_TYPE_UESR);
    announcement.setSendStatus(CommonConstant.HAS_SEND);
    announcement.setSendTime(new Date());
    announcement.setMsgCategory(setMsgCategory);
    announcement.setDelFlag(String.valueOf(CommonConstant.DEL_FLAG_0));
    announcement.setBusId(busId);
    announcement.setBusType(busType);
    announcement.setOpenType(SysAnnmentTypeEnum.getByType(busType).getOpenType());
    announcement.setOpenPage(SysAnnmentTypeEnum.getByType(busType).getOpenPage());
    sysAnnouncementMapper.insert(announcement);
    // 2. Insert user announcement read-mark records
    String userId = toUser;
    String[] userIds = userId.split(",");
    String anntId = announcement.getId();
    for (int i = 0; i < userIds.length; i++) {
      if (oConvertUtils.isNotEmpty(userIds[i])) {
        SysUser sysUser = userMapper.getUserByName(userIds[i]);
        if (sysUser == null) {
          continue;
        }
        // NOTE: cleaned - SysAnnouncementSend announcementSend = new SysAnnouncementSend();
        announcementSend.setAnntId(anntId);
        announcementSend.setUserId(sysUser.getId());
        announcementSend.setReadFlag(CommonConstant.NO_READ_FLAG);
        sysAnnouncementSendMapper.insert(announcementSend);
        JSONObject obj = new JSONObject();
        obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_USER);
        obj.put(WebsocketConst.MSG_USER_ID, sysUser.getId());
        obj.put(WebsocketConst.MSG_ID, announcement.getId());
        obj.put(WebsocketConst.MSG_TXT, announcement.getTitile());
        webSocket.sendMessage(sysUser.getId(), obj.toJSONString());
      }
    }
  }

  /**
   * Send email message
   *
   * @param email
   * @param title
   * @param content
   */
  @Override
  public void sendEmailMsg(String email, String title, String content) {
    EmailSendMsgHandle emailHandle = new EmailSendMsgHandle();
    emailHandle.sendMsg(email, title, content);
  }

  /**
   * Send HTML template email message
   *
   * @param email
   * @param title
   * @param emailTemplateEnum
   * @param params
   */
  @Override
  public void sendHtmlTemplateEmail(
      String email, String title, EmailTemplateEnum emailTemplateEnum, JSONObject params) {
    EmailSendMsgHandle emailHandle = new EmailSendMsgHandle();
    String htmlText = "";
    try {
      Configuration configuration = sharedFreemarkerConfiguration;
      String url = emailTemplateEnum.getUrl();
      configuration.setClassForTemplateLoading(
          this.getClass(), url.substring(0, url.lastIndexOf("/")));
      Template template = configuration.getTemplate(url.substring(url.lastIndexOf("/")));
      params.put("logo", LogoLoader.getLogo());
      String brand = jeecgBaseConfig.getBrandName();
      params.put("brandName", brand != null && !brand.isBlank() ? brand : "Mighty Ramp");
      String domain = jeecgBaseConfig.getPublicDomain();
      params.put("publicDomain", domain != null ? domain : "");
      String supportEmail = jeecgBaseConfig.getContactSupportEmail();
      params.put("supportEmail", supportEmail != null ? supportEmail : "");
      htmlText = FreeMarkerTemplateUtils.processTemplateIntoString(template, params);
    } catch (IOException e) {
      e.printStackTrace();
    } catch (TemplateException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }

    log.info("Email Html Text：{}", htmlText);
    emailHandle.sendMsg(email, title, htmlText);
  }

  /**
   * Obtain the company's subordinate departments and all user ID information
   *
   * @param orgCode
   * @return
   */
  @Override
  public List<Map> getDeptUserByOrgCode(String orgCode) {
    // 1. Obtain company information
    SysDepart comp = sysDepartService.queryCompByOrgCode(orgCode);
    if (comp != null) {
      // 2. Obtain subordinate departments of the company
      List<SysDepart> departs = sysDepartService.queryDeptByPid(comp.getId());
      // 3. Obtain personnel information under the department
      List<Map> list = new ArrayList();
      // 4.handle department and descendant users
      for (SysDepart dept : departs) {
        Map map = new HashMap(5);
        // Department name
        String departName = dept.getDepartName();
        // Get child department ids by org code
        List<String> listIds = departMapper.getSubDepIdsByDepId(dept.getId());
        // Get all users under child department ids
        List<SysUserDepart> userList =
            sysUserDepartService.list(new QueryWrapper<SysUserDepart>().in("dep_id", listIds));
        List<String> userIds = new ArrayList<>();
        for (SysUserDepart userDepart : userList) {
          if (!userIds.contains(userDepart.getUserId())) {
            userIds.add(userDepart.getUserId());
          }
        }
        map.put("name", departName);
        map.put("ids", userIds);
        list.add(map);
      }
      return list;
    }
    return null;
  }

  /**
   * Query category dictionary translation
   *
   * @param ids Category dictionary table ids
   * @return
   */
  @Override
  public List<String> loadCategoryDictItem(String ids) {
    return sysCategoryService.loadDictItem(ids, false);
  }

  @Override
  public List<String> loadCategoryDictItemByNames(String names, boolean delNotExist) {
    return sysCategoryService.loadDictItemByNames(names, delNotExist);
  }

  /**
   * Load dictionary text by dictionary code
   *
   * @param dictCode order: tableName, text, code
   * @param keys keys to query
   * @return
   */
  @Override
  public List<String> loadDictItem(String dictCode, String keys) {
    String[] params = dictCode.split(",");
    return sysDictService.queryTableDictByKeys(params[0], params[1], params[2], keys, false);
  }

  @Override
  public Map<String, String> copyLowAppDict(String originalAppId, String appId, String tenantId) {
    Map<String, String> dictCodeMapping = new HashMap<String, String>();
    List<SysDictVo> ls = sysDictService.getDictListByLowAppId(originalAppId);
    for (SysDictVo vo : ls) {
      vo.setId(null);
      vo.setLowAppId(appId);
      vo.setTenantId(oConvertUtils.getInt(tenantId, null));
      String newDictCode = sysDictService.addDictByLowAppId(vo);
      dictCodeMapping.put(vo.getDictCode(), newDictCode);
    }

    log.info(
        " --- Batch copy application dictionaries to new tenant — source app ID:{}, new app ID:{}, new tenant ID:{}, dict count:{} --- ",
        originalAppId,
        appId,
        tenantId,
        dictCodeMapping.size());
    return dictCodeMapping;
  }

  /**
   * Query dictionary items by dictionary code
   *
   * @param dictCode order: tableName, text, code
   * @param dictCode key to query
   * @return
   */
  @Override
  public List<DictModel> getDictItems(String dictCode) {
    List<DictModel> ls = sysDictService.getDictItems(dictCode);
    if (ls == null) {
      ls = new ArrayList<>();
    }
    return ls;
  }

  /**
   * Query multiple dictionary items by multiple dictionary codes
   *
   * @param dictCodeList
   * @return key = dictCode; value = corresponding dictionary items
   */
  @Override
  public Map<String, List<DictModel>> getManyDictItems(List<String> dictCodeList) {
    return sysDictService.queryDictItemsByCodeList(dictCodeList);
  }

  /**
   * 【Dropdown search】 large dict tables load async; front-end filters by input
   *
   * @param dictCode dictionary code format: table,text,code
   * @param keyword filter keyword
   * @return
   */
  @Override
  public List<DictModel> loadDictItemByKeyword(String dictCode, String keyword, Integer pageSize) {
    return sysDictService.loadDict(dictCode, keyword, pageSize);
  }

  @Override
  public Map<String, List<DictModel>> translateManyDict(String dictCodes, String keys) {
    List<String> dictCodeList = Arrays.asList(dictCodes.split(","));
    List<String> values = Arrays.asList(keys.split(","));
    // update-begin---author:chenrui ---date:20231221
    // for：[issues/#5643]fix cross-DB table-dict query in distributed setup------------
    return sysDictService.queryManyDictByKeys(dictCodeList, values);
    // update-end---author:chenrui ---date:20231221  for: [issues/#5643] fix table-dict cross-DB query under distributed mode ------------
  }

  // update-begin---author:chenrui ---date:20231221  for: [issues/#5643] fix table-dict cross-DB query under distributed mode ------------
  @Override
  public List<DictModel> translateDictFromTableByKeys(
      String table, String text, String code, String keys, String dataSource) {
    return sysDictService.queryTableDictTextByKeys(
        table, text, code, Arrays.asList(keys.split(",")), dataSource);
  }

  // update-end---author:chenrui ---date:20231221  for: [issues/#5643] fix table-dict cross-DB query under distributed mode ------------

  // -------------------------------------workflow node send template message-----------------------------------------------
  @Override
  public void sendTemplateMessage(MessageDTO message) {
    String messageType = message.getType();
    log.debug(" [Universal] Push message messageType = {}", messageType);
    // update-begin-author:taoyan date:2022-7-9 for: move template parse into message send, not callers
    String templateCode = message.getTemplateCode();
    if (oConvertUtils.isNotEmpty(templateCode)) {
      SysMessageTemplate templateEntity = getTemplateEntity(templateCode);
      boolean isMarkdown =
          CommonConstant.MSG_TEMPLATE_TYPE_MD.equals(templateEntity.getTemplateType());
      String content = templateEntity.getTemplateContent();
      if (oConvertUtils.isNotEmpty(content) && null != message.getData()) {
        content =
            FreemarkerParseFactory.parseTemplateContent(content, message.getData(), isMarkdown);
      }
      message.setIsMarkdown(isMarkdown);
      message.setContent(content);
    }
    if (oConvertUtils.isEmpty(message.getContent())) {
      log.error("Failed to send message; message content is empty!");
      throw new JeecgBootException("Failed to send message; message content is empty!");
    }

    // update-end-author:taoyan date:2022-7-9 for: move template parse into message send, not callers
    if (MessageTypeEnum.XT.getType().equals(messageType)) {
      if (message.isMarkdown()) {
        // System messages need Markdown parsing
        message.setContent(HTMLUtils.parseMarkdown(message.getContent()));
      }
      systemSendMsgHandle.sendMessage(message);
    } else if (MessageTypeEnum.YJ.getType().equals(messageType)) {
      if (message.isMarkdown()) {
        // Email messages need Markdown parsing
        message.setContent(HTMLUtils.parseMarkdown(message.getContent()));
      }
      emailSendMsgHandle.sendMessage(message);
    } else if (MessageTypeEnum.DD.getType().equals(messageType)) {
      ddSendMsgHandle.sendMessage(message);
    } else if (MessageTypeEnum.QYWX.getType().equals(messageType)) {
      qywxSendMsgHandle.sendMessage(message);
    }
  }

  @Override
  public String getTemplateContent(String code) {
    List<SysMessageTemplate> list = sysMessageTemplateService.selectByCode(code);
    if (list == null || list.size() == 0) {
      return null;
    }
    return list.get(0).getTemplateContent();
  }

  /**
   * Get template content; parse markdown
   *
   * @param code
   * @return
   */
  public SysMessageTemplate getTemplateEntity(String code) {
    List<SysMessageTemplate> list = sysMessageTemplateService.selectByCode(code);
    if (list == null || list.size() == 0) {
      return null;
    }
    return list.get(0);
  }

  // -------------------------------------workflow node send template message-----------------------------------------------

  @Override
  public void saveDataLog(DataLogDTO dataLogDto) {
    try {
      SysDataLog entity = new SysDataLog();
      entity.setDataTable(dataLogDto.getTableName());
      entity.setDataId(dataLogDto.getDataId());
      entity.setDataContent(dataLogDto.getContent());
      entity.setType(dataLogDto.getType());
      entity.setDataVersion("1");
      if (oConvertUtils.isNotEmpty(dataLogDto.getCreateName())) {
        entity.setCreateBy(dataLogDto.getCreateName());
      } else {
        entity.autoSetCreateName();
      }
      sysDataLogService.save(entity);
    } catch (Exception e) {
      log.warn(e.getMessage(), e);
      // e.printStackTrace();
    }
  }

  /**
   * Persist an operation log into mpay_system.sys_log.
   *
   * <p>This is the system-side endpoint for the cross-service addLog contract. Other services
   * (dcs/processengine) invoke it via Feign so that sys_log is written only by the system service
   * — the operation-log table is sharded to system and must NOT be replicated into other
   * services' local schemas.
   *
   * <p>Implementation note: deliberately bypasses {@link org.jeecg.modules.base.service.BaseCommonService}
   * and writes through {@link BaseCommonMapper} directly. BaseCommonService.addLog in turn calls
   * {@code commonAPI.addLog}, which in the system process resolves to this very method — going
   * through BaseCommonService here would create a recursive loop. Local sys-user/role/permission
   * controllers that call {@code baseCommonService.addLog(...)} still work: their call resolves
   * to {@code commonAPI.addLog} -> this method -> mapper write, with no extra Feign hop.
   */
  @Override
  public void addLog(LogDTO logDTO) {
    if (oConvertUtils.isEmpty(logDTO.getId())) {
      logDTO.setId(String.valueOf(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId()));
    }
    try {
      if (logDTO.getCreateTime() == null) {
        logDTO.setCreateTime(new Date());
      }
      baseCommonMapper.saveLog(logDTO);
    } catch (Exception e) {
      // Mirror the original BaseCommonServiceImpl behavior — never let log persistence break the
      // surrounding business flow (callers run async flush with try/catch too, but be defensive).
      int len = logDTO.getLogContent() == null ? 0 : logDTO.getLogContent().length();
      log.warn("addLog persistence failed; LogContent length={}, err={}", len, e.getMessage());
    }
  }

  /**
   * Persist a denied-invocation audit record into mpay_system.sys_deny_audit.
   *
   * <p>This service is the shard owner of {@code sys_deny_audit}. Non-owner shards (dcs /
   * processengine) reach this method through the Feign endpoint
   * {@code POST /sys/api/recordDenyAudit}, so no local mirror of the table is required anywhere
   * else (SHARD-BAN T5-06/07, gate 1.14).
   */
  @Override
  public void recordDenyAudit(DenyAuditDTO denyAuditDTO) {
    if (denyAuditDTO == null) {
      return;
    }
    ISysDenyAuditService svc = sysDenyAuditService;
    if (svc == null) {
      log.warn("recordDenyAudit: ISysDenyAuditService unavailable; reasonCode={}",
          denyAuditDTO.getReasonCode());
      return;
    }
    try {
      SysDenyAudit entity = new SysDenyAudit();
      entity.setId(denyAuditDTO.getId());
      entity.setRequestIp(denyAuditDTO.getRequestIp());
      entity.setUserIdOpt(denyAuditDTO.getUserIdOpt());
      entity.setHttpMethod(denyAuditDTO.getHttpMethod());
      entity.setRequestUri(denyAuditDTO.getRequestUri());
      entity.setReasonCode(denyAuditDTO.getReasonCode());
      entity.setReasonDetailRedacted(denyAuditDTO.getReasonDetailRedacted());
      entity.setRequestBodyRedacted(denyAuditDTO.getRequestBodyRedacted());
      entity.setTraceId(denyAuditDTO.getTraceId());
      entity.setCreateTime(denyAuditDTO.getCreateTime() == null ? new Date() : denyAuditDTO.getCreateTime());
      entity.setTenantId(denyAuditDTO.getTenantId());
      svc.asyncRecord(entity);
    } catch (Exception e) {
      // Audit persistence must never break the caller's business flow.
      log.warn("recordDenyAudit persistence failed; reasonCode={}, err={}",
          denyAuditDTO.getReasonCode(), e.getMessage());
    }
  }

  /**
   * Owner-side probe used by the H6 startup gate: does {@code sys_deny_audit} exist in this
   * schema? Implemented with JDBC metadata so it reads no rows.
   */
  @Override
  public boolean isDenyAuditOwnerAvailable() {
    DataSource ds = shardProbeDataSource;
    if (ds == null) {
      return false;
    }
    try (Connection c = ds.getConnection()) {
      DatabaseMetaData md = c.getMetaData();
      for (String candidate : new String[] {"sys_deny_audit", "SYS_DENY_AUDIT"}) {
        try (java.sql.ResultSet rs = md.getTables(null, null, candidate, new String[] {"TABLE"})) {
          if (rs.next()) {
            return true;
          }
        }
      }
      return false;
    } catch (Exception e) {
      log.warn("isDenyAuditOwnerAvailable probe failed: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public void updateAvatar(LoginUser loginUser) {
    SysUser sysUser = new SysUser();
    // Create UpdateWrapper
    UpdateWrapper<SysUser> updateWrapper = new UpdateWrapper<>();
    updateWrapper.eq("id", loginUser.getId()); // Set update conditions
    sysUser.setAvatar(loginUser.getAvatar()); // Set fields to update
    sysUserService.update(sysUser, updateWrapper);
  }

  @Override
  public void sendAppChatSocket(String userId) {
    JSONObject obj = new JSONObject();
    obj.put(WebsocketConst.MSG_CMD, WebsocketConst.MSG_CHAT);
    obj.put(WebsocketConst.MSG_USER_ID, userId);
    webSocket.sendMessage(userId, obj.toJSONString());
  }

  @Override
  public String getRoleCodeById(String id) {
    SysRole role = roleMapper.selectById(id);
    if (role != null) {
      return role.getRoleCode();
    }
    return null;
  }

  @Override
  public List<DictModel> queryRoleDictByCode(String roleCodes) {
    if (oConvertUtils.isEmpty(roleCodes)) {
      return new ArrayList<>();
    }
    List<String> codeList = Arrays.asList(roleCodes.split(","));
    LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.in(SysRole::getRoleCode, codeList);
    List<SysRole> list = roleMapper.selectList(queryWrapper);
    // Convert to SysRoleVo
    return list.stream()
        .map(
            sysRole -> {
              DictModel model = new DictModel();
              model.setText(sysRole.getRoleName());
              model.setValue(sysRole.getRoleCode());
              return model;
            })
        .collect(Collectors.toList());
  }

  @Override
  public List<String> queryUserIdsByDeptIds(List<String> deptIds) {
    QueryWrapper<SysUserDepart> queryWrapper = new QueryWrapper<>();
    queryWrapper
        .lambda()
        .select(SysUserDepart::getUserId)
        .in(true, SysUserDepart::getDepId, deptIds);
    return sysUserDepartService.listObjs(queryWrapper, e -> e.toString());
  }

  @Override
  public List<String> queryUserAccountsByDeptIds(List<String> deptIds) {
    return departMapper.queryUserAccountByDepartIds(deptIds);
  }

  @Override
  public List<String> queryUserIdsByRoleds(List<String> roleCodes) {
    LambdaQueryWrapper<SysRole> query =
        new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleCode, roleCodes);
    List<SysRole> roleList = sysRoleService.list(query);
    if (roleList != null && roleList.size() > 0) {
      List<String> idList =
          roleList.stream().map(role -> role.getId()).collect(Collectors.toList());
      QueryWrapper<SysUserRole> queryWrapper = new QueryWrapper<>();
      queryWrapper.lambda().select(SysUserRole::getUserId).in(true, SysUserRole::getRoleId, idList);
      return sysUserRoleService.listObjs(queryWrapper, e -> e.toString());
    }
    return null;
  }

  @Override
  public List<String> queryUserIdsByPositionIds(List<String> positionIds) {
    QueryWrapper<SysUserPosition> queryWrapper = new QueryWrapper<>();
    queryWrapper
        .lambda()
        .select(SysUserPosition::getUserId)
        .in(true, SysUserPosition::getPositionId, positionIds);
    return sysUserPositionService.listObjs(queryWrapper, e -> e.toString());
  }

  // update-begin-author: taoyan date: 2023-2-21 for: Solving parameter order problems
  /**
   * Get report URLs with params; reorder params to cover permutations and query menus with IN
   * /online/cgreport/1624393012494286850?name=1&age=16
   * /online/cgreport/1624393012494286850?age=16&name=1
   *
   * @param path
   * @return
   */
  private List<String> getOnlinePossiblePaths(String path) {
    List<String> result = new ArrayList<>();
    log.info(" path = " + path);
    if (path.indexOf("?") >= 0
        && (path.contains("/online/cgreport/")
            || path.contains("/online/cgformList/")
            || path.contains("/online/graphreport/"))) {
      // Contains ? means multiple params
      String[] pathArray = path.split("\\?");
      if (oConvertUtils.isNotEmpty(pathArray[1])) {
        String[] params = pathArray[1].split("&");
        if (params.length == 1) {
          result.add(path);
        } else {
          result = anm(pathArray[0], Arrays.asList(params));
        }
      } else {
        result.add(path);
      }
    } else {
      result.add(path);
    }
    return result;
  }

  /**
   * Get permutations of array elements A(n,m)
   *
   * @param list
   * @return
   */
  private List<String> anm(String baseUrl, List<String> list) {
    int len = list.size();
    int[] destArray = new int[len];
    for (int i = 0; i < len; i++) {
      destArray[i] = i;
    }
    int[] temp = new int[len];
    List<String> result = new ArrayList<>();
    while (temp[0] < len) {
      temp[temp.length - 1]++;
      for (int i = temp.length - 1; i > 0; i--) {
        if (temp[i] == len) {
          temp[i] = 0;
          temp[i - 1]++;
        }
      }
      int[] tt = temp.clone();
      Arrays.sort(tt);
      if (!Arrays.equals(tt, destArray)) {
        continue;
      }
      String str = "";
      for (int i = 0; i < len; i++) {
        if (i > 0) {
          str += "&";
        }
        str += list.get(temp[i]);
      }
      result.add(baseUrl + "?" + str);
    }
    return result;
  }

  // update-end-author: taoyan date: 2023-2-21 for: Solving parameter order problems

  @Override
  public List<String> getUserAccountsByDepCode(String orgCode) {
    return userMapper.getUserAccountsByDepCode(orgCode);
  }

  @Override
  public boolean dictTableWhiteListCheckBySql(String selectSql) {
    return dictTableWhiteListHandler.isPassBySql(selectSql);
  }

  @Override
  public boolean dictTableWhiteListCheckByDict(String tableOrDictCode, String... fields) {
    if (fields == null || fields.length == 0) {
      return dictTableWhiteListHandler.isPassByDict(tableOrDictCode);
    } else {
      return dictTableWhiteListHandler.isPassByDict(tableOrDictCode, fields);
    }
  }

  @Override
  public Result<?> upload(MultipartFile file, UsageScenarios usageScenarios) {
    String objectId = uploadAndGetObjectId(file, usageScenarios, null);
    // Prefer preview URL for immediate <img> display; always embed canonical objectId so
    // admin convertObjectId / getUploadResponseUrl can recover upload/yyyyMMdd/...
    String preview = FilePreviewUtils.buildFileUrl(objectId);
    String payload =
        StringUtils.isNotBlank(preview) && preview.contains("objectId=") ? preview : objectId;
    return Result.OK(payload);
  }

  /** Upload file and return objectId (no domain). */
  private String uploadAndGetObjectId(
      MultipartFile file, UsageScenarios usageScenarios, String explicitOwnerUserId) {
    LoginUser loginUser = null;
    try {
      Object raw = StpUtil.getSession().get("loginUser");
      if (raw instanceof LoginUser) {
        loginUser = (LoginUser) raw;
      }
    } catch (Exception ignored) {
    }

    String ownerUserId = StringUtils.trimToNull(explicitOwnerUserId);
    if (ownerUserId == null && loginUser != null) {
      ownerUserId = StringUtils.trimToNull(loginUser.getId());
    }
    if (StringUtils.isBlank(ownerUserId)) {
      throw new JeecgBootException("File upload failed: ownerUserId required");
    }

    String secureFileName = FileNameUtil.generateSecureFileName(file.getOriginalFilename());
    String originalDisplayName = FileNameUtil.sanitizeOriginalDisplayName(file.getOriginalFilename());

    FileInfo fileInfo =
        fileStorageService
            .of(file)
            .setSaveFilename(secureFileName)
            .setPath(DateUtil.format(new Date(), "yyyyMMdd") + "/")
            .upload();

    String objectId = buildObjectIdFromFileInfo(fileInfo);
    if (!CommonUtils.isValidUploadObjectId(objectId)) {
      objectId = CommonUtils.convertObjectIdByDomain(fileInfo.getUrl());
    }
    while (objectId != null && objectId.startsWith("/")) {
      objectId = objectId.substring(1);
    }

    OssFile ossFile = new OssFile();
    ossFile.setFileName(fileInfo.getFilename());
    ossFile.setOriginalFileName(originalDisplayName);
    ossFile.setUrl(fileInfo.getUrl());
    ossFile.setUsageScenarios(usageScenarios);
    ossFile.setUserId(ownerUserId);
    ossFile.setBasePath(fileInfo.getBasePath());
    ossFile.setPath(fileInfo.getPath());
    ossFile.setExt(fileInfo.getExt());
    ossFile.setSize(BigInteger.valueOf(fileInfo.getSize()));
    ossFile.setObjectId(objectId);
    ossFile.setPlatform(fileInfo.getPlatform());

    ossFileService.save(ossFile);

    return objectId;
  }

  /**
   * Compose objectId from x-file-storage parts. Requires path to end with {@code /} (see setPath),
   * otherwise date and filename concatenate: {@code upload/20260922file_xxx.jpeg}.
   */
  private static String buildObjectIdFromFileInfo(FileInfo fileInfo) {
    if (fileInfo == null) {
      return null;
    }
    String base = StringUtils.defaultString(fileInfo.getBasePath());
    String path = StringUtils.defaultString(fileInfo.getPath());
    String name = StringUtils.defaultString(fileInfo.getFilename());
    String joined = (base + path + name).replace('\\', '/');
    while (joined.contains("//")) {
      joined = joined.replace("//", "/");
    }
    while (joined.startsWith("/")) {
      joined = joined.substring(1);
    }
    return CommonUtils.normalizeUploadObjectId(joined);
  }

  @Override
  public Result<String> uploadFileBytes(FileBytesUploadDTO uploadDTO) {
    if (uploadDTO == null || StringUtils.isBlank(uploadDTO.getBase64Content())) {
      return Result.error("file content is empty");
    }
    byte[] content = Base64.decode(uploadDTO.getBase64Content());
    MultipartFile file =
        new ByteArrayMultipartFile(
            "file",
            uploadDTO.getFileName(),
            uploadDTO.getContentType(),
            content);
    String objectId =
        uploadAndGetObjectId(file, uploadDTO.getUsageScenarios(), uploadDTO.getOwnerUserId());
    return Result.OK(objectId);
  }

  @SneakyThrows
  @Override
  public void downloadFile(String objectId, File file) {

    String fileURL = fileDomain + objectId;

    byte[] bytes =
        fileStorageService
            .download(fileURL)
            .setProgressListener(
                new ProgressListener() {
                  @Override
                  public void start() {
                    log.info("Starting file download: {}", fileURL);
                  }

                  @Override
                  public void progress(long progressSize, Long allSize) {
                    log.info("Download progress: {} / {}", progressSize, allSize);
                  }

                  @Override
                  public void finish() {
                    log.info("File download complete: {}", fileURL);
                  }
                })
            .bytes();

    FileUtil.writeBytes(bytes, file);
  }

  // ================= SHARD-ADAPT T7：跨库先查ID再IN 的 system 侧实现 =================

  @Override
  public List<String> listUserIdsByKeyword(
      String usernameLike, String realnameLike, boolean includeDeleted) {
    boolean hasUsername = StringUtils.isNotBlank(usernameLike);
    boolean hasRealname = StringUtils.isNotBlank(realnameLike);
    if (!hasUsername && !hasRealname) {
      return Collections.emptyList();
    }
    LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
    // C6 敏感表白名单：仅 select id，WHERE 仅命中 username/realname(/del_flag) 白名单列
    qw.select(SysUser::getId);
    // 还原调用方原始语义：仅当调用方原本带 del_flag=0 时才过滤已删用户
    if (!includeDeleted) {
      qw.eq(SysUser::getDelFlag, 0);
    }
    if (hasUsername && hasRealname) {
      qw.and(
          w ->
              w.like(SysUser::getUsername, usernameLike)
                  .or()
                  .like(SysUser::getRealname, realnameLike));
    } else if (hasUsername) {
      qw.like(SysUser::getUsername, usernameLike);
    } else {
      qw.like(SysUser::getRealname, realnameLike);
    }
    return userMapper.selectList(qw).stream().map(SysUser::getId).collect(Collectors.toList());
  }

  @Override
  public List<String> listUserIdsByInviterId(String inviterId) {
    if (StringUtils.isBlank(inviterId)) {
      return Collections.emptyList();
    }
    LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
    qw.select(SysUser::getId);
    qw.eq(SysUser::getInviterId, inviterId);
    // 还原调用方原始语义：原 inviter_id 子查询未带 del_flag 条件，故不过滤已删用户
    return userMapper.selectList(qw).stream().map(SysUser::getId).collect(Collectors.toList());
  }

  @Override
  public List<String> listUserIdsByRoleCodes(Collection<String> roleCodes) {
    if (roleCodes == null || roleCodes.isEmpty()) {
      return Collections.emptyList();
    }
    LambdaQueryWrapper<SysRole> roleQuery = new LambdaQueryWrapper<>();
    roleQuery.select(SysRole::getId);
    roleQuery.in(SysRole::getRoleCode, roleCodes);
    List<String> roleIds =
        roleMapper.selectList(roleQuery).stream()
            .map(SysRole::getId)
            .collect(Collectors.toList());
    if (roleIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> userIds = userMapper.getUserIdsByRoleIds(roleIds);
    return userIds == null ? Collections.emptyList() : userIds;
  }

  @Override
  public Long countUserRegistrationByDateRange(String startTime, String endTime, String roleCode) {
    if (StringUtils.isBlank(startTime) || StringUtils.isBlank(endTime)) {
      return 0L;
    }
    Date start;
    Date end;
    try {
      start = DateUtil.parse(startTime);
      end = DateUtil.parse(endTime);
    } catch (Exception e) {
      log.warn("[SHARD-T7] invalid registration date range: {} ~ {}", startTime, endTime);
      return 0L;
    }
    String roleId = null;
    if (StringUtils.isNotBlank(roleCode)) {
      LambdaQueryWrapper<SysRole> roleQuery = new LambdaQueryWrapper<>();
      roleQuery.select(SysRole::getId).eq(SysRole::getRoleCode, roleCode);
      List<String> roleIds =
          roleMapper.selectList(roleQuery).stream()
              .map(SysRole::getId)
              .collect(Collectors.toList());
      if (roleIds.isEmpty()) {
        return 0L;
      }
      roleId = roleIds.get(0);
    }
    // 复用既有 C6 合规统计：仅 COUNT(*) + create_time / id 白名单列，sys_user_role 同库
    Long count = userMapper.countRegistrationByRoleAndDateRange(roleId, start, end);
    return count == null ? 0L : count;
  }
}
