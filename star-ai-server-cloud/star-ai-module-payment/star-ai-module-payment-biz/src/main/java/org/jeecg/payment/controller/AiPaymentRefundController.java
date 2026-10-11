package org.jeecg.payment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.payment.entity.AiPaymentRefund;
import org.jeecg.payment.service.IAiPaymentRefundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

public class AiPaymentRefundController {
    @Autowired
    private IAiPaymentRefundService aiPaymentRefundService;

    @ApiOperation("分页查询")
    @GetMapping("/list")
    public Result<IPage<AiPaymentRefund>> list(AiPaymentRefund query, Page<AiPaymentRefund> page, HttpServletRequest req) {
        return Result.OK(aiPaymentRefundService.page(page, QueryGenerator.initQueryWrapper(query, req.getParameterMap())));
    }

    @ApiOperation("新增")
    @PostMapping("/add")
    public Result<String> add(@RequestBody AiPaymentRefund entity) {
        aiPaymentRefundService.save(entity);
        return Result.OK("ok");
    }

    @ApiOperation("修改")
    @PostMapping("/edit")
    public Result<String> edit(@RequestBody AiPaymentRefund entity) {
        aiPaymentRefundService.updateById(entity);
        return Result.OK("ok");
    }

    @ApiOperation("删除")
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam Long id) {
        aiPaymentRefundService.removeById(id);
        return Result.OK("ok");
    }

    @ApiOperation("根据ID查询")
    @GetMapping("/queryById")
    public Result<AiPaymentRefund> queryById(@RequestParam Long id) {
        return Result.OK(aiPaymentRefundService.getById(id));
    }
}
