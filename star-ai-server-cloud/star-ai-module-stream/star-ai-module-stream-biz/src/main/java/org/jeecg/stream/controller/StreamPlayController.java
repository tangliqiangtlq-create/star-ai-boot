package org.jeecg.stream.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.dto.PlayPermission;
import org.jeecg.stream.service.IPlayPermissionService;
import org.jeecg.stream.service.IStreamPlayService;
import org.jeecg.stream.service.IStreamVideoService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/stream/play")
@RequiredArgsConstructor
public class StreamPlayController {

    private final IStreamVideoService videoService;
    private final IPlayPermissionService permissionService;
    private final IStreamPlayService playService;

    @ApiOperation("完整播放 HLS master URL (需付费权限)")
    @GetMapping("/{videoId}")
    public Result<String> playFull(@PathVariable Long videoId) {
        Long userId = StpUtil.getLoginIdAsLong();
        PlayPermission pp = permissionService.check(userId, videoId);
        if (!pp.isGranted()) {
            return Result.error(402, "需要付费购买才能完整播放");
        }
        playService.recordPlayStart(userId, videoId);
        return Result.OK(playService.getFullHlsUrl(videoId));
    }

    @ApiOperation("试看 HLS (preview_sec 秒) — 未付费也能看")
    @GetMapping("/{videoId}/preview")
    public Result<String> playPreview(@PathVariable Long videoId) {
        StreamVideo video = videoService.getById(videoId);
        if (video == null || video.getPreviewSec() == null || video.getPreviewSec() <= 0) {
            return Result.error("该视频不允许试看");
        }
        String url = playService.getPreviewHlsUrl(videoId);
        return Result.OK(url);
    }

    @ApiOperation("播放结束埋点")
    @PostMapping("/{videoId}/end")
    public Result<String> playEnd(@PathVariable Long videoId, @RequestBody(required = false) Integer durationSec) {
        Long userId = StpUtil.getLoginIdAsLong();
        playService.recordPlayEnd(userId, videoId, durationSec);
        return Result.OK("ok");
    }
}
