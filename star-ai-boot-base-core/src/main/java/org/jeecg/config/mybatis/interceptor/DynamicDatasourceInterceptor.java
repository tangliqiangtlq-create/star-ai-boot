package org.jeecg.config.mybatis.interceptor;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.system.security.CommonRoleGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class DynamicDatasourceInterceptor implements HandlerInterceptor {

    private static final Set<String> DEFAULT_ALLOWED = Set.of("master");

    @Value("${jeecg.datasource.allowed-ds-codes:}")
    private String configuredAllowedCsv;

    private Set<String> allowedCodes;

    @PostConstruct
    public void init() {
        allowedCodes = new HashSet<>(DEFAULT_ALLOWED);
        if (StringUtils.isNotBlank(configuredAllowedCsv)) {
            Set<String> parsed = Arrays.stream(configuredAllowedCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
            if (!parsed.isEmpty()) {
                allowedCodes.addAll(parsed);
            }
        }
        log.info("[SHARD-DS] interceptor loaded. allowedCodes={}", allowedCodes);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestURI = request.getRequestURI();
        String dsName = request.getParameter("dsName");
        String dsKey = "master";
        if (StringUtils.isNotEmpty(dsName)) {
            if (allowedCodes.contains(dsName) || hasSuperAdminRole()) {
                dsKey = dsName;
                log.debug("[SHARD-DS] uri={} user={} switch allowed ds={}", requestURI, safeLoginId(), dsName);
            } else {
                log.warn("[SHARD-DS-BLOCK] uri={} user={} ds={} — not in whitelist or role. allowedCodes={}",
                    requestURI, safeLoginId(), dsName, allowedCodes);
            }
        }
        DynamicDataSourceContextHolder.push(dsKey);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DynamicDataSourceContextHolder.clear();
    }

    private boolean hasSuperAdminRole() {
        try {
            return CommonRoleGuard.hasAnyOwnerAdminRole();
        } catch (Exception ignore) {
            return false;
        }
    }

    private String safeLoginId() {
        try {
            Object o = StpUtil.getLoginIdDefaultNull();
            return o == null ? "anon" : String.valueOf(o);
        } catch (Exception ignore) {
            return "anon";
        }
    }
}