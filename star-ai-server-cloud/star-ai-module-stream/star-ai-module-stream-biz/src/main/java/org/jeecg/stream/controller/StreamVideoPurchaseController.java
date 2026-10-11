package org.jeecg.stream.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.stream.entity.StreamVideoPurchase;
import org.jeecg.stream.service.IStreamVideoPurchaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

public class StreamVideoPurchaseController {
    @Autowired
    private IStreamVideoPurchaseService streamVideoPurchaseService;

    @ApiOperation("分页查询")
    @GetMapping("/list")
    public Result<IPage<StreamVideoPurchase>> list(StreamVideoPurchase query, Page<StreamVideoPurchase> page, HttpServletRequest req) {
        return Result.OK(streamVideoPurchaseService.page(page, QueryGenerator.initQueryWrapper(query, req.getParameterMap())));
    }

    @ApiOperation("新增")
    @PostMapping("/add")
    public Result<String> add(@RequestBody StreamVideoPurchase entity) {
        streamVideoPurchaseService.save(entity);
        return Result.OK("ok");
    }

    @ApiOperation("修改")
    @PostMapping("/edit")
    public Result<String> edit(@RequestBody StreamVideoPurchase entity) {
        streamVideoPurchaseService.updateById(entity);
        return Result.OK("ok");
    }

    @ApiOperation("删除")
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam Long id) {
        streamVideoPurchaseService.removeById(id);
        return Result.OK("ok");
    }

    @ApiOperation("根据ID查询")
    @GetMapping("/queryById")
    public Result<StreamVideoPurchase> queryById(@RequestParam Long id) {
        return Result.OK(streamVideoPurchaseService.getById(id));
    }
}
