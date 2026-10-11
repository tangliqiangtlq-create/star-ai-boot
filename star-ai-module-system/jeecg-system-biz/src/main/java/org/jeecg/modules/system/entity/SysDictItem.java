package org.jeecg.modules.system.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecgframework.poi.excel.annotation.Excel;

/**
 * <p>
 *
 * </p>
 *
 * @Author zhangweijian
 * @since 2018-12-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SysDictItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Dictionaryid
     */
    private String dictId;

    /**
     * Dict item text
     */
    @Excel(name = "Dictionary item text", width = 20)
    private String itemText;

    /**
     * Dict item value
     */
    @Excel(name = "Dictionary item value", width = 30)
    private String itemValue;

    /**
     * Description
     */
    @Excel(name = "Description", width = 40)
    private String description;

    /**
     * Sort
     */
    @Excel(name = "Sort", width = 15,type=4)
    private Integer sortOrder;


    /**
     * Status (1 enabled 0 disabled)
     */
    @Dict(dicCode = "dict_item_status")
    private Integer status;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    /**
     * Dict item color 
     */
    private String itemColor;

}
