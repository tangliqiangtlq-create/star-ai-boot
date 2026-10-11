package org.jeecg.stream.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.service.IStreamVideoService;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@Slf4j
@RestController
@RequestMapping("/internal/stream/review")
@RequiredArgsConstructor
public class InternalReviewController {

    private final IStreamVideoService videoService;

    @ApiOperation("审核队列 (只给 admin 后台用)")
    @GetMapping("/queue")
    public Result<IPage<StreamVideo>> queue(Page<StreamVideo> page) {
        return Result.OK(videoService.page(page, new LambdaQueryWrapper<StreamVideo>()
            .in(StreamVideo::getPublishStatus, "pending_review", "pending")
            .orderByAsc(StreamVideo::getPublishedAt)));
    }

    @ApiOperation("审核通过")
    @PostMapping("/{id}/approve")
    public Result<String> approve(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, Object> body) {
        StreamVideo video = videoService.getById(id);
        if (video == null) return Result.error("视频不存在");
        video.setPublishStatus("published");
        video.setAutoReviewStatus("passed");
        video.setReviewedAt(new Date());
        if (body != null && body.get("reviewedBy") != null) {
            video.setReviewedBy(Long.valueOf(body.get("reviewedBy").toString()));
        }
        videoService.updateById(video);
        log.info("审核通过 videoId=" + id);
        return Result.OK("ok");
    }

    @ApiOperation("审核驳回")
    @PostMapping("/{id}/reject")
    public Result<String> reject(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, Object> body) {
        StreamVideo video = videoService.getById(id);
        if (video == null) return Result.error("视频不存在");
        video.setPublishStatus("rejected");
        video.setAutoReviewStatus("rejected");
        if (body != null && body.get("reason") != null) {
            video.setReviewNote(body.get("reason").toString());
        }
        video.setReviewedAt(new Date());
        videoService.updateById(video);
        log.info("审核驳回 videoId=" + id + " reason=" + video.getReviewNote());
        return Result.OK("ok");
    }
}
