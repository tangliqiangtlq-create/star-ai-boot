package org.jeecg.common.util.filter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.FileContentSniffer;
import org.springframework.web.multipart.MultipartFile;

/**
 * checkupload/downloadFile type。When uploading based onFile contentdeterminationType，Does not rely on client-providedFile nameor extension (SECZ-SSCSP-CODE-040）。
 *
 * @author: lsq
 * @date: 2023Year09moon12day 15:29
 */
@Slf4j
public class SsrfFileTypeFilter {

  /** Allow operationFile typewhitelist */
  private static final List<String> FILE_TYPE_WHITE_LIST = new ArrayList<>();

  static {
    // Image files
    FILE_TYPE_WHITE_LIST.add("jpg");
    FILE_TYPE_WHITE_LIST.add("jpeg");
    FILE_TYPE_WHITE_LIST.add("png");
    FILE_TYPE_WHITE_LIST.add("gif");
    FILE_TYPE_WHITE_LIST.add("bmp");
    FILE_TYPE_WHITE_LIST.add("svg");
    FILE_TYPE_WHITE_LIST.add("ico");

    // text file
    FILE_TYPE_WHITE_LIST.add("txt");
    FILE_TYPE_WHITE_LIST.add("doc");
    FILE_TYPE_WHITE_LIST.add("docx");
    FILE_TYPE_WHITE_LIST.add("pptx");
    FILE_TYPE_WHITE_LIST.add("pdf");
    FILE_TYPE_WHITE_LIST.add("csv");

    // Audio and video files
    FILE_TYPE_WHITE_LIST.add("mp4");
    FILE_TYPE_WHITE_LIST.add("avi");
    FILE_TYPE_WHITE_LIST.add("mov");
    FILE_TYPE_WHITE_LIST.add("wmv");
    FILE_TYPE_WHITE_LIST.add("mp3");
    FILE_TYPE_WHITE_LIST.add("wav");

    // form file
    FILE_TYPE_WHITE_LIST.add("xls");
    FILE_TYPE_WHITE_LIST.add("xlsx");

    // compressed file
    FILE_TYPE_WHITE_LIST.add("zip");
    FILE_TYPE_WHITE_LIST.add("rar");
    FILE_TYPE_WHITE_LIST.add("7z");
    FILE_TYPE_WHITE_LIST.add("tar");

    // app file suffix
    FILE_TYPE_WHITE_LIST.add("apk");
    FILE_TYPE_WHITE_LIST.add("wgt");
  }

  /**
   * Obtain the extension name through the path suffix (only used to assist in download scenarios where the path has been placed on the server.check；Please do not use when uploading).
   *
   * @param fileName File nameor path
   */
  private static String getFileTypeBySuffix(String fileName) {
    if (fileName == null) {
      return "";
    }
    int last = fileName.lastIndexOf('.');
    if (last < 0 || last >= fileName.length() - 1) {
      return "";
    }
    return fileName.substring(last + 1);
  }

  /**
   * downloadFile typeFiltering (based on path suffix; download resources should be server-side trusted paths)
   *
   * @param filePath file path
   */
  public static void checkDownloadFileType(String filePath) throws IOException {
    String suffix = getFileTypeBySuffix(filePath);
    log.info("suffix:{}", suffix);
    boolean isAllowExtension = FILE_TYPE_WHITE_LIST.contains(suffix.toLowerCase());
    if (!isAllowExtension) {
      throw new IOException("Download failed, illegal file type:" + suffix);
    }
  }

  /**
   * checkUpload and return after passing the whitelistContentThe inferred extension (lowercase) used for storing object names to avoid relying on client suffixes.
   *
   * @return ContentInferred extensions, e.g. jpg、docx
   */
  public static String validateUploadAndGetExtension(MultipartFile file) throws Exception {
    String suffix = FileContentSniffer.sniffExtension(file);
    if (suffix == null || suffix.isEmpty()) {
      throw new Exception("Upload failed, unable to detect file type from content");
    }
    log.info("suffix (content-based):{}", suffix);
    if (!FILE_TYPE_WHITE_LIST.contains(suffix.toLowerCase())) {
      throw new Exception("Upload failed, illegal file type:" + suffix);
    }
    return suffix;
  }

  /**
   * and {@link #validateUploadAndGetExtension(MultipartFile)} Same for streams that have been written to temporary files (such as commons FileItemStream）。
   */
  public static String validateUploadAndGetExtension(Path tempFile) throws Exception {
    String suffix = FileContentSniffer.sniffExtension(tempFile);
    if (suffix == null || suffix.isEmpty()) {
      throw new Exception("Upload failed, unable to detect file type from content");
    }
    log.info("suffix (content-based, temp file):{}", suffix);
    if (!FILE_TYPE_WHITE_LIST.contains(suffix.toLowerCase())) {
      throw new Exception("Upload failed, illegal file type:" + suffix);
    }
    return suffix;
  }

  /**
   * uploadFile typeFilter: Completely based on {@link FileContentSniffer} fromFile contentinferredType，Does not rely on originalFile name。
   *
   * @param file Upload files
   */
  public static void checkUploadFileType(MultipartFile file) throws Exception {
    validateUploadAndGetExtension(file);
  }
}
