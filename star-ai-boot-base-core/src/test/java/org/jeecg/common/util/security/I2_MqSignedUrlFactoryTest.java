package org.jeecg.common.util.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONObject;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.jeecg.common.system.passwordreset.PasswordResetLinkFacade;
import org.jeecg.common.system.passwordreset.vo.PasswordResetLinkVO;
import org.jeecg.common.util.SpringContextUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

class I2_MqSignedUrlFactoryTest {

  private static final String PORTAL_ORIGIN = "https://app.example.com";
  private static final String SIGN_SECRET = "unit-test-sign-secret";

  @AfterEach
  void tearDown() throws Exception {
    setApplicationContext(null);
  }

  private static void setApplicationContext(GenericApplicationContext ctx) throws Exception {
    Field f = SpringContextUtils.class.getDeclaredField("applicationContext");
    f.setAccessible(true);
    f.set(null, ctx);
  }

  /** Install a Spring context whose facade/whitelist config mimics the email consumer runtime. */
  private static void installContext(PasswordResetLinkFacade facade) throws Exception {
    Map<String, Object> props = new HashMap<>();
    props.put("jeecg.password-reset.portal-origin", PORTAL_ORIGIN);
    props.put("jeecg.mq.message.sign.secret", SIGN_SECRET);
    GenericApplicationContext ctx = new GenericApplicationContext();
    ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", props));
    ctx.registerBean(PasswordResetLinkFacade.class, () -> facade);
    ctx.refresh();
    setApplicationContext(ctx);
  }

  @Test
  void applyLinkToJson_setsThreeFieldsAndRemovesLegacy() {
    PasswordResetLinkVO link =
        new PasswordResetLinkVO()
            .setToken("tok-abc123xyz")
            .setExpireTime(LocalDateTime.of(2026, 10, 1, 12, 0, 0));
    JSONObject mq = new JSONObject();
    mq.set("setPasswordUrl", "https://evil.example.com/phish");
    mq.set("inviteLinkEnabled", true);
    MqSignedUrlFactory.applyLinkToJson(mq, link);

    assertEquals("tok-abc123xyz", mq.getStr("pwResetLinkId"));
    assertNotNull(mq.getStr("pwResetSign"));
    assertTrue(mq.getStr("pwResetExpire").contains("2026"));
    assertNull(mq.getStr("setPasswordUrl"), "legacy full URL stripped from payload");
    assertNull(mq.get("inviteLinkEnabled"), "inviteLinkEnabled not passed (decided by backend)");
  }

  @Test
  void applyLinkToJson_nullLink_setsEmptyFields() {
    JSONObject mq = new JSONObject();
    MqSignedUrlFactory.applyLinkToJson(mq, null);
    assertEquals("", mq.getStr("pwResetLinkId"));
    assertEquals("", mq.getStr("pwResetExpire"));
    assertEquals("", mq.getStr("pwResetSign"));
  }

  @Test
  void createSignedLinkMeta_nullLink_emptyValues() {
    MqSignedUrlFactory.LinkSigned s = MqSignedUrlFactory.createSignedLinkMeta(null);
    assertNull(s.linkId);
    assertNull(s.sign);
  }

  @Test
  void resolveAndVerifyLink_nullData_disabledByDefault() {
    MqSignedUrlFactory.ResolvedLink r = MqSignedUrlFactory.resolveAndVerifyLink(null);
    assertFalse(r.inviteLinkEnabled);
    assertNull(r.setPasswordUrl);
  }

  @Test
  void resolveAndVerifyLink_legacyFallbackUrl_whitelistAccepted() {
    JSONObject d = new JSONObject();
    d.set("setPasswordUrl", "https://app.example.com/pwreset?t=ok");
    d.set("linkExpireTime", "2026-10-01 12:00:00");
    MqSignedUrlFactory.ResolvedLink r = MqSignedUrlFactory.resolveAndVerifyLink(d);
    assertTrue(r.inviteLinkEnabled, "legacy URL path also accepted during 2-week SLA");
    assertEquals("2026-10-01 12:00:00", r.linkExpireTime);
  }

  @Test
  void isWhitelistedResetUrl_emptyOrBlank_false() {
    assertFalse(MqSignedUrlFactory.isWhitelistedResetUrl(""));
    assertFalse(MqSignedUrlFactory.isWhitelistedResetUrl(null));
  }

  @Test
  void resolveAndVerifyLink_newTripleFormat_resolvesUrlViaFacade() throws Exception {
    PasswordResetLinkFacade facade = mock(PasswordResetLinkFacade.class);
    when(facade.getLinkByTokenSafe("tok-abc123xyz"))
        .thenReturn(
            new PasswordResetLinkVO()
                .setToken("tok-abc123xyz")
                .setResetUrl(PORTAL_ORIGIN + "/reset-password/tok-abc123xyz"));
    installContext(facade);

    JSONObject mq = new JSONObject();
    MqSignedUrlFactory.applyLinkToJson(
        mq,
        new PasswordResetLinkVO()
            .setToken("tok-abc123xyz")
            .setExpireTime(LocalDateTime.of(2026, 10, 1, 12, 0, 0)));

    MqSignedUrlFactory.ResolvedLink r = MqSignedUrlFactory.resolveAndVerifyLink(mq);
    assertTrue(r.inviteLinkEnabled, "new signed format must resolve to a URL");
    assertEquals(PORTAL_ORIGIN + "/reset-password/tok-abc123xyz", r.setPasswordUrl);
  }

  @Test
  void resolveAndVerifyLink_newTripleFormat_notPendingLink_returnsDisabled() throws Exception {
    PasswordResetLinkFacade facade = mock(PasswordResetLinkFacade.class);
    when(facade.getLinkByTokenSafe("tok-abc123xyz")).thenReturn(null);
    installContext(facade);

    JSONObject mq = new JSONObject();
    MqSignedUrlFactory.applyLinkToJson(
        mq,
        new PasswordResetLinkVO()
            .setToken("tok-abc123xyz")
            .setExpireTime(LocalDateTime.of(2026, 10, 1, 12, 0, 0)));

    MqSignedUrlFactory.ResolvedLink r = MqSignedUrlFactory.resolveAndVerifyLink(mq);
    assertFalse(r.inviteLinkEnabled);
    assertNull(r.setPasswordUrl);
  }

  @Test
  void isWhitelistedResetUrl_nonWhitelistedDomain_failClosed() throws Exception {
    installContext(mock(PasswordResetLinkFacade.class));
    assertTrue(MqSignedUrlFactory.isWhitelistedResetUrl(PORTAL_ORIGIN + "/reset-password/x"));
    assertFalse(
        MqSignedUrlFactory.isWhitelistedResetUrl("https://phishing.evil.com/reset?t=x"),
        "non-whitelisted domain must be rejected (I2 anti-phishing)");
  }
}
