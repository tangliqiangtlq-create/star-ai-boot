package org.jeecg.payment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.payment.entity.AiPaymentChannel;
import org.jeecg.payment.service.IAiPaymentChannelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

public class AiPaymentChannelController {
    @Autowired
    private IAiPaymentChannelService aiPaymentChannelService;

    @ApiOperation("分页查询")
    @GetMapping("/list")
    public Result<IPage<AiPaymentChannel>> list(AiPaymentChannel query, Page<AiPaymentChannel> page, HttpServletRequest req) {
        return Result.OK(aiPaymentChannelService.page(page, QueryGenerator.initQueryWrapper(query, req.getParameterMap())));
    }

    @ApiOperation("新增")
    @PostMapping("/add")
    public Result<String> add(@RequestBody AiPaymentChannel entity) {
        aiPaymentChannelService.save(entity);
        return Result.OK("ok");
    }

    @ApiOperation("修改")
    @PostMapping("/edit")
    public Result<String> edit(@RequestBody AiPaymentChannel entity) {
        aiPaymentChannelService.updateById(entity);
        return Result.OK("ok");
    }

    @ApiOperation("删除")
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam Long id) {
        aiPaymentChannelService.removeById(id);
        return Result.OK("ok");
    }

    @ApiOperation("根据ID查询")
    @GetMapping("/queryById")
    public Result<AiPaymentChannel> queryById(@RequestParam Long id) {
        return Result.OK(aiPaymentChannelService.getById(id));
    }
}
