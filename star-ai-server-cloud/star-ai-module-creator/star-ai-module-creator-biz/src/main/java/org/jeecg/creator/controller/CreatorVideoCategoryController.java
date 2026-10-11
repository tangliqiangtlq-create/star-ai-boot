package org.jeecg.creator.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.creator.entity.CreatorVideoCategory;
import org.jeecg.creator.service.ICreatorVideoCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

public class CreatorVideoCategoryController {
    @Autowired
    private ICreatorVideoCategoryService creatorVideoCategoryService;

    @ApiOperation("分页查询")
    @GetMapping("/list")
    public Result<IPage<CreatorVideoCategory>> list(CreatorVideoCategory query, Page<CreatorVideoCategory> page, HttpServletRequest req) {
        return Result.OK(creatorVideoCategoryService.page(page, QueryGenerator.initQueryWrapper(query, req.getParameterMap())));
    }

    @ApiOperation("新增")
    @PostMapping("/add")
    public Result<String> add(@RequestBody CreatorVideoCategory entity) {
        creatorVideoCategoryService.save(entity);
        return Result.OK("ok");
    }

    @ApiOperation("修改")
    @PostMapping("/edit")
    public Result<String> edit(@RequestBody CreatorVideoCategory entity) {
        creatorVideoCategoryService.updateById(entity);
        return Result.OK("ok");
    }

    @ApiOperation("删除")
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam Long id) {
        creatorVideoCategoryService.removeById(id);
        return Result.OK("ok");
    }

    @ApiOperation("根据ID查询")
    @GetMapping("/queryById")
    public Result<CreatorVideoCategory> queryById(@RequestParam Long id) {
        return Result.OK(creatorVideoCategoryService.getById(id));
    }
}
