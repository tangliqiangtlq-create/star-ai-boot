package org.jeecg.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevent swiping SMS interface (only for binding mobile phone number template:SMS_175430166）
 * 
 * 1、sameIP，1Sending text messages within minutes is not allowed to exceed5times (reset every minute for eachIPnumber of requests)
 * 2、sameIP，1Send text messages within minutes for more than20times, entered the blacklist, and was not allowed to use the SMS interface.
 * 
 * 3、SMS interface signature and timestamp
 *  Involving interfaces:
 *  /sys/sms
 *  /desform/api/sendVerifyCode
 *  /sys/sendChangePwdSms
 */
@Slf4j
public class DySmsLimit {

    // 1Maximum number of text messages sent in a minute (singleIP）
    private static final int MAX_MESSAGE_PER_MINUTE = 5;
    // 1minute
    private static final int MILLIS_PER_MINUTE = 60000;
    // The maximum number of text messages for the alarm line in one minute. If it exceeds the number, it will be blacklisted (singleIP）
    private static final int MAX_TOTAL_MESSAGE_PER_MINUTE = 20;

    private static ConcurrentHashMap<String, Long> ipLastRequestTime = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Integer> ipRequestCount = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Boolean> ipBlacklist = new ConcurrentHashMap<>();

    /**
     * @param ip Request to send text messageIPaddress
     * @return
     */
    public static boolean canSendSms(String ip) {
        long currentTime = System.currentTimeMillis();
        long lastRequestTime = ipLastRequestTime.getOrDefault(ip, 0L);
        int requestCount = ipRequestCount.getOrDefault(ip, 0);
        log.info("IP：{}, Msg requestCount：{} ", ip, requestCount);

        if (ipBlacklist.getOrDefault(ip, false)) {
            // ifIPIf you are in the blacklist, you are prohibited from sending text messages.
            log.error("IP: {}, added to blacklist; SMS requests are blocked!", ip);
            return false;
        }

        if (currentTime - lastRequestTime >= MILLIS_PER_MINUTE) {
            // If more than one minute has passed since the last request, reset the count
            ipRequestCount.put(ip, 1);
            ipLastRequestTime.put(ip, currentTime);
            return true;
        } else {
            // If it's been less than a minute since the last request
            ipRequestCount.put(ip, requestCount + 1);
            if (requestCount < MAX_MESSAGE_PER_MINUTE) {
                // If the number of requests is less than5times, allowing text messages to be sent
                return true;
            } else if (requestCount >= MAX_TOTAL_MESSAGE_PER_MINUTE) {
                // If the number of requests exceeds the number of alarm line text messages, theIPAdd to blacklist
                ipBlacklist.put(ip, true);
                return false;
            } else {
                log.error("IP: {}, SMS requests exceed 5 within 1 minute; please try again later!", ip);
                return false;
            }
        }
    }

    /**
     * The quantity will be cleared after the picture QR code is successfully verified.
     * 
     * @param ip IPaddress
     */
    public static void clearSendSmsCount(String ip) {
        long currentTime = System.currentTimeMillis();
        ipRequestCount.put(ip, 0);
        ipLastRequestTime.put(ip, currentTime);
    }
    
//    public static void main(String[] args) {
//        String ip = "192.168.1.1";
//        for (int i = 1; i < 50; i++) {
//            if (canSendSms(ip)) {
//                log.debug("Send SMS successfully");
//            } else {
//                //log.debug("Exceed SMS limit for IP " + ip);
//            }
//        }
//
//        log.debug(String.valueOf(ipLastRequestTime));
//        log.debug(String.valueOf(ipRequestCount));
//        log.debug(String.valueOf(ipBlacklist));
//    }
}
