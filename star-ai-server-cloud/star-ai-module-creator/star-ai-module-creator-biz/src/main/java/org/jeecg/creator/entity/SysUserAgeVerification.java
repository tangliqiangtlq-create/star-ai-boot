package org.jeecg.creator.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_user_age_verification")
public class SysUserAgeVerification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 sys_user.id */
    private Long userId;

    /** 0=未验证, 1=已成年 */
    private Integer ageVerified;

    /** id_card/kyc/sumsum/jumio/creditcard_preauth */
    private String ageVerifyMethod;

    /** 验证时间 */
    private Date ageVerifiedAt;

    /** 第三方 KYC 引用ID */
    private String ageVerifyRef;

    /** cn/sg/us */
    private String region;

    private Date createTime;

    private Date updateTime;

}
