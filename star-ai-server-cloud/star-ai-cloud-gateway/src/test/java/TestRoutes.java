import org.jeecg.loader.vo.PredicatesVo;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @Description: test
 * @author: lsq
 * @date: October 13, 2023 11:32
 */
public class TestRoutes {
  private static final Logger log = LoggerFactory.getLogger(TestRoutes.class);


    @Test
    public void TestRoutes() {
        List<PredicatesVo> list = new ArrayList<>();
        PredicatesVo a = new PredicatesVo();
        a.setName("path");
        String[] aArr={"/sys/**","/eoa/**"};
        a.setArgs(Arrays.asList(aArr));
        list.add(a);

        PredicatesVo b = new PredicatesVo();
        b.setName("path");
        String[] bArr={"/sys/**","/demo/**"};
        b.setArgs(Arrays.asList(bArr));
        list.add(b);

        Map<String, List<String>> groupedPredicates = new HashMap<>();
        for (PredicatesVo predicatesVo : list) {
            String name = predicatesVo.getName();
            List<String> args1 = predicatesVo.getArgs();
            groupedPredicates.computeIfAbsent(name, k -> new ArrayList<>()).addAll(args1);
        }
        log.debug(String.valueOf(groupedPredicates));
    }

}
