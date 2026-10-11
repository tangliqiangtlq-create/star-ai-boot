//package org.jeecg.fallback;

import org.springframework.web.bind.annotation.PostMapping;
//
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import reactor.core.publisher.Mono;
//
///**
// * Response timeout circuit breaker handler [deprecated after springboot2.6.6 upgrade]
// *
// * @author zyf
// */
//@RestController
//public class FallbackController {
//
//    /**
//     * Global circuit breaker handler
//     * @return
//     */
//    @PostMapping("/fallback")
//    public Mono<String> fallback() {
//        return Mono.just("Request timeout, please try again later!");
//    }
//
//    /**
//     * Demo circuit breaker handler
//     * @return
//     */
//    @PostMapping("/demo/fallback")
//    public Mono<String> fallback2() {
//        return Mono.just("Request timeout, please try again later!");
//    }
//}
