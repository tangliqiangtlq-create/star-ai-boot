package org.jeecg.modules.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_template_edit_log")
public class SysTemplateEditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @Excel(name = "template_id", width = 36)
    private String templateId;

    @TableField("old_content")
    private String oldContent;

    @TableField("new_content")
    private String newContent;

    @Excel(name = "editor_user_id", width = 36)
    private String editorUserId;

    @Excel(name = "editor_username", width = 32)
    private String editorUsername;

    @Excel(name = "approved_by", width = 36)
    private String approvedBy;

    @Dict(dicCode = "template_edit_approval_status")
    @Excel(name = "status", width = 16)
    private String status;

    @Excel(name = "reject_reason", width = 128)
    private String rejectReason;

    @Excel(name = "template_code", width = 32)
    private String templateCode;

    @Excel(name = "change_summary", width = 256)
    private String changeSummary;

    private String tenantId;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
}
