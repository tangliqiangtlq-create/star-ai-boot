package org.jeecg.payment.entity;

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
@TableName("ai_payment_order")
public class AiPaymentOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 内部支付订单号 (雪花ID，唯一) */
    private String orderNo;

    /** 业务侧订单号 (stream/billing/creator) */
    private String businessOrderNo;

    /** 业务类型: stream_video_purchase / billing_recharge / creator_withdrawal / creator_earnings_payout */
    private String businessType;

    /** 付款用户 */
    private Long userId;

    /** 收款创作者 (仅提现/分润打款场景) */
    private Long creatorId;

    /** 渠道: wechat_native / wechat_jsapi / alipay_pc / alipay_h5 / stripe_checkout / stripe_card / paddle / bank_transfer */
    private String channelType;

    /** 第三方流水号 (支付成功后回填) */
    private String channelTradeNo;

    /** 支付参数 (二维码URL / Stripe Session URL / JSAPI 参数 JSON) */
    private String payUrl;

    /** 订单金额 (原币) */
    private BigDecimal amount;

    private String currency;

    /** 渠道手续费 (支付完成后回填) */
    private BigDecimal feeAmount;

    /** 净收入 = amount - fee_amount */
    private BigDecimal netAmount;

    /** created/paying/paid/closed/refunding/refunded/failed */
    private String status;

    private Date paidAt;

    private Date closedAt;

    /** 订单过期时间 (默认 30min) */
    private Date expiresAt;

    /** 关闭原因: timeout/user_cancel/refund_all */
    private String closeReason;

    /** 业务方回调地址 (支付成功/退款成功后 POST) */
    private String notifyUrl;

    /** 已通知次数 */
    private Integer notifyCount;

    private Date lastNotifyAt;

    /** 上次通知 HTTP 状态 + 响应体摘要 */
    private String lastNotifyResult;

    /** cn/sg/us */
    private String region;

    private String remark;

    private Long tenantId;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private Integer delFlag;

    private Integer revision;

}
