package org.jeecg.modules.system.entity;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecg.modules.system.constant.DefIndexConst;

/**
 * <p>
 * MenuPermissionTable
 * </p>
 *
 * @Author scott
 * @since 2018-12-21
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SysPermission implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/**
	 * Parent id
	 */
	private String parentId;

	/**
	 * MenuName
	 */
	private String name;

	/**
	 * Menu permission codes, e.g. "sys:schedule:list,sys:schedule:info", comma-separated
	 */
	private String perms;
	/**
	 * Permission policy: 1 show, 2 disable
	 */
	private String permsType;

	/**
	 * Menu icon
	 */
	private String icon;

	/**
	 * Component
	 */
	private String component;
	
	/**
	 * Component name
	 */
	private String componentName;

	/**
	 * Path
	 */
	private String url;
	/**
	 * Top-level menu redirect URL
	 */
	private String redirect;

	/**
	 * MenuSort
	 */
	private Double sortNo;

	/**
	 * Type (0: top-level menu; 1: submenu; 2: button permission)
	 */
	@Dict(dicCode = "menu_type")
	private Integer menuType;

	/**
	 * Whether leaf node: 1 yes, 0 no
	 */
	@TableField(value="is_leaf")
	private boolean leaf;
	
	/**
	 * Whether route menu: 0 no, 1 yes (default 1)
	 */
	@TableField(value="is_route")
	private boolean route;


	/**
	 * Whether to cache page: 0 no, 1 yes (default 1)
	 */
	@TableField(value="keep_alive")
	private boolean keepAlive;

	/**
	 * Description
	 */
	private String description;

	/**
	 * Created by
	 */
	private String createBy;

	/**
	 * Delete status: 0 normal, 1 deleted
	 */
	private Integer delFlag;
	
	/**
	 * Whether menu data permission is configured: 1 yes, 0 no (default 0)
	 */
	private Integer ruleFlag;
	
	/**
	 * Whether to hide route menu: 0 no, 1 yes (default 0)
	 */
	private boolean hidden;

	/**
	 * Whether to hide tab: 0 no, 1 yes (default 0)
	 */
	private boolean hideTab;

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
	
	/**Button permission status (0 invalid, 1 valid) */
	private java.lang.String status;
	
	/**alwaysShow*/
    private boolean alwaysShow;

	/*update_begin author:wuxianquan date:20190908 for:Add field to entity */
    /** External link menu opening method 0/Open internally 1/Open externally */
    private boolean internalOrExternal;
	/*update_end author:wuxianquan date:20190908 for:Entity adds fields */

    public SysPermission() {
    	
    }
    public SysPermission(boolean index) {
    	if(index) {
			this.id = "9502685863ab87f0ad1134142788a385";
			this.name = DefIndexConst.DEF_INDEX_NAME;
			this.component = DefIndexConst.DEF_INDEX_COMPONENT;
			this.componentName = "dashboard-analysis";
			this.url = DefIndexConst.DEF_INDEX_URL;
        	this.icon="home";
        	this.menuType=0;
        	this.sortNo=0.0;
        	this.ruleFlag=0;
        	this.delFlag=0;
        	this.alwaysShow=false;
        	this.route=true;
        	this.keepAlive=true;
        	this.leaf=true;
        	this.hidden=false;
    	}
    	
    }
}
