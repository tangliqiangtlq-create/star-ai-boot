package org.jeecg.payment.service;

import org.jeecg.payment.dto.PaymentCreateReq;
import org.jeecg.payment.dto.PaymentCreateResp;
import org.jeecg.payment.entity.AiPaymentOrder;

public interface IPaymentOrderBizService {
    PaymentCreateResp create(PaymentCreateReq req);
    AiPaymentOrder getByPaymentOrderNo(String paymentOrderNo);
    AiPaymentOrder getByBizOrderNo(String bizOrderNo);
    void markPaid(String paymentOrderNo);
}
