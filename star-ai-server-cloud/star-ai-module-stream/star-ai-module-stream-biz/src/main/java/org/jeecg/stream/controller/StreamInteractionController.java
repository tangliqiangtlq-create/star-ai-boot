package org.jeecg.stream.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.entity.StreamVideoInteraction;
import org.jeecg.stream.mapper.StreamVideoInteractionMapper;
import org.jeecg.stream.service.IStreamVideoService;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/stream/interactions")
@RequiredArgsConstructor
public class StreamInteractionController {

    private final StreamVideoInteractionMapper interactionMapper;
    private final IStreamVideoService videoService;

    @ApiOperation("点赞视频")
    @PostMapping("/like/{videoId}")
    public Result<String> like(@PathVariable Long videoId) {
        Long userId = StpUtil.getLoginIdAsLong();
        StreamVideoInteraction exist = interactionMapper.selectOne(new LambdaQueryWrapper<StreamVideoInteraction>()
            .eq(StreamVideoInteraction::getUserId, userId)
            .eq(StreamVideoInteraction::getVideoId, videoId)
            .eq(StreamVideoInteraction::getInteractionType, "like"));
        if (exist == null) {
            StreamVideoInteraction inter = new StreamVideoInteraction();
            inter.setUserId(userId); inter.setVideoId(videoId);
            inter.setInteractionType("like");
            interactionMapper.insert(inter);
            StreamVideo v = videoService.getById(videoId);
            if (v != null) { v.setLikeCount(v.getLikeCount() != null ? v.getLikeCount() + 1 : 1); videoService.updateById(v); }
        }
        return Result.OK("ok");
    }

    @ApiOperation("取消点赞")
    @DeleteMapping("/like/{videoId}")
    public Result<String> unlike(@PathVariable Long videoId) {
        Long userId = StpUtil.getLoginIdAsLong();
        interactionMapper.delete(new LambdaQueryWrapper<StreamVideoInteraction>()
            .eq(StreamVideoInteraction::getUserId, userId)
            .eq(StreamVideoInteraction::getVideoId, videoId)
            .eq(StreamVideoInteraction::getInteractionType, "like"));
        StreamVideo v = videoService.getById(videoId);
        if (v != null && v.getLikeCount() != null && v.getLikeCount() > 0) {
            v.setLikeCount(v.getLikeCount() - 1); videoService.updateById(v);
        }
        return Result.OK("ok");
    }

    @ApiOperation("收藏视频")
    @PostMapping("/favorite/{videoId}")
    public Result<String> favorite(@PathVariable Long videoId) {
        Long userId = StpUtil.getLoginIdAsLong();
        StreamVideoInteraction exist = interactionMapper.selectOne(new LambdaQueryWrapper<StreamVideoInteraction>()
            .eq(StreamVideoInteraction::getUserId, userId)
            .eq(StreamVideoInteraction::getVideoId, videoId)
            .eq(StreamVideoInteraction::getInteractionType, "favorite"));
        if (exist == null) {
            StreamVideoInteraction inter = new StreamVideoInteraction();
            inter.setUserId(userId); inter.setVideoId(videoId);
            inter.setInteractionType("favorite");
            interactionMapper.insert(inter);
            StreamVideo v = videoService.getById(videoId);
            if (v != null) { v.setFavoriteCount(v.getFavoriteCount() != null ? v.getFavoriteCount() + 1 : 1); videoService.updateById(v); }
        }
        return Result.OK("ok");
    }

    @ApiOperation("取消收藏")
    @DeleteMapping("/favorite/{videoId}")
    public Result<String> unfavorite(@PathVariable Long videoId) {
        Long userId = StpUtil.getLoginIdAsLong();
        interactionMapper.delete(new LambdaQueryWrapper<StreamVideoInteraction>()
            .eq(StreamVideoInteraction::getUserId, userId)
            .eq(StreamVideoInteraction::getVideoId, videoId)
            .eq(StreamVideoInteraction::getInteractionType, "favorite"));
        StreamVideo v = videoService.getById(videoId);
        if (v != null && v.getFavoriteCount() != null && v.getFavoriteCount() > 0) {
            v.setFavoriteCount(v.getFavoriteCount() - 1); videoService.updateById(v);
        }
        return Result.OK("ok");
    }
}
