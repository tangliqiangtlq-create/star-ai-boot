package org.jeecg.common.system.vo;

import lombok.Data;

/**
 * Dictionary query parameter entity
 * @author: jeecg-boot
 */
@Data
public class DictQuery {
    /**
     * Table name
     */
    private String table;
    /**
     * Storage column
     */
    private String code;

    /**
     * Display columns
     */
    private String text;

    /**
     * Keyword search.
     */
    private String keyword;

    /**
     * Storage column value used for display queries
     */
    private String codeValue;

}
