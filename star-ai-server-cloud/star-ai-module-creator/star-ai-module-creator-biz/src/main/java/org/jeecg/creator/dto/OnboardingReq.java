package org.jeecg.creator.dto;
import lombok.Data;
@Data
public class OnboardingReq {
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private String region;
}
