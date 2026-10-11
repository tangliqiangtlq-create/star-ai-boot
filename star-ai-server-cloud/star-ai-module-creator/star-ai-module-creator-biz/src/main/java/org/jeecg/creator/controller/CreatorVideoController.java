package org.jeecg.creator.controller;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.creator.dto.PresignReq;
import org.jeecg.creator.dto.PresignResp;
import org.jeecg.creator.dto.VideoPublishReq;
import org.jeecg.creator.service.IMinioPresignService;
import org.jeecg.creator.service.IVideoPublishService;
import org.jeecg.creator.service.ICreatorOnboardingService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.service.IStreamVideoService;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/creator/videos")
@RequiredArgsConstructor
public class CreatorVideoController {
    private final IMinioPresignService presignService;
    private final IVideoPublishService publishService;
    private final ICreatorOnboardingService onboardingService;
    private final IStreamVideoService streamVideoService;

    @ApiOperation("MinIO 分片上传预签名 URL")
    @PostMapping("/upload-init")
    public Result<PresignResp> uploadInit(@RequestBody PresignReq req) {
        return Result.OK(presignService.generatePresign(req));
    }

    @ApiOperation("发布视频 (核心: 固化分润快照 + 转码 MQ)")
    @PostMapping("/publish")
    public Result<StreamVideo> publish(@RequestBody VideoPublishReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        req.setCreatorId(userId);
        return Result.OK(publishService.publish(req));
    }

    @ApiOperation("我的视频列表")
    @GetMapping
    public Result<IPage<StreamVideo>> myVideos(Page<StreamVideo> page) {
        Long userId = StpUtil.getLoginIdAsLong();
        var account = onboardingService.getByUserId(userId);
        if (account == null) return Result.OK(page);
        return Result.OK(streamVideoService.page(page, new LambdaQueryWrapper<StreamVideo>()
            .eq(StreamVideo::getCreatorId, account.getId())
            .orderByDesc(StreamVideo::getCreateTime)));
    }

    @ApiOperation("视频详情")
    @GetMapping("/{id}")
    public Result<StreamVideo> detail(@PathVariable Long id) {
        return Result.OK(streamVideoService.getById(id));
    }
}
