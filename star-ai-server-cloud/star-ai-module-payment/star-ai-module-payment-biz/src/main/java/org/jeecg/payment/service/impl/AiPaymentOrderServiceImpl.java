package org.jeecg.payment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.payment.entity.AiPaymentOrder;
import org.jeecg.payment.mapper.AiPaymentOrderMapper;
import org.jeecg.payment.service.IAiPaymentOrderService;
import org.springframework.stereotype.Service;

@Service
public class AiPaymentOrderServiceImpl extends ServiceImpl<AiPaymentOrderMapper, AiPaymentOrder> implements IAiPaymentOrderService {
}
