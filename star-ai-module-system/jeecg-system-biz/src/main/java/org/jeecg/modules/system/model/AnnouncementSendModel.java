package org.jeecg.modules.system.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * @Description: User announcement read-mark table
 * @Author: jeecg-boot
 * @Date:  2019-02-21
 * @Version: V1.0
 */
@Data
public class AnnouncementSendModel implements Serializable {
    private static final long serialVersionUID = 1L;

	/**id*/
	@TableId(type = IdType.ASSIGN_ID)
	private java.lang.String id;
	/**Announcement id*/
	private java.lang.String anntId;
	/**Userid*/
	private java.lang.String userId;
	/**Title*/
	private java.lang.String titile;
	/**Content*/
	private java.lang.String msgContent;
	/**Publisher*/
	private java.lang.String sender;
	/**Priority (L=low, M=medium, H=high)*/
	private java.lang.String priority;
	/**Read status*/
	private java.lang.Integer readFlag;
	/**Publish time*/
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
	private java.util.Date sendTime;
	/**Page count*/
	private java.lang.Integer pageNo;
	/**Size*/
	private java.lang.Integer pageSize;
	/**
	 * B12-2: Derived LIMIT offset / pageSize used in the paginated inner-query SQL; populated from
	 * (pageNo-1)*pageSize by the service layer and passed through to the XML SQL.
	 */
	private transient java.lang.Integer offset;
	private transient java.lang.Integer size;
    /**
     * MessageType1:NotificationAnnouncement2:SystemMessage
     */
    private java.lang.String msgCategory;
	/**
	 * Businessid
	 */
	private java.lang.String busId;
	/**
	 * BusinessType
	 */
	private java.lang.String busType;
	/**
	 * Open mode: component or url route
	 */
	private java.lang.String openType;
	/**
	 * Component/route path
	 */
	private java.lang.String openPage;

	/**
	 * Business type query (0 = non-BPM business)
	 */
	private java.lang.String bizSource;

	/**
	 * Summary
	 */
	private java.lang.String msgAbstract;

}
