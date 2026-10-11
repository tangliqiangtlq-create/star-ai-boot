package org.jeecg.transcode.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "transcode")
public class TranscodeConfig {
    private String ffmpegPath = "/usr/local/bin/ffmpeg";
    private String ffprobePath = "/usr/local/bin/ffprobe";
    private String workDir = "/tmp/star-ai-transcode";
    private String publishedBucket = "stream-published";
    private int concurrentWorkers = 2;
    private List<Preset> presets = new ArrayList<>();

    @Data
    public static class Preset {
        private String name;
        private int height;
        private int bitrateKbps;
    }
}
