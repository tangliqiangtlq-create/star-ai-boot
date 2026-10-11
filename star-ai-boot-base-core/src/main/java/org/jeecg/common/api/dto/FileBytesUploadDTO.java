package org.jeecg.common.api.dto;

import java.io.Serializable;
import lombok.Data;
import org.jeecg.common.enem.UsageScenarios;

/** File upload DTO between microservices to avoid Feign multipart coding problems. */
@Data
public class FileBytesUploadDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** File content of Base64 coding */
  private String base64Content;

  /** original file name */
  private String fileName;

  /** MIME Type, such as application/pdf */
  private String contentType;

  /** file usage scenarios */
  private UsageScenarios usageScenarios;

  /**
   * Explicit file owner (sys_user.id). Required for background / S2S uploads (e.g. monthly
   * statement) so {@code oss_file.user_id} is the business owner rather than the internal caller.
   */
  private String ownerUserId;
}
