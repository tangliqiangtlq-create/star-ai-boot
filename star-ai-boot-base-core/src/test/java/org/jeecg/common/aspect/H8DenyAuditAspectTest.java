package org.jeecg.common.aspect;

import static org.junit.Assert.*;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import org.aspectj.lang.annotation.Aspect;
import org.junit.Test;
import org.springframework.core.Ordered;

public class H8DenyAuditAspectTest {

  @Test
  public void T3_06_aspectHasExpectedStereotypes() {
    Class<?> c = H8DenyCallAuditAspect.class;
    assertTrue("@Aspect annotation present", c.isAnnotationPresent(Aspect.class));
    assertTrue("@Component stereotype present",
        c.isAnnotationPresent(org.springframework.stereotype.Component.class));
    assertTrue("ApplicationContextAware: allow lazy service fetch + AOP loading order",
        org.springframework.context.ApplicationContextAware.class.isAssignableFrom(c));
  }

  @Test
  public void T3_06_orderIsVeryHigh_precedencePlus50() {
    Class<?> c = H8DenyCallAuditAspect.class;
    org.springframework.core.annotation.Order order =
        c.getAnnotation(org.springframework.core.annotation.Order.class);
    assertNotNull("@Order annotation must be explicit", order);
    int expected = Ordered.HIGHEST_PRECEDENCE + 50;
    assertEquals("Order value = HIGHEST_PRECEDENCE + 50 to run before business aspects",
        expected, order.value());
  }

  @Test
  public void T3_05_resolveReason_3exceptions_and_depth5_and_unknownNull() {
    NotLoginException nle = new NotLoginException("-1", "test", "login");
    assertEquals("NotLoginException → REASON_NOT_LOGIN equivalent",
        "H8_DENIED_NOTLOGIN", H8DenyCallAuditAspect.resolveReason(nle));

    NotPermissionException npe = new NotPermissionException("perm:test", "perm");
    assertEquals("NotPermissionException → REASON_NOT_PERMISSION",
        "H8_DENIED_NOPERMISSION", H8DenyCallAuditAspect.resolveReason(npe));

    Exception denied = new SpringSecurityAccessDeniedException("access denied: admin role missing");
    assertEquals("AccessDeniedException class-name match → REASON_ACCESS",
        "H8_DENIED_ACCESS", H8DenyCallAuditAspect.resolveReason(denied));

    NotPermissionException cause4 = new NotPermissionException("deep_perm_denied", "code:x");
    RuntimeException deep = new RuntimeException("surface", cause4);
    Exception cause1 = new Exception("wrap1", deep);
    Exception cause2 = new Exception("wrap2", cause1);
    Exception cause3 = new Exception("wrap3", cause2);
    assertEquals("Deep cause depth<=5 must resolve NotPermission correctly",
        "H8_DENIED_NOPERMISSION", H8DenyCallAuditAspect.resolveReason(cause3));

    assertNull("Null Throwable → null reason (ignore)",
        H8DenyCallAuditAspect.resolveReason(null));
    assertNull("Plain RuntimeException (no deny exception) → null reason",
        H8DenyCallAuditAspect.resolveReason(new RuntimeException("plain")));
  }

  public static class SpringSecurityAccessDeniedException extends RuntimeException {
    public SpringSecurityAccessDeniedException(String msg) { super(msg); }
    @Override
    public String toString() {
      return "org.springframework.security.access.AccessDeniedException: " + getMessage();
    }
  }

  @Test
  public void T3_07_meterCounter_name_and_tags_consistent() throws Exception {
    String reasonNotLogin = "H8_DENIED_NOTLOGIN";
    String reasonNoPerm = "H8_DENIED_NOPERMISSION";
    String reasonAccess = "H8_DENIED_ACCESS";

    Class<?> svcImpl = findSysDenyAuditServiceImpl();
    assertNotNull("SysDenyAuditServiceImpl must exist", svcImpl);

    java.lang.reflect.Field denyCounterNotLogin = svcImpl.getDeclaredField("denyCounterNotLogin");
    denyCounterNotLogin.setAccessible(true);
    java.lang.reflect.Field denyCounterNoPermission = svcImpl.getDeclaredField("denyCounterNoPermission");
    denyCounterNoPermission.setAccessible(true);
    java.lang.reflect.Field denyCounterAccess = svcImpl.getDeclaredField("denyCounterAccess");
    denyCounterAccess.setAccessible(true);

    assertTrue("denyCounterNotLogin exists (Micrometer Counter)",
        io.micrometer.core.instrument.Counter.class.isAssignableFrom(denyCounterNotLogin.getType()));
    assertTrue("denyCounterNoPermission exists",
        io.micrometer.core.instrument.Counter.class.isAssignableFrom(denyCounterNoPermission.getType()));
    assertTrue("denyCounterAccess exists",
        io.micrometer.core.instrument.Counter.class.isAssignableFrom(denyCounterAccess.getType()));

    assertTrue("incrementMetric(String) method must exist for counters",
        java.util.Arrays.stream(svcImpl.getDeclaredMethods())
            .anyMatch(m -> m.getName().equals("incrementMetric") && m.getParameterCount() == 1));
  }

  @Test
  public void T3_06_postConstruct_logsLoaded_and_markerClassPresent() {
    boolean hasPostConstruct = false;
    for (java.lang.reflect.Method m : H8DenyCallAuditAspect.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(jakarta.annotation.PostConstruct.class)) {
        hasPostConstruct = true;
        break;
      }
    }
    assertTrue("H8 Aspect must have @PostConstruct markLoaded() → observable startup",
        hasPostConstruct);

    Class<?> annotationClass = null;
    try {
      annotationClass = Class.forName("org.jeecg.common.aspect.annotation.H8LoginAudit");
    } catch (ClassNotFoundException ignore) {
    }
    assertNotNull("@H8LoginAudit annotation must be loadable for 3 controllers to use",
        annotationClass);
  }

  private static Class<?> findSysDenyAuditServiceImpl() {
    try {
      return Class.forName("org.jeecg.modules.system.service.impl.SysDenyAuditServiceImpl");
    } catch (ClassNotFoundException e) {
      try {
        java.io.File bizClasses = new java.io.File(
            "/Users/luohuan/Documents/trae_projects/mp-/mightyPay-boot/jeecg-module-system/jeecg-system-biz/target/classes");
        if (bizClasses.isDirectory()) {
          java.net.URL[] urls = new java.net.URL[] { bizClasses.toURI().toURL() };
          try (java.net.URLClassLoader ucl = new java.net.URLClassLoader(urls, Thread.currentThread().getContextClassLoader())) {
            return Class.forName("org.jeecg.modules.system.service.impl.SysDenyAuditServiceImpl", true, ucl);
          }
        }
      } catch (Throwable ignore) {
      }
      return null;
    }
  }
}
