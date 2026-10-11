package org.jeecg.transcode.service;
import org.jeecg.transcode.dto.VideoUploadedMsg;

public interface ITranscodeService {
    void transcode(VideoUploadedMsg msg);
}
