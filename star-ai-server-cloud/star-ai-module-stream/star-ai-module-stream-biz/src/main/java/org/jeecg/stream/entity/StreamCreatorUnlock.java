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
@TableName("stream_creator_unlock")
public class StreamCreatorUnlock implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 解锁哪个创作者的全部视频 */
    private Long creatorId;

    /** stream 业务订单号 */
    private String orderNo;

    /** payment 模块订单号 (gift 可 NULL) */
    private String paymentOrderNo;

    /** purchase=付费解锁 / gift=运营赠送 */
    private String accessType;

    private BigDecimal paidAmount;

    private String currency;

    /** NULL=永久 (付费解锁默认永久) */
    private Date expiresAt;

    private Long tenantId;

    private Date createTime;

}
