package org.jeecg.stream.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.stp.StpUtil;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.dto.VideoListQuery;
import org.jeecg.stream.dto.PlayPermission;
import org.jeecg.stream.service.IPlayPermissionService;
import org.jeecg.stream.service.IStreamVideoService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/stream/videos")
@RequiredArgsConstructor
public class StreamBrowseController {

    private final IStreamVideoService videoService;
    private final IPlayPermissionService permissionService;

    @ApiOperation("首页视频列表 (已发布)")
    @GetMapping
    public Result<IPage<StreamVideo>> list(VideoListQuery query) {
        Page<StreamVideo> page = new Page<>(
            query.getPageNum() != null ? query.getPageNum() : 1,
            query.getPageSize() != null ? query.getPageSize() : 20
        );
        LambdaQueryWrapper<StreamVideo> w = new LambdaQueryWrapper<>();
        w.eq(StreamVideo::getPublishStatus, "published")
         .eq(StreamVideo::getTranscodeStatus, "completed");
        if (query.getCategoryId() != null) {
            w.eq(StreamVideo::getCategoryId, query.getCategoryId());
        }
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            w.like(StreamVideo::getTitle, query.getKeyword());
        }
        if (query.getSortBy() != null && query.getSortBy().equals("play_count")) {
            w.orderByDesc(StreamVideo::getPlayCount);
        } else {
            w.orderByDesc(StreamVideo::getPublishedAt);
        }
        return Result.OK(videoService.page(page, w));
    }

    @ApiOperation("视频详情 + 播放权限判断")
    @GetMapping("/{id}")
    public Result<StreamVideo> detail(@PathVariable Long id) {
        StreamVideo video = videoService.getById(id);
        if (video == null || !"published".equals(video.getPublishStatus())) {
            return Result.error("视频不存在或未发布");
        }
        Long userId = null;
        try { userId = StpUtil.getLoginIdAsLong(); } catch (Exception ignored) {}
        PlayPermission pp = userId != null ? permissionService.check(userId, id) : PlayPermission.locked(video.getPriceType(), video.getPriceAmount(), video.getPreviewSec());
        log.info("播放权限: userId={}, videoId={}, granted={}, type={}", userId, id, pp.isGranted(), pp.getPermissionType());
        return Result.OK(video);
    }
}
