package org.jeecg.payment.client.impl;
import org.jeecg.payment.client.PaymentClient;
import org.jeecg.payment.dto.PaymentCreateReq;
import org.jeecg.payment.dto.PaymentCreateResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@ConditionalOnProperty(name="payment.channel", havingValue="stripe")
public class StripeCheckoutPaymentClient implements PaymentClient {
    @Override public PaymentCreateResp createPayment(PaymentCreateReq req) {
        log.warn("[Stripe] Phase 2 才接真实 Stripe, 当前 mock");
        PaymentCreateResp r = new PaymentCreateResp();
        r.setPaymentOrderNo("STRPMOCK" + System.currentTimeMillis());
        r.setPayUrl("mock://stripe?o=" + r.getPaymentOrderNo());
        r.setPayCode(r.getPaymentOrderNo()); r.setChannel("stripe"); r.setStatus("pending");
        return r;
    }
    @Override public String getChannelCode() { return "stripe"; }
}
