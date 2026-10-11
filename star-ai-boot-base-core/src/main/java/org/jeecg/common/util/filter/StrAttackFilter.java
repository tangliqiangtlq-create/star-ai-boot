package org.jeecg.common.util.filter;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * File uploadString filter special characters
 * @author: jeecg-boot
 */
public class StrAttackFilter {
  private static final Logger log = LoggerFactory.getLogger(StrAttackFilter.class);


    public static String filter(String str) throws PatternSyntaxException {
        // Remove all special characters
        String regEx = "[`_《》~!@#$%^&*()+=|{}':;',\\[\\].<>?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        return m.replaceAll("").trim();
    }

//    public static void main(String[] args) {
//        String filter = filter("@#jeecg/《》【bo】￥%……&*（o）)))！@t<>,.,/?'\'~~`");
//        log.debug(String.valueOf(filter));
//    }
}
