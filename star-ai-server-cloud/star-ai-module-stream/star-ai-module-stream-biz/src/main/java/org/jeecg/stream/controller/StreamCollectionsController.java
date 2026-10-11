package org.jeecg.stream.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.entity.StreamVideoCollection;
import org.jeecg.stream.entity.StreamVideoCollectionRel;
import org.jeecg.stream.mapper.StreamVideoCollectionRelMapper;
import org.jeecg.stream.service.IStreamVideoCollectionService;
import org.jeecg.stream.service.IStreamVideoService;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/stream/collections")
@RequiredArgsConstructor
public class StreamCollectionsController {

    private final IStreamVideoCollectionService collectionService;
    private final IStreamVideoService videoService;
    private final StreamVideoCollectionRelMapper relMapper;

    @ApiOperation("剧集列表")
    @GetMapping
    public Result<IPage<StreamVideoCollection>> list(Page<StreamVideoCollection> page) {
        return Result.OK(collectionService.page(page, new LambdaQueryWrapper<StreamVideoCollection>()
            .orderByDesc(StreamVideoCollection::getCreateTime)));
    }

    @ApiOperation("剧集详情")
    @GetMapping("/{id}")
    public Result<StreamVideoCollection> detail(@PathVariable Long id) {
        return Result.OK(collectionService.getById(id));
    }

    @ApiOperation("剧集下视频 (按集数排序)")
    @GetMapping("/{id}/videos")
    public Result<IPage<StreamVideoCollectionRel>> videos(@PathVariable Long id, Page<StreamVideoCollectionRel> page) {
        IPage<StreamVideoCollectionRel> rels = relMapper.selectPage(page, new LambdaQueryWrapper<StreamVideoCollectionRel>()
            .eq(StreamVideoCollectionRel::getCollectionId, id)
            .orderByAsc(StreamVideoCollectionRel::getEpisodeNumber));
        return Result.OK(rels);
    }
}
