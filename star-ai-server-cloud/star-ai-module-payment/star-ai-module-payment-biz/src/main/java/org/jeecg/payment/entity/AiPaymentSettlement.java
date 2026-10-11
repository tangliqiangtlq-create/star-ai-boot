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
@TableName("ai_payment_settlement")
public class AiPaymentSettlement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对账日期 (T+1 跑 T 的数据) */
    private Date settleDate;

    /** 按渠道分表统计 */
    private String channelType;

    private String region;

    private String currency;

    /** 当日已支付订单数 */
    private Integer totalOrders;

    /** 当日收款总额 */
    private BigDecimal totalAmount;

    /** 当日手续费总额 */
    private BigDecimal totalFee;

    /** 当日净收入 = total_amount - total_fee */
    private BigDecimal netAmount;

    /** 当日退款单数 */
    private Integer refundCount;

    /** 当日退款总额 */
    private BigDecimal refundAmount;

    /** 云厂商账单文件ID (如 Stripe report_run_id) */
    private String cloudBillId;

    /** 云厂商账单的收款总额 (拉取后回填) */
    private BigDecimal cloudTotalAmount;

    /** 云厂商账单的手续费总额 */
    private BigDecimal cloudTotalFee;

    /** pending/done/discrepancy */
    private String status;

    /** 差异金额 (仅 discrepancy 时有值) */
    private BigDecimal discrepancyAmount;

    /** 差异详情: [{order_no, system_amount, cloud_amount}] */
    private String discrepancyDetail;

    private Date executedAt;

    /** 定时任务固定填 system */
    private Long executedBy;

    private String remark;

    private Long tenantId;

    private Date createTime;

    private Date updateTime;

}
