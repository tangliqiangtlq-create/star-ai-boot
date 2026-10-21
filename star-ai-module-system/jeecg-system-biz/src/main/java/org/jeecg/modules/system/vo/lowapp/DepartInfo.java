package org.jeecg.modules.system.vo.lowapp;

import lombok.Data;

import java.util.List;

/**
 * @Author taoYan
 * @Date 2022/12/30 10:52
 **/
@Data
public class DepartInfo {

    private String id;

    /**
     * Parent name - child name.
     */
    private List<String> orgName;

    /**
     * Parent ID - child ID.
     */
    private List<String> orgId;

}