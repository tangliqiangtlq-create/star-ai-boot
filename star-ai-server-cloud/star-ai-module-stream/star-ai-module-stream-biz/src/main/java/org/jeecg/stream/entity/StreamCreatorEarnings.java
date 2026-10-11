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
@TableName("stream_creator_earnings")
public class StreamCreatorEarnings implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long creatorId;

    /** 累计总收入 */
    private BigDecimal totalEarned;

    /** 可提现余额 */
    private BigDecimal availableBalance;

    /** 冻结中 (已申请提现未打款) */
    private BigDecimal frozenBalance;

    /** 累计已提现 */
    private BigDecimal totalWithdrawn;

    private Date lastUpdatedAt;

}
