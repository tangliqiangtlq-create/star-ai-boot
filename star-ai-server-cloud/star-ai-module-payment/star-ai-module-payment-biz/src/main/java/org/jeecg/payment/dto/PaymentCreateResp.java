package org.jeecg.payment.dto;
import lombok.Data;
@Data
public class PaymentCreateResp {
    private String paymentOrderNo;
    private String payUrl;
    private String payCode;
    private String channel;
    private String status;
}
