package org.jeecg.stream.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.stream.entity.StreamVideoCollectionRel;
import org.jeecg.stream.service.IStreamVideoCollectionRelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

public class StreamVideoCollectionRelController {
    @Autowired
    private IStreamVideoCollectionRelService streamVideoCollectionRelService;

    @ApiOperation("分页查询")
    @GetMapping("/list")
    public Result<IPage<StreamVideoCollectionRel>> list(StreamVideoCollectionRel query, Page<StreamVideoCollectionRel> page, HttpServletRequest req) {
        return Result.OK(streamVideoCollectionRelService.page(page, QueryGenerator.initQueryWrapper(query, req.getParameterMap())));
    }

    @ApiOperation("新增")
    @PostMapping("/add")
    public Result<String> add(@RequestBody StreamVideoCollectionRel entity) {
        streamVideoCollectionRelService.save(entity);
        return Result.OK("ok");
    }

    @ApiOperation("修改")
    @PostMapping("/edit")
    public Result<String> edit(@RequestBody StreamVideoCollectionRel entity) {
        streamVideoCollectionRelService.updateById(entity);
        return Result.OK("ok");
    }

    @ApiOperation("删除")
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam Long id) {
        streamVideoCollectionRelService.removeById(id);
        return Result.OK("ok");
    }

    @ApiOperation("根据ID查询")
    @GetMapping("/queryById")
    public Result<StreamVideoCollectionRel> queryById(@RequestParam Long id) {
        return Result.OK(streamVideoCollectionRelService.getById(id));
    }
}
