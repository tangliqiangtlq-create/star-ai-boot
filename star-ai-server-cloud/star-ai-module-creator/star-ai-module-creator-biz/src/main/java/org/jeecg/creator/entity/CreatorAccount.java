package org.jeecg.creator.entity;

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
@TableName("creator_account")
public class CreatorAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 sys_user.id */
    private Long userId;

    /** 创作者昵称 */
    private String nickname;

    private String avatarUrl;

    /** 个人简介 */
    private String bio;

    /** cn/sg/us */
    private String region;

    /** pending/active/frozen/rejected */
    private String status;

    /** 创作者等级 L1-L5 */
    private String level;

    /** 交易驱动分润比例% (单视频付费时创作者拿多少, 默认 30%) */
    private BigDecimal revenueRate;

    /** 订阅池分润比例% (订阅池场景独立配置, 可不同于 revenue_rate) */
    private BigDecimal subscriptionRevenueRate;

    /** 是否开放定价权给该创作者 (0=平台统一定价, 1=创作者自助定价) */
    private Integer pricingEnabled;

    /** self=自助定价 / platform=平台统一定价 */
    private String pricingMode;

    /** 该创作者单视频最低定价 (NULL=走平台全局默认值) */
    private BigDecimal priceAmountMin;

    /** 该创作者单视频最高定价 (NULL=走平台全局默认值) */
    private BigDecimal priceAmountMax;

    /** 该创作者剧集打包最低定价 (NULL=走平台全局默认值) */
    private BigDecimal seriesPriceMin;

    /** 该创作者剧集打包最高定价 (NULL=走平台全局默认值) */
    private BigDecimal seriesPriceMax;

    /** 该创作者全局解锁最低定价 */
    private BigDecimal globalUnlockPriceMin;

    /** 该创作者全局解锁最高定价 */
    private BigDecimal globalUnlockPriceMax;

    /** 定价在允许区间内是否自动生效 (0=需人工审核, 1=自动生效) */
    private Integer autoPublishPrice;

    /** 是否开启全局解锁 (0=关闭, 1=开启并定价) */
    private Integer globalUnlockEnabled;

    /** 全局解锁定价 (0=免费解锁所有视频, 不推荐) */
    private BigDecimal globalUnlockPrice;

    private String globalUnlockCurrency;

    /** 前端展示用: "全局解锁比逐集买省 XX%" (NULL=动态计算, 实际不参与定价逻辑) */
    private BigDecimal globalUnlockDiscountPct;

    /** 订阅/关注该创作者的粉丝数 (订阅场景下影响贡献度算法) */
    private Integer subscriberCount;

    private String kycStatus;

    private Long totalViews;

    private Long totalFavorites;

    private Integer videosPublished;

    private Long tenantId;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private Integer delFlag;

    private Integer revision;

}
