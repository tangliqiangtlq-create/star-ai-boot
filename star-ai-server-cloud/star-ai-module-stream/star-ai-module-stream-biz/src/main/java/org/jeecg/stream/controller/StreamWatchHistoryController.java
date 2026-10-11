package org.jeecg.stream.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.stream.entity.StreamVideoPlayHistory;
import org.jeecg.stream.service.IStreamPlayService;
import org.jeecg.stream.service.IStreamVideoPlayHistoryService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/stream/history")
@RequiredArgsConstructor
public class StreamWatchHistoryController {

    private final IStreamPlayService playService;
    private final IStreamVideoPlayHistoryService historyService;

    @ApiOperation("更新播放进度")
    @PostMapping("/{videoId}/progress")
    public Result<String> progress(@PathVariable Long videoId,
                                   @RequestBody(required = false) java.util.Map<String, Integer> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        int pos = body != null && body.get("positionSec") != null ? body.get("positionSec") : 0;
        StreamVideoPlayHistory h = historyService.getOne(new LambdaQueryWrapper<StreamVideoPlayHistory>()
            .eq(StreamVideoPlayHistory::getUserId, userId)
            .eq(StreamVideoPlayHistory::getVideoId, videoId));
        if (h == null) {
            h = new StreamVideoPlayHistory();
            h.setUserId(userId); h.setVideoId(videoId);
            h.setPositionSec(pos); h.setTotalWatchedSec(pos);
            h.setPlayCount(1); h.setLastPlayedAt(new java.util.Date());
            historyService.save(h);
        } else {
            h.setPositionSec(pos);
            if (h.getTotalWatchedSec() == null) h.setTotalWatchedSec(0);
            h.setTotalWatchedSec(h.getTotalWatchedSec() + pos);
            h.setLastPlayedAt(new java.util.Date());
            historyService.updateById(h);
        }
        return Result.OK("ok");
    }

    @ApiOperation("播放结束埋点 (算有效播放)")
    @PostMapping("/{videoId}/end")
    public Result<String> watchEnd(@PathVariable Long videoId,
                                   @RequestBody(required = false) java.util.Map<String, Integer> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        int duration = body != null && body.get("durationSec") != null ? body.get("durationSec") : 0;
        playService.recordPlayEnd(userId, videoId, duration);
        return Result.OK("ok");
    }

    @ApiOperation("我的观看历史")
    @GetMapping
    public Result<IPage<StreamVideoPlayHistory>> mine(Page<StreamVideoPlayHistory> page) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.OK(historyService.page(page, new LambdaQueryWrapper<StreamVideoPlayHistory>()
            .eq(StreamVideoPlayHistory::getUserId, userId)
            .orderByDesc(StreamVideoPlayHistory::getLastPlayedAt)));
    }
}
