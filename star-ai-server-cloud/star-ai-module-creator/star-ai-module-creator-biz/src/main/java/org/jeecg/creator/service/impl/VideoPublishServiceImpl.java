package org.jeecg.creator.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.creator.config.RabbitMqConfig;
import org.jeecg.creator.dto.OnboardingReq;
import org.jeecg.creator.dto.VideoPublishReq;
import org.jeecg.creator.entity.CreatorAccount;
import org.jeecg.creator.service.ICreatorOnboardingService;
import org.jeecg.creator.service.IVideoPublishService;
import org.jeecg.creator.util.PricingValidator;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.mapper.StreamVideoMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoPublishServiceImpl extends ServiceImpl<StreamVideoMapper, StreamVideo>
    implements IVideoPublishService {

    private final ICreatorOnboardingService onboardingService;
    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public StreamVideo publish(VideoPublishReq req) {
        CreatorAccount account = onboardingService.getByUserId(req.getCreatorId());
        if (account == null) {
            OnboardingReq ob = new OnboardingReq();
            ob.setUserId(req.getCreatorId());
            ob.setNickname("creator_" + req.getCreatorId());
            ob.setRegion(req.getRegion());
            account = onboardingService.onboard(ob);
        }
        Assert.isTrue("active".equals(account.getStatus()), "创作者账户未激活");

        StreamVideo video = new StreamVideo();
        video.setCreatorId(account.getId());
        video.setTitle(req.getTitle());
        video.setDescription(req.getDescription());
        video.setCoverUrl(req.getCoverUrl());
        video.setDurationSec(req.getDurationSec() != null ? req.getDurationSec() : 0);
        video.setCategoryId(req.getCategoryId());
        video.setRegion(req.getRegion() != null ? req.getRegion() : account.getRegion());
        video.setStorageBucket("stream-creator-draft");
        video.setOriginalKey(req.getOriginalKey());
        video.setOriginalSize(req.getOriginalSize() != null ? req.getOriginalSize() : 0);
        video.setMimeType(req.getMimeType());
        video.setTranscodeStatus("pending");
        video.setPublishStatus("draft");
        video.setPriceType(req.getPriceType() != null ? req.getPriceType() : "free");
        video.setPriceAmount(req.getPriceAmount() != null ? req.getPriceAmount() : BigDecimal.ZERO);
        video.setCurrency(req.getCurrency() != null ? req.getCurrency() : "CNY");
        video.setPreviewSec(req.getPreviewSec() != null ? req.getPreviewSec() : 30);

        try {
            PricingValidator.validate(account, video);
            video.setPriceStatus("approved");
        } catch (IllegalArgumentException e) {
            log.warn("定价超出允许区间, 提交人工审核: " + e.getMessage());
            video.setPriceStatus("pending");
        }

        // ============ 核心: 分润快照固化 ============
        // 发布时把 creator_account 的分润比例固化到 stream_video, 后续改创作者抽成不追溯
        video.setRevenueRateSnapshot(account.getRevenueRate() != null ? account.getRevenueRate() : new BigDecimal("30.00"));
        video.setSubscriptionRevenueRateSnapshot(account.getSubscriptionRevenueRate() != null ? account.getSubscriptionRevenueRate() : new BigDecimal("30.00"));

        video.setAutoReviewStatus("pending");
        video.setPublishStatus("pending_review");
        video.setPublishedAt(new Date());
        video.setCreateBy(account.getUserId());
        video.setUpdateBy(account.getUserId());

        save(video);
        log.info("视频发布入库: videoId=" + video.getId() + " creatorId=" + account.getId() + " revenueRateSnapshot=" + video.getRevenueRateSnapshot());

        try {
            String msg = "{\"videoId\":" + video.getId() + ",\"creatorId\":" + account.getId()
                + ",\"originalKey\":\"" + video.getOriginalKey() + "\",\"bucket\":\"" + video.getStorageBucket() + "\"}";
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_VIDEO, RabbitMqConfig.ROUTING_VIDEO_UPLOADED, msg);
            log.info("已发送 video.uploaded MQ: videoId=" + video.getId());
        } catch (Exception e) {
            log.error("发送 video.uploaded MQ 失败, videoId=" + video.getId() + ", 转码需手动触发", e);
        }
        return video;
    }
}
