package org.jeecg.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.payment.config.PaymentConfig;
import org.jeecg.payment.config.RabbitMqConfig;
import org.jeecg.payment.dto.PaymentCreateReq;
import org.jeecg.payment.dto.PaymentCreateResp;
import org.jeecg.payment.entity.AiPaymentOrder;
import org.jeecg.payment.mapper.AiPaymentOrderMapper;
import org.jeecg.payment.service.IPaymentOrderBizService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderBizServiceImpl implements IPaymentOrderBizService {

    private final AiPaymentOrderMapper orderMapper;
    private final PaymentConfig paymentConfig;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mock-payment-callback"); t.setDaemon(true); return t;
    });

    @Override
    @Transactional
    public PaymentCreateResp create(PaymentCreateReq req) {
        AiPaymentOrder exist = getByBizOrderNo(req.getOrderNo());
        if (exist != null) {
            PaymentCreateResp resp = new PaymentCreateResp();
            resp.setPaymentOrderNo(exist.getOrderNo());
            resp.setPayUrl(exist.getPayUrl());
            resp.setChannel(exist.getChannelType());
            resp.setStatus(exist.getStatus());
            return resp;
        }

        PaymentCreateResp resp = paymentConfig.getActiveClient().createPayment(req);

        AiPaymentOrder order = new AiPaymentOrder();
        order.setOrderNo(resp.getPaymentOrderNo());
        order.setBusinessOrderNo(req.getOrderNo());
        order.setBusinessType("stream_video_purchase");
        order.setUserId(req.getUserId());
        order.setChannelType(resp.getChannel());
        order.setAmount(req.getAmount());
        order.setCurrency(req.getCurrency() != null ? req.getCurrency() : "CNY");
        order.setPayUrl(resp.getPayUrl());
        order.setStatus("created");
        order.setNotifyUrl(req.getNotifyUrl());
        order.setCreateTime(new Date());
        order.setExpiresAt(new Date(System.currentTimeMillis() + 30 * 60 * 1000L));
        orderMapper.insert(order);

        if ("mock".equals(resp.getChannel())) {
            scheduler.schedule(() -> {
                try {
                    log.info("[MOCK] 自动回调: paymentOrderNo=" + resp.getPaymentOrderNo());
                    markPaid(resp.getPaymentOrderNo());
                    sendSuccessMq(resp.getPaymentOrderNo(), req);
                } catch (Exception e) { log.error("mock callback fail", e); }
            }, 3, TimeUnit.SECONDS);
        }
        log.info("创建支付: biz=" + req.getOrderNo() + " pay=" + resp.getPaymentOrderNo() + " ch=" + resp.getChannel());
        return resp;
    }

    private void sendSuccessMq(String paymentOrderNo, PaymentCreateReq req) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("orderNo", req.getOrderNo());
            m.put("paymentOrderNo", paymentOrderNo);
            m.put("userId", req.getUserId());
            m.put("orderAmount", req.getAmount());
            m.put("status", "paid");
            String json = objectMapper.writeValueAsString(m);
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING, json);
            log.info("已发送 payment.success MQ: " + req.getOrderNo());
        } catch (Exception e) { log.error("send mq fail", e); }
    }

    @Override
    public AiPaymentOrder getByPaymentOrderNo(String paymentOrderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<AiPaymentOrder>()
            .eq(AiPaymentOrder::getOrderNo, paymentOrderNo));
    }

    @Override
    public AiPaymentOrder getByBizOrderNo(String bizOrderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<AiPaymentOrder>()
            .eq(AiPaymentOrder::getBusinessOrderNo, bizOrderNo));
    }

    @Override
    @Transactional
    public void markPaid(String paymentOrderNo) {
        AiPaymentOrder o = getByPaymentOrderNo(paymentOrderNo);
        Assert.notNull(o, "支付订单不存在");
        o.setStatus("paid");
        o.setPaidAt(new Date());
        o.setNetAmount(o.getAmount());
        o.setFeeAmount(BigDecimal.ZERO);
        orderMapper.updateById(o);
    }
}
