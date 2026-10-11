package org.jeecg.modules.system.vo;

import lombok.Data;
import org.jeecg.modules.system.entity.SysDictItem;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.jeecgframework.poi.excel.annotation.ExcelCollection;

import java.util.List;

/**
 * @Description: SystemDictionaryPagination
 * @author: jeecg-boot
 */
@Data
public class SysDictPage {

    /**
     * Primary key
     */
    private String id;
    /**
     * DictionaryName
     */
    @Excel(name = "DictionaryName", width = 20)
    private String dictName;

    /**
     * DictionaryCode
     */
    @Excel(name = "DictionaryCode", width = 30)
    private String dictCode;
    /**
     * DeleteStatus
     */
    private Integer delFlag;
    /**
     * Description
     */
    @Excel(name = "Description", width = 30)
    private String description;

    @ExcelCollection(name = "DictionaryList")
    private List<SysDictItem> sysDictItemList;

}
