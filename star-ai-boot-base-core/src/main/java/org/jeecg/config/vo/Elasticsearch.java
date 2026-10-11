package org.jeecg.config.vo;

/**
 * @author: scott
 * @date: 2023-05-10 16:06
 */
public class Elasticsearch {
    private String clusterNodes;
    private boolean checkEnabled;

    public String getClusterNodes() {
        return clusterNodes;
    }

    public void setClusterNodes(String clusterNodes) {
        this.clusterNodes = clusterNodes;
    }

    public boolean isCheckEnabled() {
        return checkEnabled;
    }

    public void setCheckEnabled(boolean checkEnabled) {
        this.checkEnabled = checkEnabled;
    }
}
