package org.jeecg.common.util.oss;

import com.aliyun.oss.ClientConfiguration;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileItemStream;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.FileNameUtil;
import org.jeecg.common.util.filter.SsrfFileTypeFilter;
import org.jeecg.common.util.filter.StrAttackFilter;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Date;
import java.util.UUID;

/**
 * @Description: Alibaba Cloud oss ​​upload tool class (high dependency version)
 * @Date: 2019/5/10
 * @author: jeecg-boot
 */
@Slf4j
public class OssBootUtil {

    private static String endPoint;
    private static String accessKeyId;
    private static String accessKeySecret;
    private static String bucketName;
    private static String staticDomain;

    public static void setEndPoint(String endPoint) {
        OssBootUtil.endPoint = endPoint;
    }

    public static void setAccessKeyId(String accessKeyId) {
        OssBootUtil.accessKeyId = accessKeyId;
    }

    public static void setAccessKeySecret(String accessKeySecret) {
        OssBootUtil.accessKeySecret = accessKeySecret;
    }

    public static void setBucketName(String bucketName) {
        OssBootUtil.bucketName = bucketName;
    }

    public static void setStaticDomain(String staticDomain) {
        OssBootUtil.staticDomain = staticDomain;
    }

    public static String getStaticDomain() {
        return staticDomain;
    }

    public static String getEndPoint() {
        return endPoint;
    }

    public static String getAccessKeyId() {
        return accessKeyId;
    }

    public static String getAccessKeySecret() {
        return accessKeySecret;
    }

    public static String getBucketName() {
        return bucketName;
    }

    public static OSSClient getOssClient() {
        return ossClient;
    }

    /**
     * OSS utility client.
     */
    private static OSSClient ossClient = null;

    /**
     * Upload file to Aliyun OSS.
     * Returns the full access URL on success; returns null on failure.
     *
     * @param file    file to upload
     * @param fileDir save directory
     * @return relative file path in OSS
     */
    public static String upload(MultipartFile file, String fileDir,String customBucket) throws Exception {
        //update-begin-author:liusq date:20210809 for: filter upload file types
        SsrfFileTypeFilter.checkUploadFileType(file);
        //update-end-author:liusq date:20210809 for: filter uploaded file types

        String filePath = null;
        initOss(endPoint, accessKeyId, accessKeySecret);
        StringBuilder fileUrl = new StringBuilder();
        String newBucket = bucketName;
        if(oConvertUtils.isNotEmpty(customBucket)){
            newBucket = customBucket;
        }
        try {
            //Determine whether the bucket exists, create the bucket if it does not exist
            if(!ossClient.doesBucketExist(newBucket)){
                ossClient.createBucket(newBucket);
            }
            // Get file name (safe random name)
            String orgName = file.getOriginalFilename();
            if (orgName == null || "".equals(orgName)) {
              orgName = file.getName();
            }
            String fileName = FileNameUtil.generateSecureFileName(orgName);
            if (fileName.startsWith("/")) {
              fileName = fileName.substring(1);
            }
            //update-begin-author:security date:2024 for: use stricter path validation to prevent path traversal
            if (oConvertUtils.isNotEmpty(fileDir)) {
                try {
                    fileDir = CommonUtils.sanitizePath(fileDir);
                } catch (Exception e) {
                    throw new Exception("Upload directory fileDir, invalid format!", e);
                }
            }
            //update-end-author:security date:2024 for: use stricter path validation to prevent path traversal
            if (!fileDir.endsWith(SymbolConstant.SINGLE_SLASH)) {
                fileDir = fileDir.concat(SymbolConstant.SINGLE_SLASH);
            }
            //update-begin-author:wangshuai date:20201012 for: filter special chars in upload folder names to prevent attacks
            fileDir=StrAttackFilter.filter(fileDir);
            //update-end-author:wangshuai date:20201012 for: filter special chars in upload folder names to prevent attacks
            fileUrl = fileUrl.append(fileDir + fileName);

            if (oConvertUtils.isNotEmpty(staticDomain) && staticDomain.toLowerCase().startsWith(CommonConstant.STR_HTTP)) {
                filePath = staticDomain + SymbolConstant.SINGLE_SLASH + fileUrl;
            } else {
                filePath = "https://" + newBucket + "." + endPoint + SymbolConstant.SINGLE_SLASH + fileUrl;
            }
            PutObjectResult result = ossClient.putObject(newBucket, fileUrl.toString(), file.getInputStream());
            // Set permission (public read)
//            ossClient.setBucketAcl(newBucket, CannedAccessControlList.PublicRead);
            if (result != null) {
                log.info("------OSSFileUpload successful------" + fileUrl);
            }
        } catch (IOException e) {
            log.error("operation failed", e);
            return null;
        }catch (Exception e) {
            log.error("operation failed", e);
            return null;
        }
        return filePath;
    }

    /**
     * Get original URL
    * @param url: original URL
    * @Return: java.lang.String
    */
    public static String getOriginalUrl(String url) {
        String originalDomain = "https://" + bucketName + "." + endPoint;
        if(oConvertUtils.isNotEmpty(staticDomain) && url.indexOf(staticDomain)!=-1){
            url = url.replace(staticDomain,originalDomain);
        }
        return url;
    }

    /**
     * File upload
     * @param file
     * @param fileDir
     * @return
     */
    public static String upload(MultipartFile file, String fileDir) throws Exception {
        return upload(file, fileDir,null);
    }

    /**
     * Upload file to Aliyun OSS.
     * Returns the full access URL on success; returns null on failure.
     *
     * @param file    file to upload
     * @param fileDir save directory
     * @return relative file path in OSS
     */
    public static String upload(FileItemStream file, String fileDir) {
        String filePath = null;
        initOss(endPoint, accessKeyId, accessKeySecret);
        StringBuilder fileUrl = new StringBuilder();
        try {
            String suffix = file.getName().substring(file.getName().lastIndexOf('.'));
            String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;
            //update-begin-author:security date:2024 for: use stricter path validation to prevent path traversal
            if (oConvertUtils.isNotEmpty(fileDir)) {
                try {
                    fileDir = CommonUtils.sanitizePath(fileDir);
                } catch (Exception e) {
                    log.error("Upload directory fileDir invalid format: {}");
                    return null;
                }
            }
            //update-end-author:security date:2024 for: use stricter path validation to prevent path traversal
            if (!fileDir.endsWith(SymbolConstant.SINGLE_SLASH)) {
                fileDir = fileDir.concat(SymbolConstant.SINGLE_SLASH);
            }
            fileDir = StrAttackFilter.filter(fileDir);
            fileUrl = fileUrl.append(fileDir + fileName);
            if (oConvertUtils.isNotEmpty(staticDomain) && staticDomain.toLowerCase().startsWith(CommonConstant.STR_HTTP)) {
                filePath = staticDomain + SymbolConstant.SINGLE_SLASH + fileUrl;
            } else {
                filePath = "https://" + bucketName + "." + endPoint + SymbolConstant.SINGLE_SLASH + fileUrl;
            }
            PutObjectResult result = ossClient.putObject(bucketName, fileUrl.toString(), file.openStream());
            // Set permission (public read)
            ossClient.setBucketAcl(bucketName, CannedAccessControlList.PublicRead);
            if (result != null) {
                log.info("------OSSFileUpload successful------" + fileUrl);
            }
        } catch (IOException e) {
            log.error("operation failed", e);
            return null;
        }
        return filePath;
    }

    /**
     * DeleteFile
     * @param url
     */
    public static void deleteUrl(String url) {
        deleteUrl(url,null);
    }

    /**
     * DeleteFile
     * @param url
     */
    public static void deleteUrl(String url,String bucket) {
        String newBucket = bucketName;
        if(oConvertUtils.isNotEmpty(bucket)){
            newBucket = bucket;
        }
        String bucketUrl = "";
        if (oConvertUtils.isNotEmpty(staticDomain) && staticDomain.toLowerCase().startsWith(CommonConstant.STR_HTTP)) {
            bucketUrl = staticDomain + SymbolConstant.SINGLE_SLASH ;
        } else {
            bucketUrl = "https://" + newBucket + "." + endPoint + SymbolConstant.SINGLE_SLASH;
        }
        //TODO Deletion of cloud storage files is temporarily not allowed
        //initOss(endPoint, accessKeyId, accessKeySecret);
        url = url.replace(bucketUrl,"");
        ossClient.deleteObject(newBucket, url);
    }

    /**
     * DeleteFile
     * @param fileName
     */
    public static void delete(String fileName) {
        ossClient.deleteObject(bucketName, fileName);
    }

    /**
     * Get file stream
     * @param objectName
     * @param bucket
     * @return
     */
    public static InputStream getOssFile(String objectName,String bucket){
        InputStream inputStream = null;
        try{
            String newBucket = bucketName;
            if(oConvertUtils.isNotEmpty(bucket)){
                newBucket = bucket;
            }
            initOss(endPoint, accessKeyId, accessKeySecret);
            //update-begin---author:liusq  Date:20220120  for: replace objectName prefix so key mismatch does not hide files----
            objectName = OssBootUtil.replacePrefix(objectName,bucket);
            //update-end---author:liusq  Date:20220120  for: replace objectName prefix so key mismatch does not hide files----
            OSSObject ossObject = ossClient.getObject(newBucket,objectName);
            inputStream = new BufferedInputStream(ossObject.getObjectContent());
        }catch (Exception e){
            log.info("FileGetFailed" + "[redacted]");
        }
        return inputStream;
    }

    ///**
    // * Get file stream
    // * @param objectName
    // * @return
    // */
    //public static InputStream getOssFile(String objectName){
    //    return getOssFile(objectName,null);
    //}

    /**
     * Get file external link
     * @param bucketName
     * @param objectName
     * @param expires
     * @return
     */
    public static String getObjectUrl(String bucketName, String objectName, Date expires) {
        initOss(endPoint, accessKeyId, accessKeySecret);
        try{
            //update-begin---author:liusq  Date:20220120  for: replace objectName prefix so key mismatch does not hide files----
            objectName = OssBootUtil.replacePrefix(objectName,bucketName);
            //update-end---author:liusq  Date:20220120  for: replace objectName prefix so key mismatch does not hide files----
            if(ossClient.doesObjectExist(bucketName,objectName)){
                URL url = ossClient.generatePresignedUrl(bucketName,objectName,expires);
                //log.info("original url: {}", url.toString());
                //log.info("decode url : {}", URLDecoder.decode(url.toString(), "UTF-8"));
                //[issues/4023] Problem: After the oss external link is converted to coding, part of it is invalid, about one-third; you can return it directly without coding #4023
                return url.toString();
            }
        }catch (Exception e){
            log.info("FilePathGetFailed" + "[redacted]"); 
        }
        return null;
    }

    /**
     * Initialize OSS client.
     *
     * @return
     */
    private static OSSClient initOss(String endpoint, String accessKeyId, String accessKeySecret) {
        if (ossClient == null) {
            ossClient = new OSSClient(endpoint,
                    new DefaultCredentialProvider(accessKeyId, accessKeySecret),
                    new ClientConfiguration());
        }
        return ossClient;
    }


    /**
     * Upload file to OSS.
     * @param stream
     * @param relativePath
     * @return
     */
    public static String upload(InputStream stream, String relativePath) {
        // Validate relativePath to prevent path traversal
        if (oConvertUtils.isNotEmpty(relativePath)) {
            try {
                relativePath = CommonUtils.sanitizePath(relativePath);
            } catch (Exception e) {
                log.error("Invalid file path format, path traversal prohibited: {}");
                return null;
            }
        }
        String filePath = null;
        String fileUrl = relativePath;
        initOss(endPoint, accessKeyId, accessKeySecret);
        if (oConvertUtils.isNotEmpty(staticDomain) && staticDomain.toLowerCase().startsWith(CommonConstant.STR_HTTP)) {
            filePath = staticDomain + SymbolConstant.SINGLE_SLASH + relativePath;
        } else {
            filePath = "https://" + bucketName + "." + endPoint + SymbolConstant.SINGLE_SLASH + fileUrl;
        }
        PutObjectResult result = ossClient.putObject(bucketName, fileUrl.toString(),stream);
        // Set permission (public read)
        ossClient.setBucketAcl(bucketName, CannedAccessControlList.PublicRead);
        if (result != null) {
            log.info("------OSSFileUpload successful------" + fileUrl);
        }
        return filePath;
    }

    /**
     * Replace prefix to prevent key mismatch when fetching files
     * @param objectName file upload path key
     * @param customBucket custom bucket
     * @date 2022-01-20
     * @author lsq
     * @return
     */
    private static String replacePrefix(String objectName,String customBucket){
        log.info("------replacePrefix---before replace---objectName:{}",objectName);
        if(oConvertUtils.isNotEmpty(staticDomain)){
            objectName= objectName.replace(staticDomain+SymbolConstant.SINGLE_SLASH,"");
        }else{
            String newBucket = bucketName;
            if(oConvertUtils.isNotEmpty(customBucket)){
                newBucket = customBucket;
            }
            String path ="https://" + newBucket + "." + endPoint + SymbolConstant.SINGLE_SLASH;
            objectName = objectName.replace(path,"");
        }
        log.info("------replacePrefix---after replace---objectName:{}",objectName);
        return objectName;
    }
}