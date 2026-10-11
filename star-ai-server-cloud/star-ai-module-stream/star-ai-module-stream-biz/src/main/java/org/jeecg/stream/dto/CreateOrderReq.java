package org.jeecg.stream.dto;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateOrderReq {
    private Long userId;
    private Long videoId;
    private Long creatorId;
    private String orderType;
    private BigDecimal amount;
    private String currency;
}
