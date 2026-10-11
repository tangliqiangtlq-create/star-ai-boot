package org.jeecg.common.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * drop-down tree model
 *
 * @author jeecg-boot
 */
@Data
public class SelectTreeModel implements Serializable {

    private String key;
    private String title;
    private String value;
    /**
     * Parent Id
     */
    private String parentId;
    /**
     * Whether it is a leaf node
     */
    private boolean isLeaf;
    /**
     * Child node
     */
    private List<SelectTreeModel> children;

}
