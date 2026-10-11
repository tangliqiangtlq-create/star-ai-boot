/*
 * Sa-Token 1.40 -> 1.44 upgrade compatibility: tokenSignList element type in legacy Account-Session JSON.
 * Removed in 1.41+; keep this class for Jackson deserialization of historical Redis data.
 */
package cn.dev33.satoken.session;

import java.io.Serializable;

public class TokenSign implements Serializable {

  private static final long serialVersionUID = 1406115065849845073L;

  private String value;
  private String device;
  private Object tag;

  public TokenSign() {}

  public TokenSign(String value, String device, Object tag) {
    this.value = value;
    this.device = device;
    this.tag = tag;
  }

  public String getValue() {
    return value;
  }

  public String getDevice() {
    return device;
  }

  public Object getTag() {
    return tag;
  }

  public TokenSign setValue(String value) {
    this.value = value;
    return this;
  }

  public TokenSign setDevice(String device) {
    this.device = device;
    return this;
  }

  public TokenSign setTag(Object tag) {
    this.tag = tag;
    return this;
  }
}
