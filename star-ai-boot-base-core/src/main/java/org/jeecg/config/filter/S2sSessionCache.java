package org.jeecg.config.filter;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.jeecg.common.system.vo.LoginUser;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class S2sSessionCache {

  private static final Cache<String, LoginUser> S2S_LU =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build();

  public static LoginUser get(String moduleId) {
    if (moduleId == null) {
      return null;
    }
    return S2S_LU.getIfPresent("s2s:" + moduleId);
  }

  public static void put(String moduleId, LoginUser lu) {
    if (moduleId == null || lu == null) {
      return;
    }
    S2S_LU.put("s2s:" + moduleId, lu);
  }

  public static void bustAll() {
    S2S_LU.invalidateAll();
  }

  @EventListener(ContextRefreshedEvent.class)
  public void onContextRefreshed() {
    bustAll();
  }
}
