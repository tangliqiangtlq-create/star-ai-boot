package org.jeecg.payment.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RabbitMqConfig {
    public static final String EXCHANGE = "x.direct.payment";
    public static final String QUEUE = "q.payment.success";
    public static final String ROUTING = "payment.success";
    @Bean public DirectExchange exchange() { return new DirectExchange(EXCHANGE, true, false); }
    @Bean public Queue queue() { return QueueBuilder.durable(QUEUE).build(); }
    @Bean public Binding binding() { return BindingBuilder.bind(queue()).to(exchange()).with(ROUTING); }
}
