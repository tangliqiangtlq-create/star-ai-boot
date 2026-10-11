package org.jeecg.common.system.vo;

import lombok.Data;
import org.springframework.beans.BeanUtils;

/**
 * @Description: data source
 * @author: jeecg-boot
 */
@Data
public class DynamicDataSourceModel {

    public DynamicDataSourceModel() {

    }

    public DynamicDataSourceModel(Object dbSource) {
        if (dbSource != null) {
            BeanUtils.copyProperties(dbSource, this);
        }
    }

    /**
     * id
     */
    private java.lang.String id;
    /**
     * Data source code
     */
    private java.lang.String code;
    /**
     * DatabaseType
     */
    private java.lang.String dbType;
    /**
     * Driver class
     */
    private java.lang.String dbDriver;
    /**
     * Data source address
     */
    private java.lang.String dbUrl;

//    /**
//     * DatabaseName
//     */
//    private java.lang.String dbName;

    /**
     * Username
     */
    private java.lang.String dbUsername;
    /**
     * Password
     */
    private java.lang.String dbPassword;

}