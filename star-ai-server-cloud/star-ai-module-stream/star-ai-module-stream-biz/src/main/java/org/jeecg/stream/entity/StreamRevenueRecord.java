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
@TableName("stream_revenue_record")
public class StreamRevenueRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分润来源: DIRECT=单视频付费 (交易驱动) / SUBSCRIPTION_POOL=会员订阅池分摊 (月度跑批) */
    private String revenueSource;

    /** 订阅池分摊批次号 (SUBSCRIPTION_POOL 时必填, 格式 YYYY-MM_region; DIRECT 时 NULL) */
    private String poolBatchNo;

    /** 关联 ai_payment_order (DIRECT 时必填, SUBSCRIPTION_POOL 时 NULL) */
    private String paymentOrderNo;

    /** stream 业务订单号 (DIRECT 时填 stream_order; SUBSCRIPTION_POOL 时填 pool_batch_no) */
    private String businessOrderNo;

    /** 关联视频 (DIRECT 时必填; SUBSCRIPTION_POOL 时 NULL, 订阅池分摊不按单视频粒度) */
    private Long videoId;

    private Long creatorId;

    /** 订单总额 (DIRECT=用户实付; SUBSCRIPTION_POOL=该创作者从池中分得金额) */
    private BigDecimal totalAmount;

    /** 平台抽成 */
    private BigDecimal platformShare;

    /** 创作者分润 (= total_amount - platform_share) */
    private BigDecimal creatorShare;

    /** 当时使用的分润比例 (DIRECT 取 creator_account.revenue_rate; SUBSCRIPTION_POOL 取 creator_account.subscription_revenue_rate) */
    private BigDecimal revenueRate;

    /** 订阅池贡献百分比 (SUBSCRIPTION_POOL 时填 creator_monthly_contribution.contribution_pct; DIRECT 时 NULL) */
    private BigDecimal contributionPct;

    /** pending/settled/withdrawn */
    private String settleStatus;

    private String settleBatchNo;

    private Long tenantId;

    private Date createTime;

}
