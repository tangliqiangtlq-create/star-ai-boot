package org.jeecg.payment.client.impl;
import org.jeecg.payment.client.PaymentClient;
import org.jeecg.payment.dto.PaymentCreateReq;
import org.jeecg.payment.dto.PaymentCreateResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Slf4j
@Component
@ConditionalOnProperty(name="payment.channel", havingValue="mock", matchIfMissing=true)
public class MockPaymentClient implements PaymentClient {
    @Override
    public PaymentCreateResp createPayment(PaymentCreateReq req) {
        String payNo = "PAY" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0,6).toUpperCase();
        log.info("[MOCK] 创建支付 orderNo=" + req.getOrderNo() + " pay=" + payNo + " amt=" + req.getAmount());
        PaymentCreateResp r = new PaymentCreateResp();
        r.setPaymentOrderNo(payNo);
        r.setPayUrl("mock://pay?o=" + payNo);
        r.setPayCode(payNo);
        r.setChannel("mock");
        r.setStatus("pending");
        return r;
    }
    @Override public String getChannelCode() { return "mock"; }
}
