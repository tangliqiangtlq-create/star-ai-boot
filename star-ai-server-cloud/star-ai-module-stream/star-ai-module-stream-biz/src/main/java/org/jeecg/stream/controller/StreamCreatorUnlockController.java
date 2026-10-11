package org.jeecg.stream.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.stream.entity.StreamCreatorUnlock;
import org.jeecg.stream.service.IStreamCreatorUnlockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

public class StreamCreatorUnlockController {
    @Autowired
    private IStreamCreatorUnlockService streamCreatorUnlockService;

    @ApiOperation("分页查询")
    @GetMapping("/list")
    public Result<IPage<StreamCreatorUnlock>> list(StreamCreatorUnlock query, Page<StreamCreatorUnlock> page, HttpServletRequest req) {
        return Result.OK(streamCreatorUnlockService.page(page, QueryGenerator.initQueryWrapper(query, req.getParameterMap())));
    }

    @ApiOperation("新增")
    @PostMapping("/add")
    public Result<String> add(@RequestBody StreamCreatorUnlock entity) {
        streamCreatorUnlockService.save(entity);
        return Result.OK("ok");
    }

    @ApiOperation("修改")
    @PostMapping("/edit")
    public Result<String> edit(@RequestBody StreamCreatorUnlock entity) {
        streamCreatorUnlockService.updateById(entity);
        return Result.OK("ok");
    }

    @ApiOperation("删除")
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam Long id) {
        streamCreatorUnlockService.removeById(id);
        return Result.OK("ok");
    }

    @ApiOperation("根据ID查询")
    @GetMapping("/queryById")
    public Result<StreamCreatorUnlock> queryById(@RequestParam Long id) {
        return Result.OK(streamCreatorUnlockService.getById(id));
    }
}
