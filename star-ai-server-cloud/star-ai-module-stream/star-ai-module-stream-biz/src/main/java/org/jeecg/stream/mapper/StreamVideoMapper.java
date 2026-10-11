package org.jeecg.stream.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.stream.entity.StreamVideo;

public interface StreamVideoMapper extends BaseMapper<StreamVideo> {

    @Select("SELECT COALESCE(SUM(play_count), 0) FROM stream_video WHERE creator_id = #{creatorId} AND publish_status = 'published'")
    Long sumPlayCountByCreatorId(@Param("creatorId") Long creatorId);
}
