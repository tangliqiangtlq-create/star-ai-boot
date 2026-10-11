package org.jeecg.creator.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.creator.dto.OnboardingReq;
import org.jeecg.creator.entity.CreatorAccount;
import org.jeecg.creator.mapper.CreatorAccountMapper;
import org.jeecg.creator.service.ICreatorOnboardingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class CreatorOnboardingServiceImpl extends ServiceImpl<CreatorAccountMapper, CreatorAccount>
    implements ICreatorOnboardingService {

    @Override
    @Transactional
    public CreatorAccount onboard(OnboardingReq req) {
        CreatorAccount exist = getByUserId(req.getUserId());
        if (exist != null) return exist;

        CreatorAccount account = new CreatorAccount();
        account.setUserId(req.getUserId());
        account.setNickname(req.getNickname());
        account.setAvatarUrl(req.getAvatarUrl());
        account.setBio(req.getBio());
        account.setRegion(req.getRegion() != null ? req.getRegion() : "cn");
        account.setStatus("active");
        account.setLevel("L1");
        account.setRevenueRate(new BigDecimal("30.00"));
        account.setSubscriptionRevenueRate(new BigDecimal("30.00"));
        account.setPricingEnabled(1);
        account.setPricingMode("self");
        save(account);
        return account;
    }

    @Override
    public CreatorAccount getByUserId(Long userId) {
        return getOne(new LambdaQueryWrapper<CreatorAccount>()
            .eq(CreatorAccount::getUserId, userId));
    }
}
