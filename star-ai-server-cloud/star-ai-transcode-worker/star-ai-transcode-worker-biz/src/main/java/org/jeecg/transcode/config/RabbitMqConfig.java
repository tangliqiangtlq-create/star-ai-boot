package org.jeecg.transcode.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    public static final String EXCHANGE_VIDEO = "x.direct.video";
    public static final String QUEUE_VIDEO_UPLOADED = "q.video.uploaded";
    public static final String ROUTING_VIDEO_UPLOADED = "video.uploaded";

    @Bean
    public DirectExchange videoExchange() {
        return new DirectExchange(EXCHANGE_VIDEO, true, false);
    }

    @Bean
    public Queue videoUploadedQueue() {
        return QueueBuilder.durable(QUEUE_VIDEO_UPLOADED)
            .withArgument("x-dead-letter-exchange", "x.dlx.video")
            .withArgument("x-message-ttl", 3600000)
            .build();
    }

    @Bean
    public Binding videoUploadedBinding() {
        return BindingBuilder.bind(videoUploadedQueue()).to(videoExchange()).with(ROUTING_VIDEO_UPLOADED);
    }

    @Bean
    public DirectExchange videoDlxExchange() {
        return new DirectExchange("x.dlx.video", true, false);
    }
}
