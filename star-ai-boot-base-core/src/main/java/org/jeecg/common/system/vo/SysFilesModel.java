package org.jeecg.common.system.vo;


/**
 * @Description: System file entity class
 * @author: wangshuai
 * @date: August 11, 2022 9:48
 */
public class SysFilesModel {
    /**Primary keyid*/
    private String id;
    /**FileName*/
    private String fileName;
    /**File URL/path*/
    private String url;
    /**Document type (folder: folder excel: excel doc: word pp: ppt image: image archive: other video: video)*/
    private String fileType;
    /**File upload type (temp/local temp upload, manage/knowledge base)*/
    private String storeType;
    /**file size（kb）*/
    private Double fileSize;
    /**Tenantid*/
    private String tenantId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getStoreType() {
        return storeType;
    }

    public void setStoreType(String storeType) {
        this.storeType = storeType;
    }

    public Double getFileSize() {
        return fileSize;
    }

    public void setFileSize(Double fileSize) {
        this.fileSize = fileSize;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}