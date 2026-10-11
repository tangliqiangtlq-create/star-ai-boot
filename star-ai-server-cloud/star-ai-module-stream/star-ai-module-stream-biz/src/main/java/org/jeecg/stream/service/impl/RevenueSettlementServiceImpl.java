package org.jeecg.stream.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.stream.dto.PaymentSuccessMsg;
import org.jeecg.stream.entity.StreamCreatorEarnings;
import org.jeecg.stream.entity.StreamRevenueRecord;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.entity.StreamVideoPurchase;
import org.jeecg.stream.mapper.*;
import org.jeecg.stream.service.IRevenueSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueSettlementServiceImpl implements IRevenueSettlementService {

    private final StreamRevenueRecordMapper revenueMapper;
    private final StreamCreatorEarningsMapper earningsMapper;
    private final StreamVideoMapper videoMapper;
    private final StreamVideoPurchaseMapper purchaseMapper;

    @Override
    @Transactional
    public void settle(PaymentSuccessMsg msg) {
        log.info("===== 分润结算: orderNo={} =====", msg.getOrderNo());

        StreamVideoPurchase order = purchaseMapper.selectOne(new LambdaQueryWrapper<StreamVideoPurchase>()
            .eq(StreamVideoPurchase::getOrderNo, msg.getOrderNo()));
        if (order == null) {
            log.error("订单不存在 orderNo={}", msg.getOrderNo());
            return;
        }

        StreamVideo video = videoMapper.selectById(order.getVideoId());
        if (video == null) {
            log.error("视频不存在 videoId={}", order.getVideoId());
            return;
        }

        // 核心: 取 video.revenueRateSnapshot (发布时固化的), 不是实时查 creator_account
        BigDecimal snapshot = video.getRevenueRateSnapshot() != null
            ? video.getRevenueRateSnapshot()
            : new BigDecimal("30.00");

        BigDecimal orderAmount = order.getPaidAmount() != null
            ? order.getPaidAmount()
            : (msg.getOrderAmount() != null ? msg.getOrderAmount() : BigDecimal.ZERO);

        BigDecimal creatorShare = orderAmount.multiply(snapshot)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal platformShare = orderAmount.subtract(creatorShare);

        // 写分润流水
        StreamRevenueRecord rec = new StreamRevenueRecord();
        rec.setRevenueSource("DIRECT");
        rec.setBusinessOrderNo(msg.getOrderNo());
        rec.setPaymentOrderNo(msg.getPaymentOrderNo());
        rec.setVideoId(order.getVideoId());
        rec.setCreatorId(video.getCreatorId());
        rec.setTotalAmount(orderAmount);
        rec.setRevenueRate(snapshot);
        rec.setCreatorShare(creatorShare);
        rec.setPlatformShare(platformShare);
        rec.setSettleStatus("settled");
        revenueMapper.insert(rec);

        // 更新视频累计数据
        video.setPurchaseCount(video.getPurchaseCount() != null ? video.getPurchaseCount() + 1 : 1);
        video.setTotalRevenue(video.getTotalRevenue() != null
            ? video.getTotalRevenue().add(creatorShare) : creatorShare);
        videoMapper.updateById(video);

        // 更新创作者收益账本
        StreamCreatorEarnings earnings = earningsMapper.selectOne(new LambdaQueryWrapper<StreamCreatorEarnings>()
            .eq(StreamCreatorEarnings::getCreatorId, video.getCreatorId()));
        if (earnings == null) {
            earnings = new StreamCreatorEarnings();
            earnings.setCreatorId(video.getCreatorId());
            earnings.setTotalEarned(creatorShare);
            earnings.setAvailableBalance(creatorShare);
            earnings.setFrozenBalance(BigDecimal.ZERO);
            earnings.setTotalWithdrawn(BigDecimal.ZERO);
            earningsMapper.insert(earnings);
        } else {
            earnings.setTotalEarned(earnings.getTotalEarned().add(creatorShare));
            earnings.setAvailableBalance(earnings.getAvailableBalance().add(creatorShare));
            earningsMapper.updateById(earnings);
        }

        log.info("分润完成: creatorId={}, orderAmount={}, snapshot={}%, creator={}, platform={}",
            video.getCreatorId(), orderAmount, snapshot, creatorShare, platformShare);
    }
}
