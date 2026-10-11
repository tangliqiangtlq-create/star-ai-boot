package org.jeecg.stream.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    public static final String EXCHANGE_PAYMENT = "x.direct.payment";
    public static final String QUEUE_PAYMENT_SUCCESS = "q.payment.success";
    public static final String ROUTING_PAYMENT_SUCCESS = "payment.success";

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(EXCHANGE_PAYMENT, true, false);
    }

    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(QUEUE_PAYMENT_SUCCESS).build();
    }

    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder.bind(paymentSuccessQueue()).to(paymentExchange()).with(ROUTING_PAYMENT_SUCCESS);
    }
}
