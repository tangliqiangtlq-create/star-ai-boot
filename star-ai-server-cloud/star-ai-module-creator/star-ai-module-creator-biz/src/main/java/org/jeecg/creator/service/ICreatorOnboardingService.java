package org.jeecg.creator.service;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.creator.dto.OnboardingReq;
import org.jeecg.creator.entity.CreatorAccount;

public interface ICreatorOnboardingService extends IService<CreatorAccount> {
    CreatorAccount onboard(OnboardingReq req);
    CreatorAccount getByUserId(Long userId);
}
