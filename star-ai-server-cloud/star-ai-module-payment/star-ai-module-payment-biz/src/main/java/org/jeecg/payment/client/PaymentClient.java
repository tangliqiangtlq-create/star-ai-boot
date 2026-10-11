package org.jeecg.payment.client;
import org.jeecg.payment.dto.PaymentCreateReq;
import org.jeecg.payment.dto.PaymentCreateResp;
public interface PaymentClient {
    PaymentCreateResp createPayment(PaymentCreateReq req);
    String getChannelCode();
}
