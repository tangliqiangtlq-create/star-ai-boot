package org.jeecg.modules.system.vo.thirdapp;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Sync result with success and failure info
 *
 * @author sunjianlei
 */
@Data
public class SyncInfoVo {

    /**
     * Success message
     */
    private List<String> successInfo;
    /**
     * Failure message
     */
    private List<String> failInfo;

    public SyncInfoVo() {
        this.successInfo = new ArrayList<>();
        this.failInfo = new ArrayList<>();
    }

    public SyncInfoVo(List<String> successInfo, List<String> failInfo) {
        this.successInfo = successInfo;
        this.failInfo = failInfo;
    }

    public SyncInfoVo addSuccessInfo(String info) {
        this.successInfo.add(info);
        return this;
    }

    public SyncInfoVo addFailInfo(String info) {
        this.failInfo.add(info);
        return this;
    }
}
