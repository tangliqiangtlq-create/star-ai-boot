package org.jeecg.modules.system.vo;

import lombok.Data;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecgframework.poi.excel.annotation.Excel;

@Data
public class SysDepartExportVo {
    /**DepartmentPath*/
    @Excel(name="DepartmentPath",width=50)
    private String departNameUrl;
    /**Org/department name*/
    @Excel(name="DepartmentName",width=50)
    private String departName;
    /**id*/
    private String id;
    /**Parent id*/
    private String parentId;
    /**English name*/
    @Excel(name="English name",width=15)
    private String departNameEn;
    /**Sort*/
    @Excel(name="Sort",width=15)
    private Integer departOrder;
    /**Description*/
    @Excel(name="Description",width=15)
    private String description;
    /**Org category: 1=company, 2=org unit, 3=post*/
    @Excel(name="Organization category",width=15,dicCode="org_category")
    private String orgCategory;
    /**Org code*/
    @Excel(name="Organization code",width=15)
    private String orgCode;
    /**Mobile phone*/
    @Excel(name="Mobile number",width=15)
    private String mobile;
    /**Fax*/
    @Excel(name="Fax",width=15)
    private String fax;
    /**Address*/
    @Excel(name="Address",width=15)
    private String address;
    /**Remark*/
    @Excel(name="Remark",width=15)
    private String memo;
}
