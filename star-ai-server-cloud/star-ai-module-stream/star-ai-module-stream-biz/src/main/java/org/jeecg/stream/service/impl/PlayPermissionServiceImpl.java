package org.jeecg.stream.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.stream.dto.PlayPermission;
import org.jeecg.stream.entity.*;
import org.jeecg.stream.mapper.*;
import org.jeecg.stream.service.IPlayPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayPermissionServiceImpl implements IPlayPermissionService {

    private final StreamVideoMapper videoMapper;
    private final StreamVideoPurchaseMapper purchaseMapper;
    private final StreamCreatorUnlockMapper creatorUnlockMapper;
    private final StreamVideoCollectionRelMapper collectionRelMapper;

    @Override
    public PlayPermission check(Long userId, Long videoId) {
        StreamVideo video = videoMapper.selectById(videoId);
        if (video == null) {
            return PlayPermission.locked("free", null, 0);
        }

        // ① 免费
        if ("free".equals(video.getPriceType())
            || video.getPriceAmount() == null
            || video.getPriceAmount().compareTo(java.math.BigDecimal.ZERO) == 0) {
            return PlayPermission.granted("free");
        }

        // ② 创作者全局解锁
        Long creatorId = video.getCreatorId();
        Long gUnlock = creatorUnlockMapper.selectCount(new LambdaQueryWrapper<StreamCreatorUnlock>()
            .eq(StreamCreatorUnlock::getUserId, userId)
            .eq(StreamCreatorUnlock::getCreatorId, creatorId));
        if (gUnlock != null && gUnlock > 0) {
            return PlayPermission.granted("creator_global");
        }

        // ③ 单集购买
        Long epCount = purchaseMapper.selectCount(new LambdaQueryWrapper<StreamVideoPurchase>()
            .eq(StreamVideoPurchase::getUserId, userId)
            .eq(StreamVideoPurchase::getVideoId, videoId)
            .eq(StreamVideoPurchase::getUnlockScope, "episode"));
        if (epCount != null && epCount > 0) {
            return PlayPermission.granted("episode");
        }

        // ④ 剧集解锁 — 通过 collection_rel 查 video 属于哪个 collection
        StreamVideoCollectionRel rel = collectionRelMapper.selectOne(new LambdaQueryWrapper<StreamVideoCollectionRel>()
            .eq(StreamVideoCollectionRel::getVideoId, videoId)
            .last("LIMIT 1"));
        if (rel != null) {
            Long colCount = purchaseMapper.selectCount(new LambdaQueryWrapper<StreamVideoPurchase>()
                .eq(StreamVideoPurchase::getUserId, userId)
                .eq(StreamVideoPurchase::getCollectionId, rel.getCollectionId())
                .eq(StreamVideoPurchase::getUnlockScope, "collection"));
            if (colCount != null && colCount > 0) {
                return PlayPermission.granted("collection");
            }
        }

        return PlayPermission.locked(video.getPriceType(), video.getPriceAmount(), video.getPreviewSec());
    }
}
