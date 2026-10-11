package org.jeecg.transcode.dto;
import lombok.Data;

@Data
public class VideoUploadedMsg {
    private Long videoId;
    private Long creatorId;
    private String originalKey;
    private String bucket;
}
