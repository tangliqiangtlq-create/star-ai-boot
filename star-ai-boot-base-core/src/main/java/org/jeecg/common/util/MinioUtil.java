package org.jeecg.common.util;

import io.minio.*;
import io.minio.http.Method;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.util.filter.SsrfFileTypeFilter;
import org.jeecg.common.util.filter.StrAttackFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLDecoder;

/**
 * minioFile upload tool class
 * @author: jeecg-boot
 */
public class MinioUtil {
    private static final Logger log = LoggerFactory.getLogger(MinioUtil.class);
    private static String minioUrl;
    private static String minioName;
    private static String minioPass;
    private static String bucketName;

    public static void setMinioUrl(String minioUrl) {
        MinioUtil.minioUrl = minioUrl;
    }

    public static void setMinioName(String minioName) {
        MinioUtil.minioName = minioName;
    }

    public static void setMinioPass(String minioPass) {
        MinioUtil.minioPass = minioPass;
    }

    public static void setBucketName(String bucketName) {
        MinioUtil.bucketName = bucketName;
    }

    public static String getMinioUrl() {
        return minioUrl;
    }

    public static String getBucketName() {
        return bucketName;
    }

    private static MinioClient minioClient = null;

    /**
     * Upload file.
     * @param file
     * @return
     */
    public static String upload(MultipartFile file, String bizPath, String customBucket) throws Exception {
        String fileUrl = "";
        //update-begin-author:wangshuai date:20201012 for: filter special chars in upload folder names to prevent attacks
        //update-begin-author:security date:2024 for: use stricter path validation to prevent path traversal
        if (oConvertUtils.isNotEmpty(bizPath)) {
            try {
                bizPath = CommonUtils.sanitizePath(bizPath);
            } catch (Exception e) {
                throw new Exception("Upload directory bizPath, invalid format!", e);
            }
        }
        //update-end-author:security date:2024 for: use stricter path validation to prevent path traversal
        //update-end-author:wangshuai date:20201012 for: filter special chars in upload folder names to prevent attacks

        //update-begin-author:liusq date:20210809 for: filter upload file types
        SsrfFileTypeFilter.checkUploadFileType(file);
        //update-end-author:liusq date:20210809 for: filter uploaded file types

        String newBucket = bucketName;
        if(oConvertUtils.isNotEmpty(customBucket)){
            newBucket = customBucket;
        }
        try {
            initMinio(minioUrl, minioName,minioPass);
            // Check if the bucket already exists
            if(minioClient.bucketExists(BucketExistsArgs.builder().bucket(newBucket).build())) {
                log.info("Bucket already exists.");
            } else {
                // Create a bucket named "ota"
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(newBucket).build());
                log.info("create a new bucket.");
            }
            try (InputStream stream = file.getInputStream()) {
              // Get file name (safe random name, do not use the client’s original name directly)
              String orgName = file.getOriginalFilename();
              if (orgName == null || "".equals(orgName)) {
                orgName = file.getName();
              }
              String secureName = FileNameUtil.generateSecureFileName(orgName);
              if (secureName.startsWith("/")) {
                secureName = secureName.substring(1);
              }
              String objectName = bizPath + "/" + secureName;

              // Use putObject to upload a local file to the bucket.
              if (objectName.startsWith(SymbolConstant.SINGLE_SLASH)) {
                objectName = objectName.substring(1);
              }
              PutObjectArgs objectArgs =
                  PutObjectArgs.builder()
                      .object(objectName)
                      .bucket(newBucket)
                      .contentType("application/octet-stream")
                      .stream(stream, stream.available(), -1)
                      .build();
              minioClient.putObject(objectArgs);
              fileUrl = minioUrl + newBucket + "/" + objectName;
            }
        }catch (Exception e){
            log.error("operation failed", e);
        }
        return fileUrl;
    }

    /**
     * File upload
     * @param file
     * @param bizPath
     * @return
     */
    public static String upload(MultipartFile file, String bizPath) throws Exception {
        return upload(file,bizPath,null);
    }

    /**
     * Get file stream
     * @param bucketName
     * @param objectName
     * @return
     */
    public static InputStream getMinioFile(String bucketName,String objectName){
        InputStream inputStream = null;
        try {
            initMinio(minioUrl, minioName, minioPass);
            GetObjectArgs objectArgs = GetObjectArgs.builder().object(objectName)
                    .bucket(bucketName).build();
            inputStream = minioClient.getObject(objectArgs);
        } catch (Exception e) {
            log.info("FileGetFailed" + "[redacted]");
        }
        return inputStream;
    }

    /**
     * DeleteFile
     * @param bucketName
     * @param objectName
     * @throws Exception
     */
    public static void removeObject(String bucketName, String objectName) {
        try {
            initMinio(minioUrl, minioName,minioPass);
            RemoveObjectArgs objectArgs = RemoveObjectArgs.builder().object(objectName)
                    .bucket(bucketName).build();
            minioClient.removeObject(objectArgs);
        }catch (Exception e){
            log.info("FileDelete failed" + "[redacted]");
        }
    }

    /**
     * Get file external link
     * @param bucketName
     * @param objectName
     * @param expires
     * @return
     */
    public static String getObjectUrl(String bucketName, String objectName, Integer expires) {
        initMinio(minioUrl, minioName,minioPass);
        try{
            //update-begin---author:liusq  Date:20220121  for: getting file external link fails with empty method, causing download/preview failure----
            GetPresignedObjectUrlArgs objectArgs = GetPresignedObjectUrlArgs.builder().object(objectName)
                    .bucket(bucketName)
                    .expiry(expires).method(Method.GET).build();
            //update-begin---author:liusq  Date:20220121  for: getting file external link fails with empty method, causing download/preview failure----
            String url = minioClient.getPresignedObjectUrl(objectArgs);
            return URLDecoder.decode(url,"UTF-8");
        }catch (Exception e){
            log.info("FilePathGetFailed" + "[redacted]");
        }
        return null;
    }

    /**
     * Initialize client.
     * @param minioUrl
     * @param minioName
     * @param minioPass
     * @return
     */
    private static MinioClient initMinio(String minioUrl, String minioName,String minioPass) {
        if (minioClient == null) {
            try {
                minioClient = MinioClient.builder()
                        .endpoint(minioUrl)
                        .credentials(minioName, minioPass)
                        .build();
            } catch (Exception e) {
                log.error("operation failed", e);
            }
        }
        return minioClient;
    }

    /**
     * Upload file to MinIO.
     * @param stream
     * @param relativePath
     * @return
     */
    public static String upload(InputStream stream,String relativePath) throws Exception {
        // Validate relativePath to prevent path traversal
        if (oConvertUtils.isNotEmpty(relativePath)) {
            try {
                relativePath = CommonUtils.sanitizePath(relativePath);
            } catch (Exception e) {
                throw new Exception("Invalid file path format, path traversal prohibited!", e);
            }
        }
        initMinio(minioUrl, minioName,minioPass);
        if(minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            log.info("Bucket already exists.");
        } else {
            // Create a bucket named "ota"
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("create a new bucket.");
        }
        PutObjectArgs objectArgs = PutObjectArgs.builder().object(relativePath)
                .bucket(bucketName)
                .contentType("application/octet-stream")
                .stream(stream,stream.available(),-1).build();
        minioClient.putObject(objectArgs);
        stream.close();
        return minioUrl+bucketName+"/"+relativePath;
    }

}
