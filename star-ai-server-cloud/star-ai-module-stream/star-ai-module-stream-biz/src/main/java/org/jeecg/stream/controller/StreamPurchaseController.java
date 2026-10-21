package org.jeecg.stream.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.stream.dto.CreateOrderReq;
import org.jeecg.stream.entity.StreamVideoPurchase;
import org.jeecg.stream.service.IStreamPurchaseService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/stream/orders")
@RequiredArgsConstructor
public class StreamPurchaseController {

    private final IStreamPurchaseService purchaseService;

    @ApiOperation("创建单集购买订单")
    @PostMapping
    public Result<StreamVideoPurchase> createOrder(@RequestBody CreateOrderReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        req.setUserId(userId);
        return Result.OK(purchaseService.createEpisodeOrder(req));
    }

    @ApiOperation("查询订单状态")
    @GetMapping("/{orderNo}")
    public Result<StreamVideoPurchase> getOrder(@PathVariable String orderNo) {
        return Result.OK(purchaseService.getByOrderNo(orderNo));
    }

    @ApiOperation("我的购买记录")
    @GetMapping("/mine")
    public Result<IPage<StreamVideoPurchase>> mine(Page<StreamVideoPurchase> page) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.OK(purchaseService.page(page, new LambdaQueryWrapper<StreamVideoPurchase>()
            .eq(StreamVideoPurchase::getUserId, userId)
            .orderByDesc(StreamVideoPurchase::getCreateTime)));
    }
}
