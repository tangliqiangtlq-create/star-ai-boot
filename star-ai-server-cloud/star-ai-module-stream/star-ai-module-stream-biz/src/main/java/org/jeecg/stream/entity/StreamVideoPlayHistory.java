package org.jeecg.stream.entity;

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
@TableName("stream_video_play_history")
public class StreamVideoPlayHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long videoId;

    /** 断点位置 */
    private Integer positionSec;

    /** 累计观看时长 */
    private Integer totalWatchedSec;

    private Integer playCount;

    /** 是否算有效播放 (total_watched_sec >= subscription_plan.min_effective_sec 时为 1, 订阅池贡献计算入口) */
    private Integer isEffective;

    /** 完播率 (= total_watched_sec / video.duration_sec, NULL=还没看完首段) */
    private BigDecimal completionRate;

    /** 观看时用户是否是 active 订阅会员 (订阅池贡献计算入口) */
    private Integer isMemberView;

    private Date lastPlayedAt;

    private Date createTime;

    private Date updateTime;

}
