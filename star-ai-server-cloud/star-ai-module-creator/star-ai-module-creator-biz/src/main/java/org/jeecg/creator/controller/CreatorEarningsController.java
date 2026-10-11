package org.jeecg.creator.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.stream.entity.StreamCreatorEarnings;
import org.jeecg.stream.entity.StreamRevenueRecord;
import org.jeecg.stream.service.IStreamCreatorEarningsService;
import org.jeecg.stream.service.IStreamRevenueRecordService;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/creator/earnings")
@RequiredArgsConstructor
public class CreatorEarningsController {

    private final IStreamCreatorEarningsService earningsService;
    private final IStreamRevenueRecordService revenueService;

    @ApiOperation("创作者收益看板 (汇总)")
    @GetMapping
    public Result<StreamCreatorEarnings> summary() {
        Long userId = StpUtil.getLoginIdAsLong();
        var account = org.jeecg.creator.service.ICreatorOnboardingService.class;
        StreamCreatorEarnings earnings = earningsService.getOne(
            new LambdaQueryWrapper<StreamCreatorEarnings>().eq(StreamCreatorEarnings::getCreatorId, userId));
        if (earnings == null) {
            earnings = new StreamCreatorEarnings();
            earnings.setCreatorId(userId);
            earnings.setTotalEarned(java.math.BigDecimal.ZERO);
            earnings.setAvailableBalance(java.math.BigDecimal.ZERO);
            earnings.setFrozenBalance(java.math.BigDecimal.ZERO);
            earnings.setTotalWithdrawn(java.math.BigDecimal.ZERO);
        }
        return Result.OK(earnings);
    }

    @ApiOperation("分润流水明细")
    @GetMapping("/records")
    public Result<IPage<StreamRevenueRecord>> records(Page<StreamRevenueRecord> page) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.OK(revenueService.page(page, new LambdaQueryWrapper<StreamRevenueRecord>()
            .eq(StreamRevenueRecord::getCreatorId, userId)
            .orderByDesc(StreamRevenueRecord::getCreateTime)));
    }
}
