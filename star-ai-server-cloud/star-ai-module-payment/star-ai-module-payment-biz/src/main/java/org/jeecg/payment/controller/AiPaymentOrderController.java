package org.jeecg.payment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.payment.entity.AiPaymentOrder;
import org.jeecg.payment.service.IAiPaymentOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

public class AiPaymentOrderController {
    @Autowired
    private IAiPaymentOrderService aiPaymentOrderService;

    @ApiOperation("分页查询")
    @GetMapping("/list")
    public Result<IPage<AiPaymentOrder>> list(AiPaymentOrder query, Page<AiPaymentOrder> page, HttpServletRequest req) {
        return Result.OK(aiPaymentOrderService.page(page, QueryGenerator.initQueryWrapper(query, req.getParameterMap())));
    }

    @ApiOperation("新增")
    @PostMapping("/add")
    public Result<String> add(@RequestBody AiPaymentOrder entity) {
        aiPaymentOrderService.save(entity);
        return Result.OK("ok");
    }

    @ApiOperation("修改")
    @PostMapping("/edit")
    public Result<String> edit(@RequestBody AiPaymentOrder entity) {
        aiPaymentOrderService.updateById(entity);
        return Result.OK("ok");
    }

    @ApiOperation("删除")
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam Long id) {
        aiPaymentOrderService.removeById(id);
        return Result.OK("ok");
    }

    @ApiOperation("根据ID查询")
    @GetMapping("/queryById")
    public Result<AiPaymentOrder> queryById(@RequestParam Long id) {
        return Result.OK(aiPaymentOrderService.getById(id));
    }
}
