package org.jeecg.creator.dto;
import lombok.Data;
import java.util.List;
@Data
public class PresignResp {
    private String bucket;
    private String objectKey;
    private List<ChunkUrl> chunks;
    @Data
    public static class ChunkUrl {
        private int index;
        private String url;
    }
}
