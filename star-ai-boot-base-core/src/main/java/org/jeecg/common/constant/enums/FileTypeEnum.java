package org.jeecg.common.constant.enums;

import org.jeecg.common.util.oConvertUtils;

/**
 * FileType
 */
public enum FileTypeEnum {
    //documentType(folder:folder excel:excel doc:word pp:ppt image:picture archive:Other documents video:video voice:voice)
//    FOLDER
    xls(".xls","excel","excel"),
    xlsx(".xlsx","excel","excel"),
    doc(".doc","doc","word"),
    docx(".docx","doc","word"),
    ppt(".ppt","pp","ppt"),
    pptx(".pptx","pp","ppt"),
    gif(".gif","image","Image"),
    jpg(".jpg","image","Image"),
    jpeg(".jpeg","image","Image"),
    png(".png","image","Image"),
    txt(".txt","text","Text"),
    avi(".avi","video","Video"),
    mov(".mov","video","Video"),
    rmvb(".rmvb","video","Video"),
    rm(".rm","video","Video"),
    flv(".flv","video","Video"),
    mp4(".mp4","video","Video"),
    zip(".zip","zip","Archive file"),
    pdf(".pdf","pdf","pdf"),
    mp3(".mp3","mp3","Voice");

    private String type;
    private String value;
    private String text;
    private FileTypeEnum(String type,String value,String text){
        this.type = type;
        this.value = value;
        this.text = text;
    }
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public static FileTypeEnum getByType(String type){
        if (oConvertUtils.isEmpty(type)) {
            return null;
        }
        for (FileTypeEnum val : values()) {
            if (val.getType().equals(type)) {
                return val;
            }
        }
        return null;
    }

}
