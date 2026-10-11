package org.jeecg.stream.service.impl;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.stream.config.MinioConfig;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.entity.StreamVideoPlayHistory;
import org.jeecg.stream.mapper.StreamVideoMapper;
import org.jeecg.stream.mapper.StreamVideoPlayHistoryMapper;
import org.jeecg.stream.service.IStreamPlayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamPlayServiceImpl implements IStreamPlayService {

    private final StreamVideoMapper videoMapper;
    private final StreamVideoPlayHistoryMapper historyMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    public String getFullHlsUrl(Long videoId) {
        StreamVideo video = videoMapper.selectById(videoId);
        if (video == null) return null;
        return buildPresignedUrl(videoId);
    }

    @Override
    public String getPreviewHlsUrl(Long videoId) {
        return buildPresignedUrl(videoId);
    }

    private String buildPresignedUrl(Long videoId) {
        StreamVideo video = videoMapper.selectById(videoId);
        if (video == null || !"completed".equals(video.getTranscodeStatus())) return null;
        String key = videoId + "/master.m3u8";
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioConfig.getPublishedBucket())
                    .object(key)
                    .expiry(minioConfig.getPresignExpireSec())
                    .build());
        } catch (Exception e) {
            log.error("生成 HLS presign 失败 videoId=" + videoId, e);
            return null;
        }
    }

    @Override
    @Transactional
    public void recordPlayStart(Long userId, Long videoId) {
        StreamVideo video = videoMapper.selectById(videoId);
        if (video == null) return;
        video.setPlayCount(video.getPlayCount() != null ? video.getPlayCount() + 1 : 1);
        videoMapper.updateById(video);

        StreamVideoPlayHistory exist = historyMapper.selectOne(new LambdaQueryWrapper<StreamVideoPlayHistory>()
            .eq(StreamVideoPlayHistory::getUserId, userId)
            .eq(StreamVideoPlayHistory::getVideoId, videoId));
        if (exist == null) {
            StreamVideoPlayHistory h = new StreamVideoPlayHistory();
            h.setUserId(userId);
            h.setVideoId(videoId);
            h.setLastPlayedAt(new Date());
            h.setPlayCount(1);
            h.setPositionSec(0);
            h.setTotalWatchedSec(0);
            historyMapper.insert(h);
        } else {
            exist.setLastPlayedAt(new Date());
            historyMapper.updateById(exist);
        }
    }

    @Override
    @Transactional
    public void recordPlayEnd(Long userId, Long videoId, Integer durationSec) {
        StreamVideoPlayHistory h = historyMapper.selectOne(new LambdaQueryWrapper<StreamVideoPlayHistory>()
            .eq(StreamVideoPlayHistory::getUserId, userId)
            .eq(StreamVideoPlayHistory::getVideoId, videoId));
        if (h != null) {
            int current = h.getTotalWatchedSec() != null ? h.getTotalWatchedSec() : 0;
            h.setTotalWatchedSec(current + (durationSec != null ? durationSec : 0));
            h.setLastPlayedAt(new Date());
            historyMapper.updateById(h);
        }
    }
}
