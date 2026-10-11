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
@TableName("stream_withdrawal_request")
public class StreamWithdrawalRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提现单号 */
    private String requestNo;

    private Long creatorId;

    private BigDecimal amount;

    private String bankName;

    /** 脱敏存储 */
    private String bankAccount;

    private String accountName;

    private String region;

    /** pending/approved/rejected/paid/failed */
    private String status;

    private Long reviewedBy;

    private Date reviewedAt;

    private Date paidAt;

    /** 打款关联的 payment 模块订单号 */
    private String paymentRefNo;

    private String failureReason;

    private Long tenantId;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private Integer delFlag;

}
