package org.jeecg.stream.service;
import org.jeecg.stream.entity.StreamVideo;

public interface IStreamPlayService {
    String getPreviewHlsUrl(Long videoId);
    String getFullHlsUrl(Long videoId);
    void recordPlayStart(Long userId, Long videoId);
    void recordPlayEnd(Long userId, Long videoId, Integer durationSec);
}
