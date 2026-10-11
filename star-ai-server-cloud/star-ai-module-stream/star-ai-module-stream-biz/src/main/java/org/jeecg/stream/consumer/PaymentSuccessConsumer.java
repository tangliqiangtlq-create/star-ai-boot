package org.jeecg.stream.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.jeecg.stream.config.RabbitMqConfig;
import org.jeecg.stream.dto.PaymentSuccessMsg;
import org.jeecg.stream.service.IRevenueSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSuccessConsumer {

    private final ObjectMapper objectMapper;
    private final IRevenueSettlementService settlementService;
    private final StringRedisTemplate redisTemplate;

    @RabbitListener(queues = "#{q.payment.success}")
    public void onPaymentSuccess(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String body = new String(message.getBody());

        try {
PaymentSuccessMsg msg = objectMapper.readValue(body, PaymentSuccessMsg.class);
String lockKey = "settle:lock:" + msg.getOrderNo();
Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 24, TimeUnit.HOURS);
if (locked == null || !locked) {
    log.warn("幂等跳过 orderNo=" + msg.getOrderNo());
    channel.basicAck(tag, false); return;
}
log.info("收到 payment.success: orderNo=" + msg.getOrderNo());
settlementService.settle(msg);
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("处理 payment.success 失败 body=" + body, e);
            try {
                channel.basicNack(tag, false, false);
            } catch (Exception ex) {
                log.error("basicNack 失败", ex);
            }
        }
    }
}
