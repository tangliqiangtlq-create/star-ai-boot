package org.jeecg.common.util;

import org.apache.commons.lang3.StringUtils;
import org.pegdown.PegDownProcessor;
import org.springframework.web.util.HtmlUtils;

/**
 * HTML Tools
 * @author: jeecg-boot
 * @date: 2022/3/30 14:43
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class HTMLUtils {

    /**
     * Get text inside HTML, excluding tags
     *
     * @param html HTML code
     */
    public static String getInnerText(String html) {
        if (StringUtils.isNotBlank(html)) {
            //remove html tags
            String content = html.replaceAll("</?[^>]+>", "");
            // Combine multiple spaces into one space
            content = content.replaceAll("(&nbsp;)+", "&nbsp;");
            // reverse escape character
            content = HtmlUtils.htmlUnescape(content);
            return content.trim();
        }
        return "";
    }

    /**
     * Parse Markdown to HTML
     * @param markdownContent
     * @return
     */
    public static String parseMarkdown(String markdownContent) {
        PegDownProcessor pdp = new PegDownProcessor();
        return pdp.markdownToHtml(markdownContent);
    }

}
