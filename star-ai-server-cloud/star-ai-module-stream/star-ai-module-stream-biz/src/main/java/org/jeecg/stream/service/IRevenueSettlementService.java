package org.jeecg.stream.service;
import org.jeecg.stream.dto.PaymentSuccessMsg;

public interface IRevenueSettlementService {
    void settle(PaymentSuccessMsg msg);
}
