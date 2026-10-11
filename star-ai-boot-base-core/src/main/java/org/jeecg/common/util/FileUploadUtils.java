package org.jeecg.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.config.firewall.E7SvgSanitizer;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class FileUploadUtils {

  // Security configuration parameters
  private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 10MB

  private static JeecgBaseConfig jeecgBaseConfig;

  // Allowed file extensions (lite high-riskType）
  private static final Set<String> ALLOWED_EXTENSIONS =
      Set.of(
          "bmp", "gif", "jpg", "jpeg", "png", "svg", "docx", "xlsx", "pptx", "pdf", "zip", "heic",
          "heif");

  // extension withMIMETypemapping
  static Map<String, Set<String>> EXTENSION_MIME_MAP =
      Map.ofEntries(
          Map.entry("bmp", Set.of("image/bmp")),
          Map.entry("gif", Set.of("image/gif")),
          Map.entry("jpg", Set.of("image/jpeg")),
          Map.entry("jpeg", Set.of("image/jpeg")),
          Map.entry("png", Set.of("image/png")),
          Map.entry("svg", Set.of("image/svg+xml")),
          Map.entry(
              "docx",
              Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
          Map.entry(
              "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
          Map.entry(
              "pptx",
              Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
          Map.entry("pdf", Set.of("application/pdf")),
          Map.entry("zip", Set.of("application/zip", "application/x-zip-compressed")));

  // Magic Number Dictionary (HEXFormat)
  private static final Map<String, String> MAGIC_BYTES_MAP =
      Map.ofEntries(
          Map.entry("bmp", "424D"),
          Map.entry("gif", "47494638"),
          Map.entry("jpg", "FFD8FF"),
          Map.entry("jpeg", "FFD8FF"),
          Map.entry("png", "89504E47"),
          Map.entry("svg", "3C3F786D"),
          Map.entry("docx", "504B0304"),
          Map.entry("xlsx", "504B0304"),
          Map.entry("pptx", "504B0304"),
          Map.entry("pdf", "25504446"),
          Map.entry("zip", "504B0304"));

  public static void assertAllowed(MultipartFile file) throws JeecgBootException {
    // Basecheck
    if (file == null || file.isEmpty()) {
      throw new JeecgBootException("File is empty");
    }

    String fileName = file.getOriginalFilename();

    if (jeecgBaseConfig == null) {
      jeecgBaseConfig = SpringContextUtils.getBean(JeecgBaseConfig.class);
    }

    String extension = getExtension(file.getOriginalFilename());
    String mimeType = file.getContentType();

    log.info("Validating file: {} [{}]", fileName, mimeType);

    validateFileSize(file);
    validateExtension(extension, fileName);

    if ("svg".equalsIgnoreCase(extension)) {
      byte[] sanitized = sanitizeSvg(file);
      if (sanitized != null) {
        try {
          java.lang.reflect.Field f = file.getClass().getDeclaredField("bytes");
          f.setAccessible(true);
          f.set(file, sanitized);
        } catch (Exception ignored) {
        }
      }
    }
  }

  public static byte[] sanitizeSvg(MultipartFile file) {
    try {
      byte[] bytes = file.getBytes();
      return E7SvgSanitizer.sanitizeSvg(bytes);
    } catch (Exception e) {
      throw new JeecgBootException("SVG content sanitize failed: " + e.getMessage());
    }
  }

  // The following are private verification methods ---------------------------------------------

  private static void validateFileSize(MultipartFile file) {
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new JeecgBootException("File size exceeds 10MB limit");
    }
  }

  private static void validateExtension(String extension, String fileName) {

    if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
      throw new JeecgBootException("Invalid file extension: " + extension);
    }
  }

  private static void validateMimeType(String extension, String mimeType, String fileName) {
    Set<String> allowedMimes = EXTENSION_MIME_MAP.get(extension.toLowerCase());
    if (allowedMimes == null || !allowedMimes.contains(mimeType)) {
      throw new JeecgBootException("MIME type mismatch for extension: " + extension);
    }
  }

  private static void validateMagicBytes(MultipartFile file, String extension) {
    try {
      String expectedMagic = MAGIC_BYTES_MAP.get(extension.toLowerCase());
      if (expectedMagic == null) {
        throw new JeecgBootException("Unsupported file type for magic validation");
      }

      String actualMagic = getMagicBytes(file, expectedMagic.length() / 2);
      if (!actualMagic.startsWith(expectedMagic)) {
        throw new JeecgBootException("File header validation failed");
      }
    } catch (IOException e) {
      throw new JeecgBootException("File validation error", e);
    }
  }

  private static void validateFileContent(MultipartFile file, String extension) {
    try {
      switch (extension.toLowerCase()) {
        case "svg":
          validateSvgContent(file);
          break;
        case "zip":
          validateZipContent(file);
          break;
        case "docx":
        case "xlsx":
        case "pptx":
          validateOfficeOpenXML(file);
          break;
        case "pdf":
          validatePDFStructure(file);
          break;
        default:
          // otherTypeExpandablechecklogic
      }
    } catch (Exception e) {
      throw new JeecgBootException("File content validation failed", e);
    }
  }

  // specificcheckaccomplish ---------------------------------------------------

  private static void validateSvgContent(MultipartFile file) throws IOException {
    String content = new String(file.getBytes(), StandardCharsets.UTF_8);

    Pattern dangerousPattern =
        Pattern.compile(
            "(<script|javascript:|onload=|<img[^>]+onerror=|<iframe|xlink:href|<object|<embed)",
            Pattern.CASE_INSENSITIVE);
    Matcher matcher = dangerousPattern.matcher(content);
    if (matcher.find()) {
      throw new JeecgBootException("SVG contains dangerous content");
    }
  }

  private static void validateZipContent(MultipartFile file) throws IOException {
    try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String name = entry.getName().toLowerCase();

        // Defending against path traversal and executable files
        if (name.contains("..")) {
          throw new JeecgBootException("ZIP contains invalid path: " + name);
        }
        if (name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".sh")) {
          throw new JeecgBootException("ZIP contains executable: " + name);
        }
      }
    }
  }

  private static void validateOfficeOpenXML(MultipartFile file) {
    try (OPCPackage pkg = OPCPackage.open(file.getInputStream())) {
      // Verify basic structural integrity
      if (pkg.getPackageProperties() == null) {
        throw new JeecgBootException("Invalid Office OpenXML document");
      }
    } catch (Exception e) {
      throw new JeecgBootException("Office file validation failed", e);
    }
  }

  private static void validatePDFStructure(MultipartFile file) {
    try (PDDocument doc = PDDocument.load(file.getInputStream())) {
      // Simplify validation and only check if there is an interactive form
      if (doc.getDocumentCatalog().getAcroForm() != null) {
        if (!doc.getDocumentCatalog().getAcroForm().getFields().isEmpty()) {
          throw new JeecgBootException("PDF contains interactive forms");
        }
      }
      // Other validation logic can continue to be processed
    } catch (IOException e) {
      throw new JeecgBootException("PDF validation failed", e);
    }
  }

  // Helper method ------------------------------------------------------

  private static String getExtension(String fileName) {
    if (fileName == null) {
      throw new JeecgBootException("File has no extension");
    }

    fileName = fileName.replace("\0", "");

    if (fileName.contains(":")) {
      throw new JeecgBootException("Illegal NTFS file detected");
    }

    // Only a single legal extension is allowed, preventing `shell.php.jpg`
    Matcher matcher = Pattern.compile("\\.([a-zA-Z0-9]+)$").matcher(fileName);
    if (matcher.find()) {
      return matcher.group(1).toLowerCase();
    }

    throw new JeecgBootException("Invalid file extension");
  }

  private static String getMagicBytes(MultipartFile file, int length) throws IOException {
    byte[] bytes = new byte[length];
    try (InputStream is = file.getInputStream()) {
      int read = is.read(bytes);
      if (read != length) {
        throw new IOException("Insufficient data for magic bytes");
      }
    }
    return bytesToHex(bytes);
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02X", b));
    }
    return sb.toString();
  }

  private static String generateSafeFileName(String extension) {
    return UUID.randomUUID() + "." + extension;
  }

  @SneakyThrows
  private static void scanForMalware(MultipartFile file) {
    // Get the operating systemname
    String osName = System.getProperty("os.name").toLowerCase();
    String clamScanCommand;

    // Select command based on operating system
    if (osName.contains("win")) {
      clamScanCommand = "C:\\Program Files\\ClamAV\\clamscan.exe";
    } else {
      clamScanCommand = "clamscan";
    }

    // Create process builder
    ProcessBuilder processBuilder = new ProcessBuilder(clamScanCommand, "--stdout", "-");
    processBuilder.redirectErrorStream(true);
    Process process = processBuilder.start();

    // WillFile contentWriting to the input stream of the scan process
    try (OutputStream os = process.getOutputStream()) {
      os.write(file.getBytes());
    }

    // Get scan results
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      StringBuilder result = new StringBuilder();
      while ((line = reader.readLine()) != null) {
        result.append(line).append("\n");
      }
      log.debug("Scan result: " + result.toString());
    }

    // Wait for the scanning process to end
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new JeecgBootException("File failed virus scan");
    }
  }
}
