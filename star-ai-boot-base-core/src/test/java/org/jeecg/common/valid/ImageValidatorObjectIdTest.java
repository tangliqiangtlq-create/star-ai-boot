package org.jeecg.common.valid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression guard for the {@code @ValidImage} contract mismatch that made
 * {@code POST /dcs/currency/add} answer 400 "Invalid parameter".
 *
 * <p>{@code docs/api-currency.md} documents the field as "object id OR url" and the admin forms /
 * edit backfill submit the stored objectId ({@code upload/yyyyMMdd/<name>}). The validator used to
 * require a request-domain prefix, so the scheme/host-less objectId never validated. These cases
 * pin the intended behaviour: canonical objectIds pass, non-self-hosted URLs do not.
 */
class ImageValidatorObjectIdTest {

  @Test
  void canonicalUploadObjectId_accepted() {
    assertTrue(
        ImageValidator.isBareUploadObjectId(
            "upload/20261002/file_e5d5633a_TZ0-6CbOZpBvSaX44ccEBQ.png"));
  }

  @Test
  void leadingSlashObjectId_normalizedAndAccepted() {
    assertTrue(ImageValidator.isBareUploadObjectId("/upload/20261002/file_abc.png"));
  }

  @Test
  void missingDateSlash_isRepairedAndAccepted() {
    assertTrue(ImageValidator.isBareUploadObjectId("upload/20261002file_abc.png"));
  }

  @Test
  void externalAbsoluteUrl_isNotBareObjectId() {
    // Stays on the domain-prefix path, so a non-self-hosted URL is still rejected there.
    assertFalse(ImageValidator.isBareUploadObjectId("https://evil.example.com/track.png"));
    assertFalse(ImageValidator.isBareUploadObjectId("http://evil.example.com/upload/20261002/x.png"));
  }

  @Test
  void nonCanonicalRelativePath_rejected() {
    assertFalse(ImageValidator.isBareUploadObjectId("upload/notadate/x.png"));
    assertFalse(ImageValidator.isBareUploadObjectId("upload/20261002/a b.png"));
    assertFalse(ImageValidator.isBareUploadObjectId("etc/passwd"));
    assertFalse(ImageValidator.isBareUploadObjectId(""));
    assertFalse(ImageValidator.isBareUploadObjectId(null));
  }
}
