package org.jeecg.creator.service;
import org.jeecg.creator.dto.PresignReq;
import org.jeecg.creator.dto.PresignResp;

public interface IMinioPresignService {
    PresignResp generatePresign(PresignReq req);
}
