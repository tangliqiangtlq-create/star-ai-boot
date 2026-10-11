package org.jeecg.stream.dto;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentSuccessMsg {
    private String orderNo;
    private String paymentOrderNo;
    private Long userId;
    private Long videoId;
    private Long creatorId;
    private BigDecimal orderAmount;
    private BigDecimal revenueRateSnapshot;
    private String revenueSource;
    private String paymentChannel;
}
