package org.jeecg.payment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.payment.entity.AiPaymentSettlement;
import org.jeecg.payment.mapper.AiPaymentSettlementMapper;
import org.jeecg.payment.service.IAiPaymentSettlementService;
import org.springframework.stereotype.Service;

@Service
public class AiPaymentSettlementServiceImpl extends ServiceImpl<AiPaymentSettlementMapper, AiPaymentSettlement> implements IAiPaymentSettlementService {
}
