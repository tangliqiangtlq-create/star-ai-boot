package org.jeecg.modules.system.vo.lowapp;

import lombok.Data;
import org.jeecgframework.poi.excel.annotation.Excel;

@Data
public class ExportDepartVo {
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
}