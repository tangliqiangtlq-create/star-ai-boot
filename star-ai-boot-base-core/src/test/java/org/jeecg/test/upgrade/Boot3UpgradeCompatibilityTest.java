package org.jeecg.test.upgrade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxl.job.core.biz.model.ReturnT;
import java.lang.reflect.Method;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.dto.FileBytesUploadDTO;
import org.jeecg.common.aspect.annotation.LimitSubmit;
import org.jeecg.common.util.ApiInputSanitizer;
import org.jeecg.common.util.CommonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Boot 3 / component upgrade regression: verify Jakarta, commons-lang3, XXL-JOB 3.x, Sa-Token,
 * and other critical capabilities did not break existing behavior.
 */
class Boot3UpgradeCompatibilityTest {

  @Test
  @DisplayName("StringUtils behavior consistent after replacing commons-lang with commons-lang3")
  void commonsLang3StringUtilsWorks() {
    assertTrue(StringUtils.isNotBlank("mightyPay"));
    assertTrue(StringUtils.isBlank("   "));
    assertEquals("abc", StringUtils.trim("  abc  "));
  }

  @Test
  @DisplayName("FileBytesUploadDTO can be constructed normally (Feign file upload DTO)")
  void fileBytesUploadDtoSerializableFields() {
    FileBytesUploadDTO dto = new FileBytesUploadDTO();
    dto.setBase64Content("dGVzdA==");
    dto.setFileName("report.pdf");
    dto.setContentType("application/pdf");
    assertEquals("report.pdf", dto.getFileName());
    assertEquals("application/pdf", dto.getContentType());
  }

  @Test
  @DisplayName("@LimitSubmit retains key attribute (MightyPay API duplicate-submit prevention)")
  void limitSubmitAnnotationKeepsKeyAttribute() throws NoSuchMethodException {
    Method method = SampleController.class.getMethod("upload", String.class);
    LimitSubmit limitSubmit = method.getAnnotation(LimitSubmit.class);
    assertNotNull(limitSubmit);
    assertEquals("SampleController_upload:%s", limitSubmit.key());
    assertEquals(10, limitSubmit.limit());
  }

  @Test
  @DisplayName("ApiInputSanitizer XSS blocking logic works correctly")
  void apiInputSanitizerBlocksScriptTags() {
    assertDoesNotThrow(() -> ApiInputSanitizer.assertNoXssInQueryParam("name", "normal-value"));
    assertThrows(
        SecurityException.class,
        () -> ApiInputSanitizer.assertNoXssInQueryParam("name", "<script>alert(1)</script>"));
    assertEquals("agent_01", ApiInputSanitizer.normalizeFilterCode("agent_01"));
    assertThrows(
        SecurityException.class, () -> ApiInputSanitizer.normalizeFilterCode("bad;drop"));
  }

  @Test
  @DisplayName("XXL-JOB 3.x ReturnT.ofSuccess / ofFail API available")
  void xxlJobReturnTypeApi() {
    ReturnT<String> success = ReturnT.ofSuccess();
    assertEquals(ReturnT.SUCCESS_CODE, success.getCode());

    ReturnT<String> fail = ReturnT.ofFail("error");
    assertEquals(ReturnT.FAIL_CODE, fail.getCode());
    assertEquals("error", fail.getMsg());
  }

  @Test
  @DisplayName("CommonUtils path/extension sanitization (commons-io + lang3)")
  void commonUtilsSanitizationWorks() {
    assertEquals("demo", CommonUtils.sanitizePathSegment("../demo"));
    assertEquals("png", CommonUtils.sanitizeFileExtension("png"));
    assertTrue(CommonUtils.ifContainChinese("Test"));
  }

  static class SampleController {
    @LimitSubmit(key = "SampleController_upload:%s")
    public void upload(String id) {}
  }
}
