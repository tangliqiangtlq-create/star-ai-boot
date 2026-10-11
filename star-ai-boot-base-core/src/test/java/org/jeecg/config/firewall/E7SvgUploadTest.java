package org.jeecg.config.firewall;

import org.junit.Assert;
import org.junit.Test;

public class E7SvgUploadTest {

    @Test
    public void testSanitizeRemovesScriptTag() {
        String evil = "<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\">\n  <script>alert('xss')</script>\n  <rect width=\"100\" height=\"100\" fill=\"red\"/>\n</svg>";
        String safe = E7SvgSanitizer.sanitize(evil);
        Assert.assertNotNull(safe);
        Assert.assertFalse("script tag must be stripped", safe.contains("<script>") || safe.contains("</script>"));
    }

    @Test
    public void testSanitizeRemovesOnloadHandler() {
        String evil = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"><circle cx=\"50\" cy=\"50\" r=\"40\"/></svg>";
        String safe = E7SvgSanitizer.sanitize(evil);
        Assert.assertNotNull(safe);
        Assert.assertFalse("on* handler must be stripped", safe.toLowerCase().contains("onload="));
    }

    @Test
    public void testSanitizeRemovesJavascriptHref() {
        String evil = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"><a xlink:href=\"javascript:alert(1)\"><text x=\"10\" y=\"20\">click</text></a></svg>";
        String safe = E7SvgSanitizer.sanitize(evil);
        Assert.assertNotNull(safe);
        Assert.assertFalse("javascript: href must be stripped", safe.toLowerCase().contains("javascript:"));
    }

    @Test
    public void testSafeSvgPreserved() {
        String good = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><circle cx=\"50\" cy=\"50\" r=\"40\" fill=\"blue\"/></svg>";
        String safe = E7SvgSanitizer.sanitize(good);
        Assert.assertNotNull(safe);
        Assert.assertTrue("circle element preserved", safe.contains("<circle") || safe.contains("circle"));
        Assert.assertTrue("svg element preserved", safe.contains("<svg") || safe.contains("svg"));
    }

    @Test
    public void testNullEmptyInputNoCrash() {
        Assert.assertNull(E7SvgSanitizer.sanitize(null));
        Assert.assertEquals("", E7SvgSanitizer.sanitize(""));
    }
}
