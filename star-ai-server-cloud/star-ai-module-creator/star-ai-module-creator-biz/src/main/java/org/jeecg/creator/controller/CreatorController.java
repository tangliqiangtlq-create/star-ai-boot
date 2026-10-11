package org.jeecg.creator.controller;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.creator.dto.OnboardingReq;
import org.jeecg.creator.entity.CreatorAccount;
import org.jeecg.creator.service.ICreatorOnboardingService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/creators")
@RequiredArgsConstructor
public class CreatorController {
    private final ICreatorOnboardingService onboardingService;

    @ApiOperation("创作者入驻")
    @PostMapping("/apply")
    public Result<CreatorAccount> apply(@RequestBody OnboardingReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        req.setUserId(userId);
        return Result.OK(onboardingService.onboard(req));
    }

    @ApiOperation("当前创作者信息")
    @GetMapping("/me")
    public Result<CreatorAccount> me() {
        Long userId = StpUtil.getLoginIdAsLong();
        CreatorAccount account = onboardingService.getByUserId(userId);
        if (account == null) return Result.error("创作者账户不存在, 请先入驻");
        return Result.OK(account);
    }

    @ApiOperation("创作者详情")
    @GetMapping("/{id}")
    public Result<CreatorAccount> getById(@PathVariable Long id) {
        return Result.OK(onboardingService.getById(id));
    }

    @ApiOperation("开启/关闭全局解锁定价")
    @PutMapping("/global-unlock")
    public Result<String> globalUnlock(@RequestBody(required = false) java.util.Map<String, Object> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        CreatorAccount account = onboardingService.getByUserId(userId);
        if (account == null) return Result.error("创作者账户不存在");
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("enabled"));
        account.setPricingEnabled(enabled ? 1 : 0);
        onboardingService.updateById(account);
        return Result.OK(enabled ? "已开启" : "已关闭");
    }
}
