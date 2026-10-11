//package org.jeecg.config.mybatis;
//
//import lombok.extern.slf4j.Slf4j;
//
///**
// * Multi-tenant tenant_id holder
// * @author: jeecg-boot
// */
//@Slf4j
//public class TenantContext {
//    private static ThreadLocal<String> currentTenant = new ThreadLocal<>();
//
//    public static void setTenant(String tenant) {
//        log.debug(" setting tenant to " + tenant);
//        currentTenant.set(tenant);
//    }
//
//    public static String getTenant() {
//        return currentTenant.get();
//    }
//
//    public static void clear(){
//        currentTenant.remove();
//    }
//}
