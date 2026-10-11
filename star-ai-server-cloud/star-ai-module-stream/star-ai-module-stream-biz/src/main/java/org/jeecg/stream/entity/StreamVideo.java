package org.jeecg.stream.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("stream_video")
public class StreamVideo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 creator_account.id */
    private Long creatorId;

    private String title;

    private String description;

    private String coverUrl;

    private Integer durationSec;

    private Long categoryId;

    /** cn/sg/us */
    private String region;

    /** stream-published / stream-creator-draft */
    private String storageBucket;

    /** MinIO / S3 Key (draft 或 published) */
    private String originalKey;

    private Long originalSize;

    private String mimeType;

    /** HLS master.m3u8 Key */
    private String hlsMasterKey;

    /** 1080p:720p:480p */
    private String hlsPresets;

    /** pending/processing/completed/failed */
    private String transcodeStatus;

    private String transcodeJobId;

    /** draft/pending_review/published/rejected/unpublished */
    private String publishStatus;

    /** 驳回原因 */
    private String reviewNote;

    private Long reviewedBy;

    private Date reviewedAt;

    private Date publishedAt;

    /** free=免费 / paid=每集付费 / member_only=会员免费看 */
    private String priceType;

    /** 单集定价 (price_type=paid 时生效) */
    private BigDecimal priceAmount;

    private String currency;

    /** 试看秒数 (0=不允许试看, 付费内容建议 30-60s) */
    private Integer previewSec;

    private String priceStatus;

    /** 定价审核人 (price_status=pending 时) */
    private Long priceAuditBy;

    private Date priceAuditAt;

    /** 驳回原因 */
    private String priceAuditRemark;

    private BigDecimal revenueRateSnapshot;

    private BigDecimal subscriptionRevenueRateSnapshot;

    /** pending/passed/flagged/rejected */
    private String autoReviewStatus;

    /** NSFW 总分 0-1 */
    private BigDecimal autoReviewScore;

    private String autoReviewDetail;

    /** 额外屏蔽的国家 ISO2 码 (逗号分隔) */
    private String geoBlockCountries;

    private Long playCount;

    private Long uniqueViewCount;

    private Long likeCount;

    private Long favoriteCount;

    private Integer purchaseCount;

    private BigDecimal totalRevenue;

    private Long tenantId;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private Integer delFlag;

    private Integer revision;

}
