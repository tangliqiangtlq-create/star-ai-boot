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
@TableName("stream_video_collection")
public class StreamVideoCollection implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long creatorId;

    /** 剧集编码 (同一部剧跨季用相同 series_code, 不同 season 区分) */
    private String seriesCode;

    /** 第几季 (短剧通常 season=1, 多季连续剧 season 递增) */
    private Integer season;

    /** 本剧总集数 (冗余, 与 episode_count 等价) */
    private Integer episodeTotal;

    private String title;

    private String coverUrl;

    private String description;

    /** free=剧集免费 / paid=剧集打包付费 / member_only=会员免费看 */
    private String priceType;

    /** 剧集打包解锁价 (创作者自己设置, 通常比单集逐集买便宜) */
    private BigDecimal priceAmount;

    /** 单集参考价 (展示用: "单集 ¥15 / 全集 ¥199 (共 16 集, 省 ¥41)", 不参与实际定价逻辑) */
    private BigDecimal episodeUnitPrice;

    private String currency;

    /** approved=已生效 / pending=待审核 / rejected=已驳回 */
    private String priceStatus;

    private Long priceAuditBy;

    private Date priceAuditAt;

    private String priceAuditRemark;

    private BigDecimal revenueRateSnapshot;

    private Integer episodeCount;

    private Integer totalDurationSec;

    private Long playCount;

    private Long tenantId;

    private Date createTime;

    private Date updateTime;

    private Integer delFlag;

}
