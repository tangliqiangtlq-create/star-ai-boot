package org.jeecg.creator.dto;
import lombok.Data;
@Data
public class PresignReq {
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Integer chunkCount;
}
