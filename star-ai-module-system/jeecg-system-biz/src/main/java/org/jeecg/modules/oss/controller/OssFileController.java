package org.jeecg.modules.oss.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.security.OwnerOrAdminGuard;
import org.jeecg.common.util.security.Rejections;
import org.jeecg.config.oss.OssTenantOwnerUtil;
import org.jeecg.modules.oss.entity.OssFile;
import org.jeecg.modules.oss.service.IOssFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cloud storage endpoints with Ramp-A6 scoping: list/queryById/delete are owner-or-file_admin.
 *
 * @author: jeecg-boot
 */
@Slf4j
@Controller
@RequestMapping("/sys/oss/file")
public class OssFileController {

  @Autowired private IOssFileService ossFileService;

  @ResponseBody
  @PostMapping("/list")
  public Result<IPage<OssFile>> queryPageList(
      OssFile file,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<IPage<OssFile>> result = new Result<>();
    // A6-1: non-admin cannot filter by arbitrary userId from client
    if (!isFileAdmin()) {
      file.setUserId(null);
    }
    QueryWrapper<OssFile> queryWrapper =
        QueryGenerator.initQueryWrapper(file, req.getParameterMap());
    if (!isFileAdmin()) {
      String uid = OssTenantOwnerUtil.currentLoginUserId();
      if (uid == null || "__INTERNAL_CALL__".equals(uid)) {
        // Anonymous / internal session without a user context -> 401/403, not HTTP 200 + 500.
        throw Rejections.unauthorizedOrForbidden(I18nUtil.getMessage("common.error.unauthorized"));
      }
      queryWrapper.eq("user_id", uid);
    }
    Page<OssFile> page = new Page<>(pageNo, pageSize);
    IPage<OssFile> pageList = ossFileService.page(page, queryWrapper);
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  @ResponseBody
  @PostMapping("/upload")
  @SaCheckPermission("system:ossFile:upload")
  public Result upload(@RequestParam("file") MultipartFile multipartFile) {
    Result result = new Result();
    try {
      ossFileService.upload(multipartFile);
      result.success(I18nUtil.getMessage("ossFile.uploadSuccess"));
    } catch (Exception ex) {
      log.info("operation failed", ex);
      result.error500(I18nUtil.getMessage("ossFile.uploadFailed"));
    }
    return result;
  }

  private static final OwnerOrAdminGuard.BucketOwnerResolver<OssFile> OSS_FILE_OWNER =
      file -> file == null ? null : file.getUserId();

  @ResponseBody
  @DeleteMapping("/delete")
  public Result delete(@RequestParam(name = "id") String id) {
    Result result = new Result();
    OssFile file = ossFileService.getById(id);
    OwnerOrAdminGuard.assertOwnerOrAdmin(file, OSS_FILE_OWNER, "ossFile");
    ossFileService.delete(file);
    result.success(I18nUtil.getMessage("common.success.delete"));
    return result;
  }

  /** Query by id — A6 owner-or-admin. Dual-guard: service scoped lookup + row-level OwnerOrAdminGuard. */
  @ResponseBody
  @PostMapping("/queryById")
  public Result<OssFile> queryById(@RequestParam(name = "id") String id) {
    Result<OssFile> result = new Result<>();
    OssFile file = ossFileService.getById(id);
    OwnerOrAdminGuard.assertOwnerOrAdmin(file, OSS_FILE_OWNER, "ossFile");
    result.setResult(file);
    result.setSuccess(true);
    return result;
  }

  private static boolean isFileAdmin() {
    try {
      if (!StpUtil.isLogin()) {
        return false;
      }
      @SuppressWarnings("rawtypes")
      List roles = StpUtil.getRoleList();
      if (roles == null) {
        return false;
      }
      for (Object r : roles) {
        if (r != null && OssTenantOwnerUtil.FILE_ADMIN_ROLES.contains(String.valueOf(r))) {
          return true;
        }
      }
    } catch (Exception ignore) {
    }
    return false;
  }
}
