package org.jeecg.config.firewall;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

@Slf4j
public final class E7SvgSanitizer {

  private static final Pattern JAVASCRIPT_PROTOCOL =
      Pattern.compile("(?i)^\\s*javascript\\s*:");

  private E7SvgSanitizer() {}

  public static String sanitize(String xml) {
    if (xml == null) return null;
    if (xml.isEmpty()) return "";
    return new String(sanitizeSvg(xml.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
  }

  public static byte[] sanitizeSvg(byte[] input) {
    if (input == null || input.length == 0) {
      return input;
    }
    try {
      String xml = new String(input, StandardCharsets.UTF_8);
      Document doc =
          Jsoup.parse(new ByteArrayInputStream(input), StandardCharsets.UTF_8.name(), "", Parser.xmlParser());
      doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
      doc.outputSettings().prettyPrint(false);

      Safelist.none();

      removeScriptElements(doc);
      removeForeignAndAnimationElements(doc);
      stripEventHandlersAndDangerousHref(doc);
      removeStyleAttributes(doc);

      String clean = doc.html();
      return clean.getBytes(StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.warn("SVG sanitize parse failed, reject original SVG");
      throw new IllegalArgumentException("SVG content parse/sanitize failed", e);
    }
  }

  private static void removeScriptElements(Document doc) {
    List<Element> scripts = doc.select("script");
    for (Element s : scripts) {
      s.remove();
    }
    List<Element> all = doc.getAllElements();
    for (Element el : all) {
      String tag = el.tagName();
      if ("script".equalsIgnoreCase(tag)) {
        el.remove();
      }
    }
  }

  /**
   * V2.14-A: strip SVG elements that can execute script or clone/instantiate other nodes —
   * {@code foreignObject} (arbitrary HTML inside SVG), {@code use} (xlink:href instantiation),
   * and {@code animate}/{@code set} (attribute rewriting into an event handler or javascript: URI).
   */
  private static void removeForeignAndAnimationElements(Document doc) {
    List<Element> all = doc.getAllElements();
    for (Element el : all) {
      String tag = el.tagName();
      if (tag == null) {
        continue;
      }
      if (tag.equalsIgnoreCase("foreignObject")
          || tag.equalsIgnoreCase("use")
          || tag.equalsIgnoreCase("animate")
          || tag.equalsIgnoreCase("set")) {
        el.remove();
      }
    }
  }

  /** V2.14-A: drop inline {@code style} attributes (url(javascript:) / expression() vectors). */
  private static void removeStyleAttributes(Document doc) {
    List<Element> all = doc.getAllElements();
    for (Element el : all) {
      if (el.hasAttr("style")) {
        el.removeAttr("style");
      }
    }
  }

  private static void stripEventHandlersAndDangerousHref(Document doc) {
    List<Element> all = doc.getAllElements();
    for (Element el : all) {
      List<Attribute> attrs = el.attributes().asList();
      for (Attribute attr : attrs) {
        String key = attr.getKey();
        if (key != null && key.length() >= 2 && key.regionMatches(true, 0, "on", 0, 2)) {
          el.removeAttr(key);
          continue;
        }
        String val = attr.getValue();
        if (val == null) {
          continue;
        }
        if ("href".equalsIgnoreCase(key)
            || "xlink:href".equalsIgnoreCase(key)
            || "src".equalsIgnoreCase(key)) {
          if (JAVASCRIPT_PROTOCOL.matcher(val).find()) {
            el.removeAttr(key);
          }
        }
      }
    }
  }
}
