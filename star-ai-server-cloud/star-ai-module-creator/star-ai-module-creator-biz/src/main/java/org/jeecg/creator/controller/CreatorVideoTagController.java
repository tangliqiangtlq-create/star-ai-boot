package org.jeecg.creator.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.creator.entity.CreatorVideoTag;
import org.jeecg.creator.service.ICreatorVideoTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

public class CreatorVideoTagController {
    @Autowired
    private ICreatorVideoTagService creatorVideoTagService;

    @ApiOperation("分页查询")
    @GetMapping("/list")
    public Result<IPage<CreatorVideoTag>> list(CreatorVideoTag query, Page<CreatorVideoTag> page, HttpServletRequest req) {
        return Result.OK(creatorVideoTagService.page(page, QueryGenerator.initQueryWrapper(query, req.getParameterMap())));
    }

    @ApiOperation("新增")
    @PostMapping("/add")
    public Result<String> add(@RequestBody CreatorVideoTag entity) {
        creatorVideoTagService.save(entity);
        return Result.OK("ok");
    }

    @ApiOperation("修改")
    @PostMapping("/edit")
    public Result<String> edit(@RequestBody CreatorVideoTag entity) {
        creatorVideoTagService.updateById(entity);
        return Result.OK("ok");
    }

    @ApiOperation("删除")
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam Long id) {
        creatorVideoTagService.removeById(id);
        return Result.OK("ok");
    }

    @ApiOperation("根据ID查询")
    @GetMapping("/queryById")
    public Result<CreatorVideoTag> queryById(@RequestParam Long id) {
        return Result.OK(creatorVideoTagService.getById(id));
    }
}
