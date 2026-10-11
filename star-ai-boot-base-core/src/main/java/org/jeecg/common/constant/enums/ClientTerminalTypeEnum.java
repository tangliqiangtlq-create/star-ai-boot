package org.jeecg.common.constant.enums;

/**
 * client terminalType
 */
public enum ClientTerminalTypeEnum {

    PC("pc", "Desktop terminal"),
    H5("h5", "Mobile web"),
    APP("app", "Mobile app");

    private String key;
    private String text;

    ClientTerminalTypeEnum(String value, String text) {
        this.key = value;
        this.text = text;
    }

    public String getKey() {
        return this.key;
    }
}
