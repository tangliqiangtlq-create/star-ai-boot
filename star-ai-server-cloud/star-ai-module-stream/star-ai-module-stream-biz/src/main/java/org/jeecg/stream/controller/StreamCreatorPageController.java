package org.jeecg.stream.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.mapper.StreamVideoMapper;
import org.jeecg.stream.service.IStreamVideoService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/stream/creators")
@RequiredArgsConstructor
public class StreamCreatorPageController {

    private final IStreamVideoService videoService;
    private final StreamVideoMapper videoMapper;

    @ApiOperation("创作者主页聚合信息 (视频数 + 总播放量)")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> info(@PathVariable Long id) {
        Long videoCount = videoService.count(new LambdaQueryWrapper<StreamVideo>()
            .eq(StreamVideo::getCreatorId, id)
            .eq(StreamVideo::getPublishStatus, "published"));
        Long totalPlays = videoMapper.sumPlayCountByCreatorId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("creatorId", id);
        result.put("videoCount", videoCount);
        result.put("totalPlayCount", totalPlays != null ? totalPlays : 0L);
        return Result.OK(result);
    }

    @ApiOperation("创作者视频列表")
    @GetMapping("/{id}/videos")
    public Result<IPage<StreamVideo>> videos(@PathVariable Long id, Page<StreamVideo> page) {
        return Result.OK(videoService.page(page, new LambdaQueryWrapper<StreamVideo>()
            .eq(StreamVideo::getCreatorId, id)
            .eq(StreamVideo::getPublishStatus, "published")
            .orderByDesc(StreamVideo::getPublishedAt)));
    }
}
