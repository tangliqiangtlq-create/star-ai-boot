package org.jeecg.creator.service;
import org.jeecg.creator.dto.VideoPublishReq;
import org.jeecg.stream.entity.StreamVideo;

public interface IVideoPublishService {
    StreamVideo publish(VideoPublishReq req);
}
