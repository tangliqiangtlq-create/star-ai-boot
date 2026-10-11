package org.jeecg.common.api.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;

/**
 * File upload
 * DTO used by cloud API for interface transfer
 * @author: jeecg-boot
 */
@Data
public class FileUploadDTO implements Serializable {

    private static final long serialVersionUID = -4111953058578954386L;

    private MultipartFile file;

    private String bizPath;

    private String uploadType;

    private String customBucket;

    public FileUploadDTO(){

    }

    /**
     * Simple upload constructor 1
     * @param file
     * @param bizPath
     * @param uploadType
     */
    public FileUploadDTO(MultipartFile file,String bizPath,String uploadType){
        this.file = file;
        this.bizPath = bizPath;
        this.uploadType = uploadType;
    }

    /**
     * Declare bucket file upload constructor 2
     * @param file
     * @param bizPath
     * @param uploadType
     * @param customBucket
     */
    public FileUploadDTO(MultipartFile file,String bizPath,String uploadType,String customBucket){
        this.file = file;
        this.bizPath = bizPath;
        this.uploadType = uploadType;
        this.customBucket = customBucket;
    }

}
