package org.jeecg.config.shiro.ignore;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory store for @IgnoreAuth URLs; works with JwtFilter for login-free validation
 * PS: ThreadLocal cannot be used because JwtFilter initializes before ThreadLocal is populated, leaving it empty
 * @author eightmonth
 * @date 2024/4/18 15:02
 */
public class InMemoryIgnoreAuth {
    private static final List<String> IGNORE_AUTH_LIST = new ArrayList<>();

    public InMemoryIgnoreAuth() {}

    public static void set(List<String> list) {
        IGNORE_AUTH_LIST.addAll(list);
    }

    public static List<String> get() {
        return IGNORE_AUTH_LIST;
    }

    public static void clear() {
        IGNORE_AUTH_LIST.clear();
    }

    public static boolean contains(String url) {
        for (String ignoreAuth : IGNORE_AUTH_LIST) {
            if (url.endsWith(ignoreAuth)) {
                return true;
            }
        }

        return false;
    }
}
