package org.jeecg.modules.system.vo.lowapp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import org.jeecg.modules.system.entity.SysDict;
import org.jeecg.modules.system.entity.SysDictItem;

import java.util.List;

@Data
public class SysDictVo {
    /**
     * Dictionaryid
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * DictionaryName
     */
    private String dictName;

    /**
     * DictionaryCode
     */
    private String dictCode;

    /**
     * App id
     */
    private String lowAppId;
    
    /**
     * TenantID
     */
    private Integer tenantId;

    /**
     * Dict sub-item
     */
    private List<SysDictItem> dictItemsList;
}