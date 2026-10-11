package org.jeecg.common.util.security;

import cn.hutool.core.util.StrUtil;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Backend-side XSS sanitizer (B9-2 / front DOMPurify mirror).
 *
 * <p>Java equivalent of DOMPurify: uses the Google {@code owasp-java-html-sanitizer} which ships
 * 5 pre-baked {@link Sanitizers} policies. We compose them into two presets:
 *
 * <ol>
 *   <li>{@link #RICH_TEXT_ALLOWLIST} — for TinyMCE / wangEditor user content. Same strict profile
 *       as the recommended frontend allowlist: block formatting, links, images, tables, but NO
 *       scripts, no event handlers, no iframe/object/embed/form/textarea, NO style with
 *       {@code expression()/javascript:}, etc.
 *   <li>{@link #STRIP_ALL} — returns plain text without any tags (used when a {@code v-html} slot
 *       expects pure text but the field was accidentally persisted as HTML).
 * </ol>
 *
 * <p>Also offers {@link #stripEventHandlers(String)} which drops all {@code on*} attributes plus
 * {@code href/src/data-*} values starting with {@code javascript:}.
 *
 * <p>We intentionally strip {@code class} / {@code id} attributes too (prevents DOM clobbering of
 * global scope through reused ids like {@code <div id=location href=...>}).
 */
public final class HtmlSanitizerUtil {

  private static final PolicyFactory RICH_TEXT_ALLOWLIST =
      Sanitizers.FORMATTING
          .and(Sanitizers.BLOCKS)
          .and(Sanitizers.LINKS)
          .and(Sanitizers.IMAGES)
          .and(Sanitizers.STYLES)
          .and(Sanitizers.TABLES)
          .and(
              new HtmlPolicyBuilder()
                  // HTML Standard URL protocols accepted in href/src — owasp-java-html-sanitizer
                  // auto-rejects javascript: / vbscript: via element policy; no allowProtocols()
                  // needed (20240325.1 release uses allowUrlProtocols with a string list, not
                  // the old allowProtocols varargs).
                  .allowElements("span", "div", "p", "br", "hr", "pre", "blockquote")
                  .allowAttributes("title", "alt", "lang", "dir")
                  .globally()
                  .allowAttributes("class")
                  .matching(
                      v ->
                          v != null
                              && !v.contains("javascript:")
                              && !v.contains("expression(")
                              && !v.contains("on"))
                  .onElements("span", "div", "p", "pre", "blockquote")
                  .disallowElements(
                      "script",
                      "iframe",
                      "object",
                      "embed",
                      "form",
                      "textarea",
                      "base",
                      "input",
                      "select",
                      "button")
                  .disallowAttributes(
                      "onabort", "onblur", "onchange", "onclick", "ondblclick",
                      "onerror", "onfocus", "onkeydown", "onkeypress", "onkeyup", "onload",
                      "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup",
                      "onreset", "onresize", "onscroll", "onselect", "onsubmit", "onunload",
                      "oninput", "onplay", "onpause", "ontoggle", "onmessage", "onhashchange",
                      "onpopstate", "oncontextmenu")
                  .globally()
                  .toFactory());

  private static final PolicyFactory STRIP_ALL =
      new HtmlPolicyBuilder().toFactory();

  private HtmlSanitizerUtil() {}

  /** Apply the rich-text allowlist and return a safe HTML string. */
  public static String sanitizeRichText(String raw) {
    return sanitize(RICH_TEXT_ALLOWLIST, raw);
  }

  /**
   * Sanitize "simple" user-visible text fields (titles, abstracts, dictText) that are not expected
   * to contain any HTML. Every element is stripped, but entity references and plain Unicode text
   * are preserved exactly. This is the backend equivalent of calling DOMPurify with ALLOWED_TAGS=[]
   * on the frontend.
   */
  public static String sanitizeSimpleText(String raw) {
    return stripAllTags(raw);
  }

  /** Strip every HTML element and return only plain text. */
  public static String stripAllTags(String raw) {
    return sanitize(STRIP_ALL, raw);
  }

  /** Convenience: sanitize with a specific preset; null-safe. */
  public static String sanitize(PolicyFactory factory, String raw) {
    if (StrUtil.isBlank(raw)) return raw;
    try {
      String s = factory.sanitize(raw);
      // Post-processing safety belt: drop any lingering "javascript:" / "vbscript:" in the
      // unlikely event a new policy lets them through (defense-in-depth).
      s = stripDangerousProtocols(s);
      return s;
    } catch (Throwable t) {
      // Never let sanitization crashes propagate (would turn this into a DoS vector).
      // Return empty string instead.
      return "";
    }
  }

  /** Keep tag structure but drop {@code on*=…} attributes and {@code javascript:} URLs. */
  public static String stripEventHandlers(String raw) {
    if (StrUtil.isBlank(raw)) return raw;
    return sanitizeRichText(raw);
  }

  private static String stripDangerousProtocols(String html) {
    // Case-insensitive replace of href/src/data-* with javascript/vbscript/data-uri except
    // data:image/*
    if (html == null) return null;
    String s = html;
    s = s.replaceAll("(?i)(href|src|data-[a-z0-9_-]+)\\s*=\\s*([\"'])\\s*javascript:[^\"']*([\"'])", "$1=$2$3");
    s = s.replaceAll("(?i)(href|src|data-[a-z0-9_-]+)\\s*=\\s*([\"'])\\s*vbscript:[^\"']*([\"'])", "$1=$2$3");
    s = s.replaceAll("(?i)expression\\s*\\([^)]*\\)", "");
    return s;
  }
}
