package org.jeecg.common.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Excel import error log written out. Desensitize written content to avoid leaking SQL/path/stack to downloadable files.
 */
@Slf4j
@Component
public class PmsUtil {

  private static String uploadPath;

  private static final Pattern SENSITIVE =
      Pattern.compile(
          "(?i)(password|passwd|pwd|secret|token|jdbc:|sqlexception|/home/|/Users/|C:\\\\|stacktrace)",
          Pattern.CASE_INSENSITIVE);

  @Value("${jeecg.path.upload:}")
  public void setUploadPath(String uploadPath) {
    PmsUtil.uploadPath = uploadPath;
  }

  public static String saveErrorTxtByList(List<String> msg, String name) {
    Date d = new Date();
    String saveDir = "logs" + File.separator + DateUtils.yyyyMMdd.get().format(d) + File.separator;
    String saveFullDir = uploadPath + File.separator + saveDir;

    File saveFile = new File(saveFullDir);
    if (!saveFile.exists()) {
      saveFile.mkdirs();
    }
    name += DateUtils.yyyymmddhhmmss.get().format(d) + Math.round(Math.random() * 10000);
    String saveFilePath = saveFullDir + name + ".txt";

    try (BufferedWriter bw = new BufferedWriter(new FileWriter(saveFilePath))) {
      for (String s : msg) {
        String line = sanitize(s);
        if (line.indexOf('_') > 0) {
          String[] arr = line.split("_", 2);
          bw.write("Row" + arr[0] + "Row:" + arr[1]);
        } else {
          bw.write(line);
        }
        bw.write("\r\n");
      }
    } catch (Exception e) {
      log.warn("Excel import failed to generate error log file exception");
    }
    return saveDir + name + ".txt";
  }

  static String sanitize(String raw) {
    if (raw == null) {
      return "";
    }
    String trimmed = raw.trim();
    if (trimmed.length() > 200) {
      trimmed = trimmed.substring(0, 200) + "...";
    }
    // The original text of imported exceptions must be generalized to avoid SQL/path/stack falling into downloadable error files.
    if (trimmed.startsWith("Exception occurred")
        || trimmed.toLowerCase().contains("exception")
        || SENSITIVE.matcher(trimmed).find()) {
      return "[REDACTED]";
    }
    return trimmed.replaceAll("(?i)(password|passwd|pwd)\\s*[:=]\\s*\\S+", "$1=***");
  }
}
