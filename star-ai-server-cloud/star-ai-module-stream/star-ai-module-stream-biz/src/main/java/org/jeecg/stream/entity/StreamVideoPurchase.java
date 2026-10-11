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
@TableName("stream_video_purchase")
public class StreamVideoPurchase implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 单视频购买时填值, 剧集解锁 NULL */
    private Long videoId;

    /** 剧集解锁时填值, 单视频购买 NULL */
    private Long collectionId;

    private String unlockScope;

    /** stream 业务订单号 */
    private String orderNo;

    /** payment 模块订单号 (gift/member 场景可 NULL) */
    private String paymentOrderNo;

    /** purchase=付费购买 / gift=赠送 / member=会员权益 (member 一般实时校验, 不一定插本表) */
    private String accessType;

    /** 用户实际支付金额 (0=免费/赠送) */
    private BigDecimal paidAmount;

    private String currency;

    /** NULL=永久有效 (付费购买默认永久, 会员权益通常有期限) */
    private Date expiresAt;

    private Date createTime;

}
