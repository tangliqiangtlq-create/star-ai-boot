package org.jeecg.common.system.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Used when querying multiple dictionaries
 * @author: jeecg-boot
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DictModelMany extends DictModel {

    /**
     * Dict code; used when querying by multiple field codes to distinguish dict options
     */
    private String dictCode;

}
