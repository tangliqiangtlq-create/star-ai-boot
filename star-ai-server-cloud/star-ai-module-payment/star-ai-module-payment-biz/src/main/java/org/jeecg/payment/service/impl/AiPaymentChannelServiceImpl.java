package org.jeecg.payment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.payment.entity.AiPaymentChannel;
import org.jeecg.payment.mapper.AiPaymentChannelMapper;
import org.jeecg.payment.service.IAiPaymentChannelService;
import org.springframework.stereotype.Service;

@Service
public class AiPaymentChannelServiceImpl extends ServiceImpl<AiPaymentChannelMapper, AiPaymentChannel> implements IAiPaymentChannelService {
}
