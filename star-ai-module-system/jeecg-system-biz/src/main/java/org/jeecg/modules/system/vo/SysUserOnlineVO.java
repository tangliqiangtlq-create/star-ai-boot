package org.jeecg.modules.system.vo;

import java.util.Date;

import org.jeecg.common.aspect.annotation.Dict;
import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 *
 * @Author: chenli
 * @Date: 2020-06-07
 * @Version: V1.0
 */
@Data
public class SysUserOnlineVO {
    /**
     * Session ID.
     */
    private String id;

    /**
     * Session number.
     */
    private String token;

    /**
     * Username
     */
    private String username;

    /**
     * Username
     */
    private String realname;

    /**
     * Avatar
     */
    private String avatar;

    /**
     * Birthday
     */
    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date birthday;

    /**
     * Gender (1: male 2: female)
     */
    @Dict(dicCode = "sex")
    private Integer sex;

    /**
     * Phone number
     */
    private String phone;
}
