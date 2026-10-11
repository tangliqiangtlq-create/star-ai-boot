package org.jeecg.common.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * SECZ-SSCSP-CODE-032: In-memory handling of sensitive UTF-8 text.
 *
 * <p>Convert the decrypted or read UTF-8 bytes into {@code char[]}, and zero out the original byte array before returning, shortening the time the plaintext resides in the variable buffer.
 * The caller should use {@link Arrays#fill(char[], char)} to zero out {@code char[]} when it is no longer needed.
 */
public final class SensitiveUtf8 {

  private SensitiveUtf8() {}

  /**
   * Decode the bytes of UTF-8 coding into a character array and set all {@code utf8Bytes} to zero before returning.
   *
   * @param utf8Bytes plain text UTF-8 bytes (can be null)
   * @return the decoded character; if the input is null, return null
   */
  public static char[] charsFromUtf8BytesWiping(byte[] utf8Bytes) {
    if (utf8Bytes == null) {
      return null;
    }
    try {
      CharBuffer cb = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(utf8Bytes));
      char[] chars = new char[cb.remaining()];
      cb.get(chars);
      return chars;
    } finally {
      Arrays.fill(utf8Bytes, (byte) 0);
    }
  }
}
