package org.jeecg.payment.dto;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class PaymentCreateReq {
    private String orderNo;
    private String orderType;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private String channel;
    private String subject;
    private String notifyUrl;
}
