package org.jeecg.modules.system.enem;

import lombok.Getter;

@Getter
public enum SceneType {
  REGISTER("register"),
  CHANGE_PASSWORD("changePassword"),
  RESET_PASSWORD("resetPassword"),
  LOGIN("login"),
  // Add second-factor auth
  TWO_FACTOR_AUTHENTICATION("twoFactorAuthentication");

  private String value;

  SceneType(String value) {
    this.value = value;
  }

  // Static method to resolve enum from string
  public static SceneType fromValue(String value) {
    for (SceneType type : values()) {
      if (type.getValue().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Invalid scene type: " + value);
  }
}
