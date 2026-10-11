package org.jeecg.modules.oss.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.dromara.x.file.storage.core.FileInfo;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.AfterCommitRunner;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.FileNameUtil;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.common.util.oss.OssBootUtil;
import org.jeecg.config.oss.OssTenantOwnerUtil;
import org.jeecg.modules.oss.entity.OssFile;
import org.jeecg.modules.oss.mapper.OssFileMapper;
import org.jeecg.modules.oss.service.IOssFileService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * OssFileServiceImpl with Ramp-A6 hardening:
 *
 * <ul>
 *   <li>Every write stores owner ({@code userId}) and {@code tenantId}; blanks are rejected.
 *   <li>Interactive reads / deletes are scoped to current user or file_admin role; callers can
 *       also use the raw {@link #getOssFileDb} for the internal S3 storage driver when loading
 *       metadata for an otherwise authorised download flow.
 * </ul>
 */
@Service("ossFileService")
public class OssFileServiceImpl extends ServiceImpl<OssFileMapper, OssFile>
    implements IOssFileService {

  @Override
  public void upload(MultipartFile multipartFile) throws Exception {
    uploadForOwner(multipartFile, OssTenantOwnerUtil.currentLoginUserId());
  }

  @Override
  public void uploadForOwner(MultipartFile multipartFile, String ownerUserId) throws Exception {
    if (multipartFile == null) {
      throw new JeecgBootException("File upload failed: file is null");
    }
    if (oConvertUtils.isEmpty(ownerUserId)) {
      throw new JeecgBootException("File upload failed: ownerUserId required");
    }
    String fileName = FileNameUtil.generateSecureFileName(multipartFile.getOriginalFilename());
    if (fileName.startsWith("/")) {
      fileName = fileName.substring(1);
    }
    OssFile ossFile = new OssFile();
    ossFile.setFileName(fileName);
    ossFile.setOriginalFileName(
        FileNameUtil.sanitizeOriginalDisplayName(multipartFile.getOriginalFilename()));
    ossFile.setUserId(ownerUserId);
    String url = OssBootUtil.upload(multipartFile, "upload/test");
    if (oConvertUtils.isEmpty(url)) {
      throw new JeecgBootException("File upload failed! ");
    }
    ossFile.setUrl(OssBootUtil.getOriginalUrl(url));
    this.save(ossFile);
  }

  @Override
  public boolean save(OssFile entity) {
    // A6-1: Owner and tenant must be bound before write. Internal background jobs (monthly
    // statements, exports, KYC auto-saves) are expected to set owner explicitly; otherwise
    // the row becomes readable by every tenant through the unfiltered list endpoints.
    OssTenantOwnerUtil.fillOwnerIfAbsent(entity);
    if (oConvertUtils.isEmpty(entity.getUserId())) {
      throw new JeecgBootException("ossFile.userId required before save");
    }
    return super.save(entity);
  }

  @Override
  public boolean updateById(OssFile entity) {
    if (entity != null && !isFileAdminOrOwner(entity.getId(), entity.getUserId())) {
      throw new JeecgBootException("ossFile.updateById forbidden");
    }
    return super.updateById(entity);
  }

  /**
   * B11-2: delete DB row first (tx), then AFTER_COMMIT delete the S3/OSS object — the reverse of
   * the historical order. Commit succeeds or fails atomically on DB; remote object deletion never
   * holds a DB connection nor aborts a commit on S3 transient errors. (Files remaining due to
   * deleteUrl exception are cleaned up later via garbage-collection sweep.)
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(OssFile entity) {
    if (entity == null) {
      return false;
    }
    if (!isFileAdminOrOwner(entity.getId(), entity.getUserId())) {
      throw new JeecgBootException("ossFile.removeById forbidden");
    }
    final String deleteUrl = entity.getUrl();
    boolean ok = super.removeById(entity.getId());
    if (ok) {
      AfterCommitRunner.runAfterCommit(
          () -> {
            if (StrUtil.isBlank(deleteUrl)) return;
            try {
              OssBootUtil.deleteUrl(deleteUrl);
            } catch (Exception ex) {
              String msg =
                  "OssFileServiceImpl.removeById afterCommit deleteUrl failed url="
                      + deleteUrl
                      + " ex="
                      + ex.getMessage();
              if (log != null) {
                log.warn(msg);
              } else {
                System.err.println(msg);
              }
            }
          });
    }
    return ok;
  }

  @Override
  public boolean delete(OssFile ossFile) {
    return removeById(ossFile);
  }

  @Override
  @Cacheable(value = "dcs_ossFileService_getFileInfo", unless = "#result == null")
  public FileInfo getFileInfo(String objectId) {

    IOssFileService ossFileService = SpringContextUtils.getBean(IOssFileService.class);

    OssFile ossFile = ossFileService.getOssFileDb(objectId);
    if (ossFile == null) {
      return null;
    }

    FileInfo fileInfo = new FileInfo();
    fileInfo.setUrl(ossFile.getUrl());
    fileInfo.setObjectId(ossFile.getObjectId());
    fileInfo.setBasePath(ossFile.getBasePath());
    fileInfo.setPlatform(ossFile.getPlatform());
    fileInfo.setExt(ossFile.getExt());
    fileInfo.setFilename(ossFile.getFileName());
    fileInfo.setPath(ossFile.getPath());
    fileInfo.setSize(ossFile.getSize() != null ? ossFile.getSize().longValue() : null);

    return fileInfo;
  }

  @Override
  public OssFile getOssFile(String objectId) {
    OssFile row = getOssFileDb(objectId);
    if (row == null) {
      return null;
    }
    if (isFileAdminOrOwner(row.getId(), row.getUserId())) {
      return row;
    }
    throw new JeecgBootException("ossFile.getOssFile forbidden");
  }

  @Override
  public OssFile getOssFileDb(String objectId) {
    if (oConvertUtils.isEmpty(objectId)) {
      return null;
    }
    String normalized = objectId.trim();
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    if (oConvertUtils.isEmpty(normalized)) {
      return null;
    }
    QueryWrapper<OssFile> wrapper = new QueryWrapper<>();
    wrapper.eq("object_id", normalized).or().eq("object_id", "/" + normalized);
    return getOne(wrapper);
  }

  @Override
  public OssFile getByIdScoped(String id) {
    if (oConvertUtils.isEmpty(id)) {
      return null;
    }
    OssFile row = getById(id);
    if (row == null) {
      return null;
    }
    if (isFileAdminOrOwner(row.getId(), row.getUserId())) {
      return row;
    }
    throw new JeecgBootException("ossFile.getByIdScoped forbidden");
  }

  // ---- access helpers ----

  static boolean isFileAdminOrOwner(String ossFileId, String ownerUserId) {
    try {
      if (StpUtil.isLogin()) {
        String uid = StpUtil.getLoginIdAsString();
        if (Objects.equals(uid, ownerUserId)) {
          return true;
        }
        @SuppressWarnings("rawtypes")
        List roles = StpUtil.getRoleList();
        if (roles != null) {
          for (Object r : roles) {
            String s = r == null ? null : String.valueOf(r);
            if (OssTenantOwnerUtil.FILE_ADMIN_ROLES.contains(s)) {
              return true;
            }
          }
        }
        if (OssTenantOwnerUtil.isInternalCallContext()) {
          return true;
        }
      }
    } catch (Exception ignore) {
      // deny by default
    }
    return false;
  }
}
