package org.jeecg.common.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface IObjectStorageService {

    String generateUploadPresign(String bucket, String objectKey, int expirySec);

    String generateDownloadPresign(String bucket, String objectKey, int expirySec);

    void uploadFile(Path localFile, String bucket, String objectKey) throws Exception;

    void uploadDirectory(Path localDir, String bucket, String prefix) throws Exception;

    void deleteObject(String bucket, String objectKey) throws Exception;

    void deleteDirectory(String bucket, String prefix) throws Exception;

    InputStream getObject(String bucket, String objectKey) throws Exception;

    List<String> listObjects(String bucket, String prefix) throws Exception;
}
