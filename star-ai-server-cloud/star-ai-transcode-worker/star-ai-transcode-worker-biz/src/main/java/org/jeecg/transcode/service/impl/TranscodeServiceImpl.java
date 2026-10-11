package org.jeecg.transcode.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.common.storage.IObjectStorageService;
import org.jeecg.transcode.config.MinioConfig;
import org.jeecg.transcode.config.TranscodeConfig;
import org.jeecg.transcode.dto.VideoUploadedMsg;
import org.jeecg.transcode.service.ITranscodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.service.IStreamVideoService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodeServiceImpl implements ITranscodeService {

    private final IObjectStorageService storageService;
    private final MinioConfig minioConfig;
    private final TranscodeConfig transcodeConfig;
    private final IStreamVideoService streamVideoService;
    private final ObjectMapper objectMapper;

    private static final String[] DEFAULT_PRESETS = {
        "1080p:2500", "720p:1500", "480p:800"
    };

    @Override
    public void transcode(VideoUploadedMsg msg) {
        Long videoId = msg.getVideoId();
        log.info("===== 开始转码 videoId={} =====", videoId);

        StreamVideo video = streamVideoService.getById(videoId);
        if (video == null) {
            log.error("videoId={} 不存在, 丢弃转码消息", videoId);
            return;
        }

        try {
            video.setTranscodeStatus("processing");
            streamVideoService.updateById(video);

            Path workDir = Paths.get(transcodeConfig.getWorkDir(), String.valueOf(videoId));
            Files.createDirectories(workDir);

            String downloadUrl = storageService.generateDownloadPresign(
                msg.getBucket(), msg.getOriginalKey(), minioConfig.getDownloadExpireSec());

            Path inputFile = workDir.resolve("input.mp4");
            downloadWithCurl(downloadUrl, inputFile.toAbsolutePath().toString());

            List<String> presetNames = new ArrayList<>();
            StringBuilder masterM3u8 = new StringBuilder("#EXTM3U\n");

            String[] presets = DEFAULT_PRESETS;
            if (transcodeConfig.getPresets() != null && !transcodeConfig.getPresets().isEmpty()) {
                presets = transcodeConfig.getPresets().stream()
                    .map(p -> p.getName() + ":" + p.getBitrateKbps())
                    .toArray(String[]::new);
            }

            for (String preset : presets) {
                String[] parts = preset.split(":");
                String name = parts[0];
                int bitrateKbps = Integer.parseInt(parts[1]);
                String height = name.replaceAll("[^0-9]", "");

                Path outDir = workDir.resolve(name);
                Files.createDirectories(outDir);

                boolean ok = runFfmpeg(inputFile.toAbsolutePath().toString(),
                    outDir.toAbsolutePath().toString(), name, height, bitrateKbps);

                if (ok) {
                    presetNames.add(name);
                    long bw = (long) bitrateKbps * 1000;
                    masterM3u8.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(bw)
                        .append(",RESOLUTION=").append("x").append(height)
                        .append("\n").append(name).append("/index.m3u8\n");

                    uploadDirToMinio(outDir, videoId + "/" + name, transcodeConfig.getPublishedBucket());
                }
            }

            generatePreview(inputFile.toAbsolutePath().toString(),
                workDir.toAbsolutePath().toString(), "preview-720p", "720", 2000);

            Path masterFile = workDir.resolve("master.m3u8");
            Files.writeString(masterFile, masterM3u8.toString());
            uploadDirToMinio(workDir, String.valueOf(videoId), transcodeConfig.getPublishedBucket());

            video.setTranscodeStatus("completed");
            video.setTranscodeJobId("ffmpeg-local-" + videoId);
            video.setHlsMasterKey(videoId + "/master.m3u8");
            video.setHlsPresets(String.join(":", presetNames));
            streamVideoService.updateById(video);

            log.info("===== 转码完成 videoId={}, presets={} =====", videoId, presetNames);

            // 清理本地工作目录
            deleteDir(workDir.toFile());

        } catch (Exception e) {
            log.error("转码失败 videoId=" + videoId, e);
            try {
                video.setTranscodeStatus("failed");
                video.setReviewNote("转码失败: " + e.getMessage());
                streamVideoService.updateById(video);
            } catch (Exception ex) {
                log.error("更新转码失败状态也失败了 videoId=" + videoId, ex);
            }
        }
    }

    private boolean runFfmpeg(String input, String outDir, String preset, String height, int bitrateKbps) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(transcodeConfig.getFfmpegPath());
            cmd.add("-y");
            cmd.add("-i");
            cmd.add(input);
            cmd.add("-c:v"); cmd.add("libx264");
            cmd.add("-preset"); cmd.add("medium");
            cmd.add("-b:v"); cmd.add(bitrateKbps + "k");
            cmd.add("-maxrate"); cmd.add((int)(bitrateKbps * 1.2) + "k");
            cmd.add("-bufsize"); cmd.add(bitrateKbps * 2 + "k");
            cmd.add("-vf"); cmd.add("scale=-2:" + height);
            cmd.add("-c:a"); cmd.add("aac");
            cmd.add("-b:a"); cmd.add("128k");
            cmd.add("-hls_time"); cmd.add("10");
            cmd.add("-hls_list_size"); cmd.add("0");
            cmd.add("-hls_segment_type"); cmd.add("fmp4");
            cmd.add(outDir + "/index.m3u8");

            log.info("FFmpeg cmd: {}", String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("Error") || line.contains("error")) {
                    log.warn("FFmpeg[{}]: {}", preset, line);
                }
            }

            int rc = p.waitFor();
            if (rc != 0) {
                log.error("FFmpeg[{}] exit code={}", preset, rc);
                return false;
            }
            log.info("FFmpeg[{}] DONE", preset);
            return true;
        } catch (Exception e) {
            log.error("FFmpeg[{}] 执行异常", preset, e);
            return false;
        }
    }

    private void downloadWithCurl(String url, String outputPath) throws Exception {
        List<String> cmd = List.of("curl", "-sSL", "-o", outputPath, url);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        int rc = pb.start().waitFor();
        if (rc != 0) throw new RuntimeException("curl download failed rc=" + rc);
    }

    private void uploadDirToMinio(Path localDir, String prefix, String bucket) throws Exception {
        storageService.uploadDirectory(localDir, bucket, prefix);
    }

private boolean generatePreview(String input, String outDir, String preset, String height, int bitrateKbps) {
    try {
        Path previewDir = Paths.get(outDir).getParent().resolve(preset + "_preview");
        Files.createDirectories(previewDir);
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(transcodeConfig.getFfmpegPath());
        cmd.add("-y"); cmd.add("-i"); cmd.add(input);
        cmd.add("-t"); cmd.add("30");
        cmd.add("-c:v"); cmd.add("libx264"); cmd.add("-preset"); cmd.add("fast");
        cmd.add("-b:v"); cmd.add(bitrateKbps + "k"); cmd.add("-vf"); cmd.add("scale=-2:" + height);
        cmd.add("-c:a"); cmd.add("aac"); cmd.add("-b:a"); cmd.add("96k");
        cmd.add("-hls_time"); cmd.add("10"); cmd.add("-hls_list_size"); cmd.add("3");
        cmd.add("-hls_segment_type"); cmd.add("fmp4");
        cmd.add(previewDir + "/preview.m3u8");
        log.info("FFmpeg preview cmd: " + String.join(" ", cmd));
        Process p2 = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        BufferedReader br = new BufferedReader(new java.io.InputStreamReader(p2.getInputStream()));
        while ((br.readLine()) != null) {}
        int rc = p2.waitFor();
        if (rc != 0) { log.warn("preview rc=" + rc); return false; }
        log.info("FFmpeg " + preset + " preview 30s DONE");
        return true;
    } catch (Exception e) { log.error("preview gen fail", e); return false; }
}

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f); else f.delete();
            }
        }
        dir.delete();
    }
}
