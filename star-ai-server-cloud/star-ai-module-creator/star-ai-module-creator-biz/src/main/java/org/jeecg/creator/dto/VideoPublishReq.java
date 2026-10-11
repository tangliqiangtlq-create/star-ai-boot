package org.jeecg.creator.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
@Data
public class VideoPublishReq {
    private Long creatorId;
    private String title;
    private String description;
    private String coverUrl;
    private String originalKey;
    private Long originalSize;
    private String mimeType;
    private Integer durationSec;
    private Long categoryId;
    private String priceType;
    private BigDecimal priceAmount;
    private String currency;
    private Integer previewSec;
    private String region;
    private List<String> tags;
}
