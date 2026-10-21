package org.jeecg.creator;

import org.jeecg.stream.service.impl.StreamCreatorEarningsServiceImpl;
import org.jeecg.stream.service.impl.StreamRevenueRecordServiceImpl;
import org.jeecg.stream.service.impl.StreamVideoCollectionServiceImpl;
import org.jeecg.stream.service.impl.StreamVideoServiceImpl;
import org.jeecg.stream.service.impl.StreamWithdrawalRequestServiceImpl;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("org.jeecg.**.mapper")
@Import({
    StreamVideoServiceImpl.class,
    StreamVideoCollectionServiceImpl.class,
    StreamWithdrawalRequestServiceImpl.class,
    StreamCreatorEarningsServiceImpl.class,
    StreamRevenueRecordServiceImpl.class
})
public class CreatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreatorApplication.class, args);
    }
}
