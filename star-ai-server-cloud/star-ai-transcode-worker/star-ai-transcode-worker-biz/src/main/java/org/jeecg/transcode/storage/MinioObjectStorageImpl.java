package org.jeecg.transcode.storage;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.storage.IObjectStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "minio", matchIfMissing = true)
public class MinioObjectStorageImpl implements IObjectStorageService {

    private final MinioClient minioClient;

    @Override
    public String generateUploadPresign(String bucket, String objectKey, int expirySec) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucket)
                .object(objectKey)
                .expiry(expirySec)
                .build());
        } catch (Exception e) {
            throw new RuntimeException("generateUploadPresign failed: bucket=" + bucket + ", key=" + objectKey, e);
        }
    }

    @Override
    public String generateDownloadPresign(String bucket, String objectKey, int expirySec) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(objectKey)
                .expiry(expirySec)
                .build());
        } catch (Exception e) {
            throw new RuntimeException("generateDownloadPresign failed: bucket=" + bucket + ", key=" + objectKey, e);
        }
    }

    @Override
    public void uploadFile(Path localFile, String bucket, String objectKey) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
            .bucket(bucket)
            .object(objectKey)
            .stream(Files.newInputStream(localFile), Files.size(localFile), -1)
            .build());
    }

    @Override
    public void uploadDirectory(Path localDir, String bucket, String prefix) throws Exception {
        try (var walk = Files.walk(localDir)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                try {
                    String relative = localDir.relativize(file).toString().replace('\\', '/');
                    String objectKey = prefix + "/" + relative;
                    minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(Files.newInputStream(file), Files.size(file), -1)
                        .build());
                    log.info("MinIO upload: bucket={}, key={}", bucket, objectKey);
                } catch (Exception e) {
                    throw new RuntimeException("uploadDirectory failed: file=" + file, e);
                }
            });
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }

    @Override
    public void deleteDirectory(String bucket, String prefix) throws Exception {
        for (String key : listObjects(bucket, prefix)) {
            deleteObject(bucket, key);
        }
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }

    @Override
    public List<String> listObjects(String bucket, String prefix) throws Exception {
        List<String> keys = new ArrayList<>();
        var args = ListObjectsArgs.builder().bucket(bucket).prefix(prefix).build();
        for (var result : minioClient.listObjects(args)) {
            keys.add(result.get().objectName());
        }
        return keys;
    }
}
