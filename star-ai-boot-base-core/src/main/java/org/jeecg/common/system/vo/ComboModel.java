package org.jeecg.common.system.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @Description: Document management
 * @author: jeecg-boot
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComboModel implements Serializable {
    private String id;
    private String title;
    /**Document management formtableSelected by default*/
    private boolean checked;
    /**Document management formtable User account*/
    private String username;
    /**Document management formtable userMail*/
    private String email;
    /**Document management formtable Role code*/
    private String roleCode;

    public ComboModel(){

    };

    public ComboModel(String id,String title,boolean checked,String username){
        this.id = id;
        this.title = title;
        this.checked = false;
        this.username = username;
    };
}
