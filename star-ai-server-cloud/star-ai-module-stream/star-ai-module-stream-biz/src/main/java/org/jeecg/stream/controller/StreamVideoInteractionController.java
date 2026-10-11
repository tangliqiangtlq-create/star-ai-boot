package org.jeecg.stream.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.stream.entity.StreamVideoInteraction;
import org.jeecg.stream.service.IStreamVideoInteractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

public class StreamVideoInteractionController {
    @Autowired
    private IStreamVideoInteractionService streamVideoInteractionService;

    @ApiOperation("分页查询")
    @GetMapping("/list")
    public Result<IPage<StreamVideoInteraction>> list(StreamVideoInteraction query, Page<StreamVideoInteraction> page, HttpServletRequest req) {
        return Result.OK(streamVideoInteractionService.page(page, QueryGenerator.initQueryWrapper(query, req.getParameterMap())));
    }

    @ApiOperation("新增")
    @PostMapping("/add")
    public Result<String> add(@RequestBody StreamVideoInteraction entity) {
        streamVideoInteractionService.save(entity);
        return Result.OK("ok");
    }

    @ApiOperation("修改")
    @PostMapping("/edit")
    public Result<String> edit(@RequestBody StreamVideoInteraction entity) {
        streamVideoInteractionService.updateById(entity);
        return Result.OK("ok");
    }

    @ApiOperation("删除")
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam Long id) {
        streamVideoInteractionService.removeById(id);
        return Result.OK("ok");
    }

    @ApiOperation("根据ID查询")
    @GetMapping("/queryById")
    public Result<StreamVideoInteraction> queryById(@RequestParam Long id) {
        return Result.OK(streamVideoInteractionService.getById(id));
    }
}
