package org.jeecg.stream.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.stream.dto.CreateOrderReq;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.entity.StreamVideoPurchase;
import org.jeecg.stream.mapper.StreamVideoMapper;
import org.jeecg.stream.mapper.StreamVideoPurchaseMapper;
import org.jeecg.stream.service.IStreamPurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamPurchaseServiceImpl extends ServiceImpl<StreamVideoPurchaseMapper, StreamVideoPurchase>
    implements IStreamPurchaseService {

    private final StreamVideoMapper videoMapper;
    private final StreamVideoPurchaseMapper purchaseMapper;

    @Override
    @Transactional
    public StreamVideoPurchase createEpisodeOrder(CreateOrderReq req) {
        StreamVideo video = videoMapper.selectById(req.getVideoId());
        Assert.notNull(video, "视频不存在");
        Assert.isTrue("paid".equals(video.getPriceType()), "该视频不是付费类型");

        boolean exists = hasPurchased(req.getUserId(), req.getVideoId());
        Assert.isTrue(!exists, "您已购买过此视频, 无需重复购买");

        StreamVideoPurchase order = new StreamVideoPurchase();
        order.setOrderNo(generateOrderNo());
        order.setUserId(req.getUserId());
        order.setVideoId(req.getVideoId());
        order.setUnlockScope("episode");
        order.setAccessType("purchase");
        order.setPaidAmount(video.getPriceAmount());
        order.setCurrency(video.getCurrency());

        purchaseMapper.insert(order);
        log.info("创建订单(待支付): orderNo=" + order.getOrderNo() + " userId=" + req.getUserId() + " videoId=" + req.getVideoId() + " amount=" + order.getPaidAmount());
        return order;
    }

    @Override
    public StreamVideoPurchase getByOrderNo(String orderNo) {
        return purchaseMapper.selectOne(new LambdaQueryWrapper<StreamVideoPurchase>()
            .eq(StreamVideoPurchase::getOrderNo, orderNo));
    }

    @Override
    public boolean hasPurchased(Long userId, Long videoId) {
        Long c = purchaseMapper.selectCount(new LambdaQueryWrapper<StreamVideoPurchase>()
            .eq(StreamVideoPurchase::getUserId, userId)
            .eq(StreamVideoPurchase::getVideoId, videoId));
        return c != null && c > 0;
    }

    private String generateOrderNo() {
        return "VI" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
