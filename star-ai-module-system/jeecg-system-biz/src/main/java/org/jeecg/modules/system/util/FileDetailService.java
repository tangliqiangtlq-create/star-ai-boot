package org.jeecg.modules.system.util;

import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.recorder.FileRecorder;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.modules.oss.service.IOssFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FileDetailService implements FileRecorder {

  @Autowired private IOssFileService ossFileService;

  @Override
  public boolean save(FileInfo fileInfo) {
    return true;
  }

  @Override
  public void update(FileInfo fileInfo) {}

  @Override
  public FileInfo getByUrl(String url) {

    String objectId = CommonUtils.convertObjectIdByDomain(url);

    return ossFileService.getFileInfo(objectId);
  }

  @Override
  public boolean delete(String url) {
    return false;
  }

  @Override
  public void saveFilePart(FilePartInfo filePartInfo) {}

  @Override
  public void deleteFilePartByUploadId(String uploadId) {}
}
