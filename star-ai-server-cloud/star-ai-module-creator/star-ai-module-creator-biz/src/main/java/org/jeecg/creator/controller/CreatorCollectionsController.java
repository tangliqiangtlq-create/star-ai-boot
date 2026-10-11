package org.jeecg.creator.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.stream.entity.StreamVideoCollection;
import org.jeecg.stream.service.IStreamVideoCollectionService;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/creator/collections")
@RequiredArgsConstructor
public class CreatorCollectionsController {

    private final IStreamVideoCollectionService collectionService;

    @ApiOperation("我的剧集列表")
    @GetMapping
    public Result<IPage<StreamVideoCollection>> my(Page<StreamVideoCollection> page) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.OK(collectionService.page(page, new LambdaQueryWrapper<StreamVideoCollection>()
            .eq(StreamVideoCollection::getCreatorId, userId)
            .orderByDesc(StreamVideoCollection::getCreateTime)));
    }

    @ApiOperation("创建剧集")
    @PostMapping
    public Result<StreamVideoCollection> create(@RequestBody StreamVideoCollection collection) {
        Long userId = StpUtil.getLoginIdAsLong();
        collection.setCreatorId(userId);
        collectionService.save(collection);
        return Result.OK(collection);
    }

    @ApiOperation("更新剧集")
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Long id, @RequestBody StreamVideoCollection collection) {
        collection.setId(id);
        collectionService.updateById(collection);
        return Result.OK("ok");
    }

    @ApiOperation("删除剧集")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        collectionService.removeById(id);
        return Result.OK("ok");
    }

    @ApiOperation("剧集视频列表")
    @GetMapping("/{id}/videos")
    public Result<?> videos(@PathVariable Long id) {
        return Result.OK(collectionService.getById(id));
    }
}
