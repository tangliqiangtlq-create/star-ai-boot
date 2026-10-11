package org.jeecg.transcode.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;
import com.rabbitmq.client.Channel;
import org.jeecg.transcode.dto.VideoUploadedMsg;
import org.jeecg.transcode.service.ITranscodeService;
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
public class VideoUploadedConsumer {

    private final ObjectMapper objectMapper;
    private final ITranscodeService transcodeService;
    private final StringRedisTemplate redisTemplate;

    @RabbitListener(queues = "#{'q.video.uploaded'}")
    public void onVideoUploaded(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String body = new String(message.getBody());

        try {
VideoUploadedMsg msg = objectMapper.readValue(body, VideoUploadedMsg.class);
String lockKey = "transcode:lock:" + msg.getVideoId();
Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 24, TimeUnit.HOURS);
if (locked == null || !locked) {
    log.warn("幂等跳过 videoId=" + msg.getVideoId());
    channel.basicAck(tag, false); return;
}
log.info("收到 video.uploaded: videoId=" + msg.getVideoId() + " bucket=" + msg.getBucket());
transcodeService.transcode(msg);
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("处理 video.uploaded 失败 body=" + body, e);
            try {
                channel.basicNack(tag, false, false);
            } catch (Exception ex) {
                log.error("basicNack 失败", ex);
            }
        }
    }
}
