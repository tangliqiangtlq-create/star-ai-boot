package org.jeecg.creator.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.storage.IObjectStorageService;
import org.jeecg.creator.config.MinioConfig;
import org.jeecg.creator.dto.PresignReq;
import org.jeecg.creator.dto.PresignResp;
import org.jeecg.creator.service.IMinioPresignService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioPresignServiceImpl implements IMinioPresignService {

    private final IObjectStorageService storageService;
    private final MinioConfig minioConfig;

    private static final int CHUNK_SIZE = 5 * 1024 * 1024;
    private static final int MAX_CHUNKS = 100;

    @Override
    public PresignResp generatePresign(PresignReq req) {
        String objectKey = UUID.randomUUID().toString() + "_" + req.getFileName();
        int totalChunks = (int) Math.ceil((double) req.getFileSize() / CHUNK_SIZE);
        if (totalChunks > MAX_CHUNKS) totalChunks = MAX_CHUNKS;
        if (req.getChunkCount() != null) totalChunks = Math.min(req.getChunkCount(), MAX_CHUNKS);

        List<PresignResp.ChunkUrl> chunks = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            String chunkKey = objectKey + ".part" + String.format("%03d", i);
            try {
                String url = storageService.generateUploadPresign(
                    minioConfig.getDraftBucket(), chunkKey, minioConfig.getPresignExpireSec());
                PresignResp.ChunkUrl cu = new PresignResp.ChunkUrl();
                cu.setIndex(i);
                cu.setUrl(url);
                chunks.add(cu);
            } catch (Exception e) {
                log.error("生成存储 presign chunk 失败, index=" + i, e);
            }
        }
        PresignResp resp = new PresignResp();
        resp.setBucket(minioConfig.getDraftBucket());
        resp.setObjectKey(objectKey);
        resp.setChunks(chunks);
        return resp;
    }
}
