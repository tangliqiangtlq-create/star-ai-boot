package org.jeecg.common.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.springframework.web.multipart.MultipartFile;

/**
 * in accordance withFile content（magic number,ZIP structure,OLE、text features, etc.) to infer the extension without relying on the client-providedFile nameor extension.
 * used to circumvent SECZ-SSCSP-CODE-040（Relying on externally provided filesnameor extension determines software behavior).
 */
public final class FileContentSniffer {

  private FileContentSniffer() {}

  /** and {@link org.jeecg.common.util.filter.SsrfFileTypeFilter} executable prohibited in/Script class headers are consistent */
  private static final Map<String, String> DANGEROUS_HEX_PREFIX = new HashMap<>();

  static {
    DANGEROUS_HEX_PREFIX.put("3c25402070616765206c", "jsp");
    DANGEROUS_HEX_PREFIX.put("3c3f7068700a0a2f2a2a0a202a205048", "php");
    DANGEROUS_HEX_PREFIX.put("cafebabe0000002e0041", "class");
    DANGEROUS_HEX_PREFIX.put("494e5345525420494e54", "sql");
  }

  /**
   * fromFile contentInfer extension (lowercase), returned if unrecognized null；If it is dangerousTypethen the corresponding extension (rejected by the caller) is returned.
   */
  public static String sniffExtension(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
      return null;
    }
    try (InputStream is = file.getInputStream()) {
      byte[] head = readUpTo(is, 65536);
      if (head.length == 0) {
        return null;
      }
      InputStream full = new SequenceInputStream(new ByteArrayInputStream(head), is);
      return sniffExtensionFromHeadAndStream(head, full);
    }
  }

  /**
   * Sniffing from dropped filesType（used for commons FileItemStream etc. single flow scenario).
   */
  public static String sniffExtension(Path path) throws IOException {
    try (InputStream is = Files.newInputStream(path)) {
      byte[] head = readUpTo(is, 65536);
      if (head.length == 0) {
        return null;
      }
      InputStream full = new SequenceInputStream(new ByteArrayInputStream(head), is);
      return sniffExtensionFromHeadAndStream(head, full);
    }
  }

  private static String sniffExtensionFromHeadAndStream(byte[] head, InputStream full)
      throws IOException {
    String hex = bytesToHexString(head);
    if (hex != null && hex.length() >= 10) {
      String lower = hex.toLowerCase();
      for (Map.Entry<String, String> e : DANGEROUS_HEX_PREFIX.entrySet()) {
        if (lower.startsWith(e.getKey().toLowerCase())) {
          return e.getValue();
        }
      }
    }

    // image
    if (startsWith(head, (byte) 0xff, (byte) 0xd8, (byte) 0xff)) {
      return "jpg";
    }
    if (startsWith(head, (byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47)) {
      return "png";
    }
    if (head.length >= 6
        && head[0] == 'G'
        && head[1] == 'I'
        && head[2] == 'F'
        && head[3] == '8'
        && (head[4] == '7' || head[4] == '9')) {
      return "gif";
    }
    if (startsWith(head, (byte) 0x42, (byte) 0x4d)) {
      return "bmp";
    }
    if (head.length >= 4
        && head[0] == 0
        && head[1] == 0
        && head[2] == 1
        && head[3] == 0) {
      return "ico";
    }

    // PDF
    if (startsWith(head, (byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46)) {
      return "pdf";
    }

    // HEIF / HEIC (ISO BMFF)
    if (head.length >= 12 && bytesAtAscii(head, 4, 4).equals("ftyp")) {
      String brand = bytesAtAscii(head, 8, 4);
      if (brand.startsWith("heic")
          || brand.startsWith("heix")
          || brand.startsWith("mif1")
          || brand.startsWith("msf1")) {
        return "heic";
      }
      if (brand.startsWith("heif")) {
        return "heif";
      }
      // MP4/MOV family
      if (head.length >= 12) {
        String brand4 = bytesAtAscii(head, 8, 4);
        if ("qt  ".equals(brand4) || "moov".equals(brand4)) {
          return "mov";
        }
        if (brand4.startsWith("isom")
            || brand4.startsWith("mp41")
            || brand4.startsWith("mp42")
            || brand4.startsWith("avc1")
            || brand4.startsWith("M4V")
            || brand4.startsWith("M4A")) {
          return "mp4";
        }
      }
    }

    // RAR
    if (head.length >= 7
        && head[0] == 'R'
        && head[1] == 'a'
        && head[2] == 'r'
        && head[3] == '!'
        && head[4] == 0x1a
        && head[5] == 0x07) {
      return "rar";
    }

    // 7z
    if (startsWith(head, (byte) 0x37, (byte) 0x7a, (byte) 0xbc, (byte) 0xaf, (byte) 0x27, (byte) 0x1c)) {
      return "7z";
    }

    // MP3
    if (head.length >= 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') {
      return "mp3";
    }
    if (head.length >= 2 && (head[0] & 0xff) == 0xff && (head[1] & 0xe0) == 0xe0) {
      return "mp3";
    }

    // RIFF (WAV / AVI / WebP)
    if (head.length >= 12
        && head[0] == 'R'
        && head[1] == 'I'
        && head[2] == 'F'
        && head[3] == 'F') {
      String rif = bytesAtAscii(head, 8, 4);
      if ("WAVE".equals(rif)) {
        return "wav";
      }
      if ("AVI ".equals(rif)) {
        return "avi";
      }
    }

    // WMV ASF
    if (head.length >= 16
        && head[0] == 0x30
        && head[1] == 0x26
        && head[2] == (byte) 0xb2
        && head[3] == 0x75) {
      return "wmv";
    }

    // QuickTime part mov by moov beginning
    if (head.length >= 4
        && head[0] == 'm'
        && head[1] == 'o'
        && head[2] == 'o'
        && head[3] == 'v') {
      return "mov";
    }

    // OLE compound document (doc / xls)
    if (startsWith(
        head, (byte) 0xd0, (byte) 0xcf, (byte) 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, (byte) 0x1a, (byte) 0xe1)) {
      return sniffOleSubtype(full);
    }

    // ZIP（Contains OOXML、APK、part wgt）
    if (head.length >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4) {
      return sniffZipFamily(full);
    }

    // tar (ustar)
    if (head.length >= 262) {
      String ustar = new String(head, 257, 5, StandardCharsets.US_ASCII);
      if ("ustar".equals(ustar) || "ustar".equalsIgnoreCase(ustar)) {
        return "tar";
      }
    }

    // SVG / Text class
    if (looksLikeSvg(head)) {
      return "svg";
    }
    if (isMostlyUtf8Text(head)) {
      if (looksLikeCsv(head)) {
        return "csv";
      }
      return "txt";
    }

    return null;
  }

  private static String sniffOleSubtype(InputStream full) throws IOException {
    try (POIFSFileSystem fs = new POIFSFileSystem(full)) {
      if (fs.getRoot().hasEntry("Workbook")) {
        return "xls";
      }
      if (fs.getRoot().hasEntry("WordDocument")) {
        return "doc";
      }
    } catch (Exception ignored) {
      // If parsing fails, press doc processing (the whitelist still contains doc）
    }
    return "doc";
  }

  private static String sniffZipFamily(InputStream full) throws IOException {
    try (ZipInputStream zis = new ZipInputStream(full)) {
      ZipEntry e;
      boolean hasAndroidManifest = false;
      boolean hasRootManifestJson = false;
      boolean hasWord = false;
      boolean hasXl = false;
      boolean hasPpt = false;
      while ((e = zis.getNextEntry()) != null) {
        String name = e.getName().replace('\\', '/');
        if (name.equals("AndroidManifest.xml") || name.endsWith("/AndroidManifest.xml")) {
          hasAndroidManifest = true;
        }
        if ("manifest.json".equals(name) || name.endsWith("/manifest.json")) {
          hasRootManifestJson = true;
        }
        if (name.startsWith("word/")) {
          hasWord = true;
        }
        if (name.startsWith("xl/")) {
          hasXl = true;
        }
        if (name.startsWith("ppt/")) {
          hasPpt = true;
        }
      }
      if (hasAndroidManifest) {
        return "apk";
      }
      if (hasWord) {
        return "docx";
      }
      if (hasXl) {
        return "xlsx";
      }
      if (hasPpt) {
        return "pptx";
      }
      if (hasRootManifestJson) {
        return "wgt";
      }
    } catch (Exception ignored) {
      // Damaged or non-standard ZIP：still regarded as zip Containers, whether allowed or not is determined by the business whitelist
    }
    return "zip";
  }

  private static boolean looksLikeSvg(byte[] head) {
    int i = 0;
    if (head.length >= 3
        && (head[0] & 0xff) == 0xef
        && (head[1] & 0xff) == 0xbb
        && (head[2] & 0xff) == 0xbf) {
      i = 3;
    }
    while (i < head.length
        && (head[i] == ' ' || head[i] == '\t' || head[i] == '\r' || head[i] == '\n')) {
      i++;
    }
    int len = Math.min(head.length - i, 300);
    if (len <= 0) {
      return false;
    }
    String start = new String(head, i, len, StandardCharsets.UTF_8);
    String lower = start.toLowerCase();
    return lower.startsWith("<?xml") || lower.startsWith("<svg");
  }

  private static boolean looksLikeCsv(byte[] head) {
    String sample = new String(head, 0, Math.min(head.length, 4096), StandardCharsets.UTF_8);
    int lines = 0;
    int commas = 0;
    for (int i = 0; i < sample.length() && lines < 5; i++) {
      if (sample.charAt(i) == '\n') {
        lines++;
      }
      if (sample.charAt(i) == ',') {
        commas++;
      }
    }
    return commas >= 2;
  }

  private static boolean isMostlyUtf8Text(byte[] head) {
    int n = Math.min(head.length, 8192);
    int controlExceptWs = 0;
    for (int i = 0; i < n; i++) {
      byte b = head[i];
      if (b == 0) {
        return false;
      }
      if (b < 32 && b != '\t' && b != '\r' && b != '\n') {
        controlExceptWs++;
      }
    }
    return controlExceptWs * 10 < n;
  }

  private static boolean startsWith(byte[] data, byte... prefix) {
    if (data.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (data[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static String bytesAtAscii(byte[] data, int offset, int len) {
    if (data.length < offset + len) {
      return "";
    }
    return new String(data, offset, len, StandardCharsets.US_ASCII);
  }

  private static String bytesToHexString(byte[] src) {
    if (src == null || src.length == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder(src.length * 2);
    for (byte b : src) {
      sb.append(String.format("%02x", b & 0xff));
    }
    return sb.toString();
  }

  private static byte[] readUpTo(InputStream is, int max) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(max, 8192));
    byte[] buf = new byte[8192];
    int total = 0;
    int r;
    while (total < max && (r = is.read(buf, 0, Math.min(buf.length, max - total))) != -1) {
      baos.write(buf, 0, r);
      total += r;
    }
    return baos.toByteArray();
  }
}
