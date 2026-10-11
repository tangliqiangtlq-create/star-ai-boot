package org.jeecg.stream.dto;
import lombok.Data;

@Data
public class VideoListQuery {
    private String keyword;
    private Long categoryId;
    private String region;
    private String sortBy = "latest";
    private String priceType;
    private Integer pageSize = 20;
    private Integer pageNum = 1;
}
