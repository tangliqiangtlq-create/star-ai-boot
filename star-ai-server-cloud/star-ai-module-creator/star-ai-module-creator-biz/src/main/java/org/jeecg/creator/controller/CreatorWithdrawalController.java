package org.jeecg.creator.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.stream.entity.StreamWithdrawalRequest;
import org.jeecg.stream.service.IStreamWithdrawalRequestService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/creator/withdrawals")
@RequiredArgsConstructor
public class CreatorWithdrawalController {

    private final IStreamWithdrawalRequestService withdrawalService;

    @ApiOperation("发起提现申请")
    @PostMapping
    public Result<StreamWithdrawalRequest> apply(@RequestBody StreamWithdrawalRequest req) {
        Long userId = StpUtil.getLoginIdAsLong();
        req.setCreatorId(userId);
        req.setStatus("pending");
        withdrawalService.save(req);
        return Result.OK(req);
    }

    @ApiOperation("我的提现记录")
    @GetMapping
    public Result<IPage<StreamWithdrawalRequest>> mine(Page<StreamWithdrawalRequest> page) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.OK(withdrawalService.page(page, new LambdaQueryWrapper<StreamWithdrawalRequest>()
            .eq(StreamWithdrawalRequest::getCreatorId, userId)
            .orderByDesc(StreamWithdrawalRequest::getCreateTime)));
    }
}
