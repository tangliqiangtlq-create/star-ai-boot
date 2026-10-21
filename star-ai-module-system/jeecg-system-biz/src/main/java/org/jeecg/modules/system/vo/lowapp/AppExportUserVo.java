package org.jeecg.modules.system.vo.lowapp;

import lombok.Data;
import org.jeecgframework.poi.excel.annotation.Excel;

import java.util.Date;

/**
* @Description: App user/dept export-import entity
*
* @author: wangshuai
* @date: 2023/6/14 16:42
*/
@Data
public class AppExportUserVo {

    /**User number*/
    @Excel(name="User ID",width=30)
    private String id;

    /**Name*/
    @Excel(name="Full name",width=30)
    private String realname;
    
    /**Position*/
    @Excel(name = "Position",width = 30)
    private String position;

    /**Department*/
    @Excel(name = "Department",width = 30)
    private String depart;

    /**Employee no*/
    @Excel(name = "Employee ID",width = 30)
    private String workNo;
    
    /**Mobile phone*/
    @Excel(name = "Mobile number",width = 30)
    private String phone;
    
    /**Email*/
    @Excel(name = "Email",width = 30)
    private String email;

    /**Join time*/
    @Excel(name = "Join time",width = 30, format = "yyyy-MM-dd")
    private Date createTime;
}