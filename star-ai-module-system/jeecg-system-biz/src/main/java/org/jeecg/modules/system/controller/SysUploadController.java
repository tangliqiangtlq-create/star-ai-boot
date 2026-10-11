package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.FileNameUtil;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.MinioUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.oss.entity.OssFile;
import org.jeecg.modules.oss.service.IOssFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * MinIO file upload example
 *
 * @author: jeecg-boot*/
@Slf4j
@RestController
@RequestMapping(value = "/sys/upload", method = RequestMethod.POST)
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class SysUploadController {
  @Autowired private IOssFileService ossFileService;

  /**
 * Upload
 *
 * @param request*/
  @PostMapping(value = "/uploadMinio")
  public Result<?> uploadMinio(HttpServletRequest request) throws Exception {
    Result<?> result = new Result<>();
    String bizPath = request.getParameter("biz");

    // LOWCOD-2580 sys/common/upload endpoint allows arbitrary file upload
    // Use CommonUtils.sanitizePath for stricter path validation
    if (oConvertUtils.isNotEmpty(bizPath)) {
      try {
        bizPath = CommonUtils.sanitizePath(bizPath);
      } catch (JeecgBootException e) {
        throw new JeecgBootException(I18nUtil.getMessage("sysUpload.bizPathInvalid"));
      }
    }

    if (oConvertUtils.isEmpty(bizPath)) {
      bizPath = "";
    }
    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
    // Get uploaded file object
    MultipartFile file = multipartRequest.getFile("file");
    // Secure object name for storage; keep client original name for display via originalFileName
    String orgName = FileNameUtil.generateSecureFileName(file.getOriginalFilename());
    if (orgName.startsWith("/")) {
      orgName = orgName.substring(1);
    }
    String fileUrl = MinioUtil.upload(file, bizPath);
    if (oConvertUtils.isEmpty(fileUrl)) {
      return Result.error(I18nUtil.getMessage("sysUpload.uploadFailed"));
    }
    // SaveFileInfo
    OssFile minioFile = new OssFile();
    minioFile.setFileName(orgName);
    minioFile.setOriginalFileName(
        FileNameUtil.sanitizeOriginalDisplayName(file.getOriginalFilename()));
    minioFile.setUrl(fileUrl);
    ossFileService.save(minioFile);
    result.setMessage(fileUrl);
    result.setSuccess(true);
    return result;
  }
}
