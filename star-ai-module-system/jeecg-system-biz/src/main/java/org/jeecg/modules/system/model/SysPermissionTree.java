package org.jeecg.modules.system.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jeecg.modules.system.entity.SysPermission;

/**
 * @Description: Menu tree wrapper
 * @author: jeecg-boot
 */
public class SysPermissionTree implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	private String id;

	private String key;
	private String title;

	/**
	 * Parent id
	 */
	private String parentId;

	/**
	 * MenuName
	 */
	private String name;

	/**
	 * MenuPermissionCode
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
	 * Redirect to web link
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
	private Integer menuType;

	/**
	 * Whether leaf node: 1 yes, 0 no
	 */
	private boolean isLeaf;
	
	/**
	 * Whether route menu: 0 no, 1 yes (default 1)
	 */
	private boolean route;


	/**
	 * Whether to cache route page: 0 no, 1 yes (default 1)
	 */
	private boolean keepAlive;


	/**
	 * Description
	 */
	private String description;

	/**
	 * Delete status: 0 normal, 1 deleted
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

	/**alwaysShow*/
    private boolean alwaysShow;
    /**Hide route menu: 0=no, 1=yes (default 0)*/
    private boolean hidden;
    
    /**Button permission status (0=invalid, 1=valid)*/
	private java.lang.String status;

	/*update_begin author:wuxianquan date:20190908 for: add model fields */
	/** External menu open mode: 0=internal, 1=external */
	private boolean internalOrExternal;
	/*update_end author:wuxianquan date:20190908 for: add model fields */

	/*update_begin author:liusq date:20230601 for: [issues/4986] add hideTab on model */
	/**
	 * Whether to hide tab: 0 no, 1 yes (default 0)
	 */
	private boolean hideTab;
	/*update_end author:liusq date:20230601 for: [issues/4986] add hideTab on model */

	public SysPermissionTree() {
	}

	public SysPermissionTree(SysPermission permission) {
		this.key = permission.getId();
		this.id = permission.getId();
		this.perms = permission.getPerms();
		this.permsType = permission.getPermsType();
		this.component = permission.getComponent();
		this.componentName = permission.getComponentName();
		this.createBy = permission.getCreateBy();
		this.createTime = permission.getCreateTime();
		this.delFlag = permission.getDelFlag();
		this.description = permission.getDescription();
		this.icon = permission.getIcon();
		this.isLeaf = permission.isLeaf();
		this.menuType = permission.getMenuType();
		this.name = permission.getName();
		this.parentId = permission.getParentId();
		this.sortNo = permission.getSortNo();
		this.updateBy = permission.getUpdateBy();
		this.updateTime = permission.getUpdateTime();
		this.redirect = permission.getRedirect();
		this.url = permission.getUrl();
		this.hidden = permission.isHidden();
		this.route = permission.isRoute();
		this.keepAlive = permission.isKeepAlive();
		this.alwaysShow= permission.isAlwaysShow();
		/*update_begin author:wuxianquan date:20190908 for: assign values */
		this.internalOrExternal = permission.isInternalOrExternal();
		/*update_end author:wuxianquan date:20190908 for: assign values */
		this.title=permission.getName();
		/*update_end author:liusq date:20230601 for: [issues/4986] add hideTab on model */
		this.hideTab = permission.isHideTab();
		/*update_end author:liusq date:20230601 for: [issues/4986] add hideTab on model */
		if (!permission.isLeaf()) {
			this.children = new ArrayList<SysPermissionTree>();
		}
		this.status = permission.getStatus();
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	private List<SysPermissionTree> children;

	public boolean isLeaf() {
		return isLeaf;
	}

	public void setLeaf(boolean leaf) {
		isLeaf = leaf;
	}

	public boolean isKeepAlive() {
		return keepAlive;
	}

	public void setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
	}

	public boolean isAlwaysShow() {
		return alwaysShow;
	}

	public void setAlwaysShow(boolean alwaysShow) {
		this.alwaysShow = alwaysShow;
	}
	public List<SysPermissionTree> getChildren() {
		return children;
	}

	public void setChildren(List<SysPermissionTree> children) {
		this.children = children;
	}

	public String getRedirect() {
		return redirect;
	}

	public void setRedirect(String redirect) {
		this.redirect = redirect;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getParentId() {
		return parentId;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getIcon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = icon;
	}

	public String getComponent() {
		return component;
	}

	public void setComponent(String component) {
		this.component = component;
	}

	public String getComponentName() {
		return componentName;
	}

	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Double getSortNo() {
		return sortNo;
	}

	public void setSortNo(Double sortNo) {
		this.sortNo = sortNo;
	}

	public Integer getMenuType() {
		return menuType;
	}

	public void setMenuType(Integer menuType) {
		this.menuType = menuType;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isRoute() {
		return route;
	}

	public void setRoute(boolean route) {
		this.route = route;
	}

	public Integer getDelFlag() {
		return delFlag;
	}

	public void setDelFlag(Integer delFlag) {
		this.delFlag = delFlag;
	}

	public String getCreateBy() {
		return createBy;
	}

	public void setCreateBy(String createBy) {
		this.createBy = createBy;
	}

	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}

	public String getUpdateBy() {
		return updateBy;
	}

	public void setUpdateBy(String updateBy) {
		this.updateBy = updateBy;
	}

	public Date getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(Date updateTime) {
		this.updateTime = updateTime;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getPerms() {
		return perms;
	}

	public void setPerms(String perms) {
		this.perms = perms;
	}

	public boolean getIsLeaf() {
		return isLeaf;
	}

	public void setIsLeaf(boolean isLeaf) {
		this.isLeaf = isLeaf;
	}

	public String getPermsType() {
		return permsType;
	}

	public void setPermsType(String permsType) {
		this.permsType = permsType;
	}

	public java.lang.String getStatus() {
		return status;
	}

	public void setStatus(java.lang.String status) {
		this.status = status;
	}

	/*update_begin author:wuxianquan date:20190908 for:get setMethod */
	public boolean isInternalOrExternal() {
		return internalOrExternal;
	}

	public void setInternalOrExternal(boolean internalOrExternal) {
		this.internalOrExternal = internalOrExternal;
	}
	/*update_end author:wuxianquan date:20190908 for:get set Method */

	public boolean isHideTab() {
		return hideTab;
	}

	public void setHideTab(boolean hideTab) {
		this.hideTab = hideTab;
	}
}
