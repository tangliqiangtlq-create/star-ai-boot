package org.jeecg.common.aspect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * dictionaryannotation
 * @author: dangzhenghui
 * @date: March 17, 2019 - 9:37:16 pm
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Dict {
    /**
     * Method description: data code
     * Author: dangzhenghui
     * Date: 2019-03-17 21:37:16
     *
     * @return Return type: String
     */
    String dicCode();

    /**
     * Method description: data Text
     * Author: dangzhenghui
     * Date: 2019-03-17 21:37:16
     *
     * @return Return type: String
     */
    String dicText() default "";

    /**
     * Method description: data dictionary table
     * Author: dangzhenghui
     * Date: 2019-03-17 21:37:16
     *
     * @return Return type: String
     */
    String dictTable() default "";

    /**
     * Method description: multi-field dictionary text format template
     * Author: mightyPay
     * Date: 2026-06-27
     *
     * @return Return type: String
     */
    String dicTextFormat() default "";


    //update-begin---author:chenrui ---date:20231221  for: [issues/#5643] fix table-dict cross-DB query under distributed mode ------------
    /**
     * Method description: data source name of the dictionary table
     * Author: chenrui
     * Date: 2023-12-20 16:58
     *
     * @return Return type: String
     */
    String ds() default "";
    //update-end---author:chenrui ---date:20231221  for: [issues/#5643] fix table-dict cross-DB query under distributed mode ------------
}
