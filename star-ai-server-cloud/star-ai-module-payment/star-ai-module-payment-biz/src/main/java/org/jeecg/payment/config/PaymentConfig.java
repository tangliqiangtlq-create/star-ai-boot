package org.jeecg.payment.config;
import org.jeecg.payment.client.PaymentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;
@Component
@RequiredArgsConstructor
public class PaymentConfig {
    private final List<PaymentClient> clients;
    @Value("${payment.channel:mock}")
    private String activeChannel;
    public PaymentClient getActiveClient() {
        return clients.stream()
            .filter(c -> activeChannel.equals(c.getChannelCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No client for channel=" + activeChannel));
    }
}
