package org.jeecg.common.system.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.desensitization.annotation.SensitiveField;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * <p>
 * OnlineUser info
 * </p>
 *
 * @Author scott
 * @since 2023-08-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class UserAccountInfo {

    /**
     * Logged-in user id
     */
    private String id;

    /**
     * Logged-in user account
     */
    private String username;

    /**
     * Logged-in user name
     */
    private String realname;

    /**
     * Email
     */
    private String email;

    /**
     * Avatar
     */
    @SensitiveField
    private String avatar;

    /**
     * Sync workflow engine: 1 sync, 0 no sync
     */
    private Integer activitiSync;

    /**
     * Phone
     */
    @SensitiveField
    private String phone;
}
