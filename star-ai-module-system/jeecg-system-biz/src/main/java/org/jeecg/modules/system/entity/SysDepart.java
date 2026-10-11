package org.jeecg.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * <p>
 * DepartmentTable
 * <p>
 * 
 * @Author Steve
 * @Since  2019-01-22
 */
@Data
@TableName("sys_depart")
public class SysDepart implements Serializable {
    private static final long serialVersionUID = 1L;
    
	/**ID*/
	@TableId(type = IdType.ASSIGN_ID)
	private String id;
	/**Parent organization ID */
	private String parentId;
	/**Organization/department name */
	@Excel(name="Organization/department name",width=15)
	private String departName;
	/**English name */
	@Excel(name="English name",width=15)
	private String departNameEn;
	/**abbreviation */
	private String departNameAbbr;
	/**Sort*/
	@Excel(name="Sort",width=15)
	private Integer departOrder;
	/**Description*/
	@Excel(name="Description",width=15)
	private String description;
	/**Organization type 1=company, 2=organization, 3=position */
	@Excel(name="Organization category",width=15,dicCode="org_category")
	private String orgCategory;
	/**Institution type */
	private String orgType;
	/**Institution code */
	@Excel(name="Organization code",width=15)
	private String orgCode;
	/**Phone number */
	@Excel(name="Mobile number",width=15)
	private String mobile;
	/**fax */
	@Excel(name="Fax",width=15)
	private String fax;
	/**address */
	@Excel(name="Address",width=15)
	private String address;
	/**Remark*/
	@Excel(name="Remark",width=15)
	private String memo;
	/**Status (1 is enabled, 0 is not enabled) */
	@Dict(dicCode = "depart_status")
	private String status;
	/**delete status (0, normal, 1 deleted) */
	@Dict(dicCode = "del_flag")
	private String delFlag;
	/**ID for connecting to corporate WeChat */
	private String qywxIdentifier;
	/**Department ID for docking with DingTalk */
	private String dingIdentifier;
	/**Created by*/
	private String createBy;
	/**Creation date */
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
	private Date createTime;
	/**Updated by*/
	private String updateBy;
	/**Update date */
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
	private Date updateTime;
	/**TenantID*/
	private java.lang.Integer tenantId;

	/**Whether there are leaf nodes: 1 yes 0 no */
	private Integer izLeaf;

    //update-begin---author:wangshuai ---date:20200308 for: [JTC-119] Set the department head under the department management menu, add the field head ids and the old head ids
    /**ids of department head */
	@TableField(exist = false)
	private String directorUserIds;
    /**The ids of the old department head (used to compare delete and new addition) */
	@TableField(exist = false)
    private String oldDirectorUserIds;
    //update-end---author:wangshuai ---date:20200308 for: [JTC-119] New field person in charge ids and old person in charge ids
	
	/**
	 * Override equals method
	 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
			return true;
		}
        if (o == null || getClass() != o.getClass()) {
			return false;
		}
        if (!super.equals(o)) {
			return false;
		}
        SysDepart depart = (SysDepart) o;
        return Objects.equals(id, depart.id) &&
                Objects.equals(parentId, depart.parentId) &&
                Objects.equals(departName, depart.departName) &&
                Objects.equals(departNameEn, depart.departNameEn) &&
                Objects.equals(departNameAbbr, depart.departNameAbbr) &&
                Objects.equals(departOrder, depart.departOrder) &&
                Objects.equals(description, depart.description) &&
                Objects.equals(orgCategory, depart.orgCategory) &&
                Objects.equals(orgType, depart.orgType) &&
                Objects.equals(orgCode, depart.orgCode) &&
                Objects.equals(mobile, depart.mobile) &&
                Objects.equals(fax, depart.fax) &&
                Objects.equals(address, depart.address) &&
                Objects.equals(memo, depart.memo) &&
                Objects.equals(status, depart.status) &&
                Objects.equals(delFlag, depart.delFlag) &&
                Objects.equals(createBy, depart.createBy) &&
                Objects.equals(createTime, depart.createTime) &&
                Objects.equals(updateBy, depart.updateBy) &&
                Objects.equals(tenantId, depart.tenantId) &&
                Objects.equals(updateTime, depart.updateTime);
    }

    /**
     * Override hashCode method
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, parentId, departName, 
        		departNameEn, departNameAbbr, departOrder, description,orgCategory, 
        		orgType, orgCode, mobile, fax, address, memo, status, 
        		delFlag, createBy, createTime, updateBy, updateTime, tenantId);
    }
}
