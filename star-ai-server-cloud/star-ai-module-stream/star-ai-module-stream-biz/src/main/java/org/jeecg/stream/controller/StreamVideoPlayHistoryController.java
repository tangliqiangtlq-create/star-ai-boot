package org.jeecg.stream.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.stream.entity.StreamVideoPlayHistory;
import org.jeecg.stream.service.IStreamVideoPlayHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

public class StreamVideoPlayHistoryController {
    @Autowired
    private IStreamVideoPlayHistoryService streamVideoPlayHistoryService;

    @ApiOperation("分页查询")
    @GetMapping("/list")
    public Result<IPage<StreamVideoPlayHistory>> list(StreamVideoPlayHistory query, Page<StreamVideoPlayHistory> page, HttpServletRequest req) {
        return Result.OK(streamVideoPlayHistoryService.page(page, QueryGenerator.initQueryWrapper(query, req.getParameterMap())));
    }

    @ApiOperation("新增")
    @PostMapping("/add")
    public Result<String> add(@RequestBody StreamVideoPlayHistory entity) {
        streamVideoPlayHistoryService.save(entity);
        return Result.OK("ok");
    }

    @ApiOperation("修改")
    @PostMapping("/edit")
    public Result<String> edit(@RequestBody StreamVideoPlayHistory entity) {
        streamVideoPlayHistoryService.updateById(entity);
        return Result.OK("ok");
    }

    @ApiOperation("删除")
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam Long id) {
        streamVideoPlayHistoryService.removeById(id);
        return Result.OK("ok");
    }

    @ApiOperation("根据ID查询")
    @GetMapping("/queryById")
    public Result<StreamVideoPlayHistory> queryById(@RequestParam Long id) {
        return Result.OK(streamVideoPlayHistoryService.getById(id));
    }
}
