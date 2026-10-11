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
@TableName("ai_payment_refund")
public class AiPaymentRefund implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退款单号 (雪花ID) */
    private String refundNo;

    /** 关联 ai_payment_order.order_no */
    private String paymentOrderNo;

    /** 业务侧原订单号 */
    private String businessOrderNo;

    private String businessType;

    /** 与支付单一致的渠道 */
    private String channelType;

    /** 第三方退款流水号 */
    private String channelRefundNo;

    /** 本次退款金额 (原币) */
    private BigDecimal refundAmount;

    private String currency;

    /** 该支付单累计已退款金额 (含本次) */
    private BigDecimal totalRefunded;

    /** 退款原因 */
    private String reason;

    /** full 全额 / partial 部分 / auto 自动 (系统触发) */
    private String refundType;

    /** created/refunding/success/failed */
    private String status;

    /** 申请退款的用户 (业务方) */
    private Long requestedBy;

    /** 审核退款的管理员 */
    private Long reviewedBy;

    private Date reviewedAt;

    private Date successAt;

    /** 退款失败原因 (渠道返回) */
    private String failureReason;

    /** 业务方退款回调地址 */
    private String notifyUrl;

    private Integer notifyCount;

    private Long tenantId;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private Integer delFlag;

    private Integer revision;

}
