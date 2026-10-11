package org.jeecg.modules.oss.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.dromara.x.file.storage.core.FileInfo;
import org.jeecg.modules.oss.entity.OssFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: OOS cloud storage service interface
 *
 * @author: jeecg-boot
 */
public interface IOssFileService extends IService<OssFile> {

  /**
   * Interactive upload. Owner = current Sa-Token login user.
   *
   * @param multipartFile multipart payload
   * @throws Exception on transport / storage failures
   */
  void upload(MultipartFile multipartFile) throws Exception;

  /**
   * Background-job upload with explicit owner (e.g. monthly statement export, KYC auto-save).
   * Required because there is no interactive user on the request thread.
   */
  void uploadForOwner(MultipartFile multipartFile, String ownerUserId) throws Exception;

  /**
   * Delete the provided oss object (storage + DB row).  File_admin or owner-only enforcement is
   * applied inside the service layer.
   */
  boolean delete(OssFile ossFile);

  /** Loads storage metadata (file size, url, platform) by object id; admin bypass for background jobs. */
  FileInfo getFileInfo(String objectId);

  /**
   * Read an OssFile row enforcing that the caller is the owner or a file_admin; throws if the
   * current login cannot access it.  Prefer this for interactive response handlers.
   */
  OssFile getOssFile(String objectId);

  /**
   * Raw DB lookup without owner enforcement — reserved for internal drivers that still want to
   * load metadata before passing the row through a dedicated access verifier (for example the
   * signed URL download flow that already enforces owner/role/signedUrl).
   */
  OssFile getOssFileDb(String objectId);

  /**
   * A6: load by primary key with owner-or-file_admin enforcement. Returns null when missing;
   * throws when the row exists but the caller is not allowed.
   */
  OssFile getByIdScoped(String id);
}
