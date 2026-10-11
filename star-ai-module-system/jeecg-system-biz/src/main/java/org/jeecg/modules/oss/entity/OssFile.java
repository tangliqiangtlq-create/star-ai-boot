package org.jeecg.modules.oss.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigInteger;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.enem.UsageScenarios;
import org.jeecg.common.system.base.entity.JeecgEntity;
import org.jeecgframework.poi.excel.annotation.Excel;

/**
 * @Description: OSS cloud storage entity
 *
 * @author: jeecg-boot
 */
@Data
@TableName("oss_file")
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class OssFile extends JeecgEntity {

  private static final long serialVersionUID = 1L;

  @Excel(name = "FileName")
  private String fileName;

  /** Client original display name (not used as object storage key). */
  @Excel(name = "OriginalFileName")
  private String originalFileName;

  @Excel(name = "File address")
  private String url;

  private String userId;

  private UsageScenarios usageScenarios;

  private String objectId;

  private String basePath;

  private String path;

  private String ext;

  private String platform;

  private BigInteger size;
}
