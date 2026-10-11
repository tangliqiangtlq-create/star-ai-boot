package org.jeecg.modules.system.util;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * @Description: XSSUtils replaces with empty chars; CSDN-style escapes e.g. leading "<" to &lt;
 * @author: lsq
 * @date: 2021-07-26 19:13
 */
public class XssUtils {

    private static Pattern[] patterns = new Pattern[]{
        //Script fragments
        Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE),
        //src='...'
        Pattern.compile("src[\r\n]*=[\r\n]*\\\'(.*?)\\\'", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("src[\r\n]*=[\r\n]*\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        //script tags
        Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<script(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        //eval(...)
        Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        //expression(...)
        Pattern.compile("e­xpression\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        //javascript:...
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        //vbscript:...
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        //onload(...)=...
        Pattern.compile("onload(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
    };

    public static String scriptXss(String value) {
        if (value != null) {
            value = value.replaceAll(" ", "");
            for(Pattern scriptPattern: patterns){
                value = scriptPattern.matcher(value).replaceAll("");
            }
        }
        return HtmlUtils.htmlEscape(value);
    }
}
