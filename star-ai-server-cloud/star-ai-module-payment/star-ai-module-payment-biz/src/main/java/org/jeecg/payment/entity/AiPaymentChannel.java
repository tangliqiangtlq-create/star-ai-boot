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
@TableName("ai_payment_channel")
public class AiPaymentChannel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 渠道唯一编码: wechat_native / stripe_checkout / paddle / bank_transfer */
    private String channelCode;

    /** 渠道展示名: 微信支付 Native / Stripe Checkout */
    private String channelName;

    /** 支付类型: pay_in / pay_out (收款 / 打款) */
    private String channelCategory;

    /** 该渠道可用的 Region: cn / sg / us / * */
    private String region;

    private Integer enabled;

    private Integer sortOrder;

    /** 渠道手续费率 (小数，如 0.006 = 0.6%) */
    private BigDecimal feeRate;

    /** 固定手续费 (叠加在 fee_rate 上) */
    private BigDecimal feeFixed;

    /** 最小收款金额 */
    private BigDecimal minAmount;

    /** 0=不限 */
    private BigDecimal maxAmount;

    /** 支持的币种列表 (逗号分隔，NULL=不限) */
    private String supportedCurrency;

    /** 加密存储的渠道配置: app_id / app_secret / private_key / webhook_secret 等 */
    private String configJson;

    /** 第三方签名头名称 (如 Wechatpay-Signature / Stripe-Signature) */
    private String signatureField;

    /** 平台在第三方配置的 webhook 地址 */
    private String webhookUrl;

    private Long tenantId;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private Integer delFlag;

    private Integer revision;

}
