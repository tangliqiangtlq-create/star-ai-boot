package org.jeecg.modules.system.model;

import java.io.Serializable;
import java.util.Date;

import org.jeecg.modules.system.entity.SysDict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

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
public class SysDictTree implements Serializable {

    private static final long serialVersionUID = 1L;

    private String key;
	
	private String title;
	
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /**
     * Dict type: 0 string, 1 number, 2 boolean
     * Frontend JS is sensitive to string, number, and boolean types and needs them distinguished for select matching
     * Defaults to string
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
    
    public SysDictTree(SysDict node) {
    	this.id = node.getId();
		this.key = node.getId();
		this.title = node.getDictName();
		this.dictCode = node.getDictCode();
		this.description = node.getDescription();
		this.delFlag = node.getDelFlag();
		this.type = node.getType();
	}
    
}
