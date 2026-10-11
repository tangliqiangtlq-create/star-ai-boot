package org.jeecg.modules.system.passwordreset.helper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.system.passwordreset.config.PasswordResetProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Client IP resolver: take X-Forwarded-For right-to-left by trusted hops,
 * Also keep raw X-Forwarded-For for audit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";

    private final PasswordResetProperties props;

    /** Return {clientIp, xForwardedForRaw}; fallback remoteAddr if parse fails */
    public ClientIp resolve(HttpServletRequest request) {
        if (request == null) return new ClientIp("", "");
        String remoteAddr = request.getRemoteAddr();
        String xff = request.getHeader(X_FORWARDED_FOR);
        String xri = request.getHeader(X_REAL_IP);

        String clientIp = remoteAddr;
        if (StringUtils.hasText(xff)) {
            List<String> ips = parseXffList(xff);
            if (!ips.isEmpty()) {
                int hops = props.getTrustedProxyHops();
                if (hops < 0) hops = 0;
                if (hops >= ips.size()) hops = ips.size() - 1;
                clientIp = ips.get(ips.size() - 1 - hops);
            }
        } else if (StringUtils.hasText(xri)) {
            clientIp = xri.trim();
        }
        if (!isValidIp(clientIp)) {
            clientIp = remoteAddr;
        }
        // Map local hostname to 127.0.0.1
        if ("0:0:0:0:0:0:0:1".equals(clientIp)) clientIp = "127.0.0.1";
        if (!isValidIp(clientIp)) {
            try {
                InetAddress addr = InetAddress.getByName(clientIp);
                clientIp = addr.getHostAddress();
            } catch (UnknownHostException e) {
                log.warn("ClientIpResolver resolution failed clientIp=[{}] remoteAddr=[{}]", clientIp, remoteAddr);
            }
        }
        return new ClientIp(clientIp, xff == null ? "" : xff);
    }

    private static List<String> parseXffList(String xff) {
        if (!StringUtils.hasText(xff)) return Collections.emptyList();
        String[] arr = xff.split(",");
        for (int i = 0; i < arr.length; i++) arr[i] = arr[i].trim();
        return Arrays.asList(arr);
    }

    private static boolean isValidIp(String s) {
        if (!StringUtils.hasText(s)) return false;
        return IPV4.matcher(s).matches() || IPV6.matcher(s).matches();
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ClientIp {
        private String clientIp;
        private String xForwardedForRaw;
    }
}
