package org.jeecg.modules.system.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * DictionaryTable
 * </p>
 *
 * @Author zhangweijian
 * @since 2018-12-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SysDict implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * [Reserved field, currently unused]
     * Dictionary type: 0 string, 1 number, 2 boolean.
     * Frontend JS is sensitive to string, number, and boolean types and must distinguish them for select tag matching.
     * Defaults to string type.
     */
    private Integer type;
    
    /**
     * DictionaryName
     */
    private String dictName;

    /**
     * DictionaryCode
     */
    private String dictCode;

    /**
     * Description
     */
    private String description;

    /**
     * DeleteStatus
     */
    @TableLogic
    private Integer delFlag;

    /**
     * Created by
     */
    private String createBy;

    /**
     * Create time
     */
    private Date createTime;

    /**
     * Updated by
     */
    private String updateBy;

    /**
     * Update time
     */
    private Date updateTime;

    /**TenantID*/
    private java.lang.Integer tenantId;
    
    /** Associated low-code app ID */
    private java.lang.String lowAppId;

}
