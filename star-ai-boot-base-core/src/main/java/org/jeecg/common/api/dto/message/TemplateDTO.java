package org.jeecg.common.api.dto.message;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * messagetemplatedto
 * @author: jeecg-boot
 */
@Data
public class TemplateDTO implements Serializable {

    private static final long serialVersionUID = 5848247133907528650L;

    /**
     * Template code
     */
    protected String templateCode;

    /**
     * Template parameters
     */
    protected Map<String, String> templateParam;

    /**
     * Constructor: obtain message content via template parameters and template code
     */
    public TemplateDTO(String templateCode, Map<String, String> templateParam){
        this.templateCode = templateCode;
        this.templateParam = templateParam;
    }

    public TemplateDTO(){

    }
}
