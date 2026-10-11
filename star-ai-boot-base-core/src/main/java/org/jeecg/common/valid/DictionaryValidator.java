package org.jeecg.common.valid;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.valid.annotation.ValidDictionary;

/**
 * Universal dictionary validator
 */
@Slf4j
public class DictionaryValidator implements ConstraintValidator<ValidDictionary, Object> {

  private String table;
  private String text;
  private String code;
  private DictScope scope;

  private CommonAPI commonAPI;
  private DictTableLookup dictTableLookup;

  @Override
  public void initialize(ValidDictionary constraintAnnotation) {
    // Get the parameter value in the annotation
    this.table = constraintAnnotation.table();
    this.text = constraintAnnotation.text();
    this.code = constraintAnnotation.code();
    this.scope = constraintAnnotation.scope();
  }

  @Override
  public boolean isValid(Object value, ConstraintValidatorContext context) {
    // Allow null values to pass validation
    if (value == null || StrUtil.isBlank(value.toString())) {
      return true;
    }

    // Get the CommonAPI instance in the Spring container
    if (commonAPI == null) {
      commonAPI = SpringContextUtils.getBean(CommonAPI.class);
    }

    // Choose different verification methods based on annotation parameters
    if (StrUtil.isNotBlank(table) && StrUtil.isNotBlank(text) && StrUtil.isNotBlank(code)
    ) {
      // Validation based on Dictionary table, text, code and key
      String dictValue = translateTableDict(value.toString());
      return StrUtil.isNotBlank(dictValue);
    } else if (StrUtil.isNotBlank(code)) {
      // Authentication based on code and key
      String dictValue = commonAPI.translateDict(code, value.toString());
      return StrUtil.isNotBlank(dictValue);
    } else {
      return false; // If the annotation does not provide enough information, validation fails
    }
  }

  /**
   * Table backed lookups run against the scope declared on the annotation: this service's own data
   * source (default) or, for system owned tables, across services through {@code CommonAPI}.
   */
  private String translateTableDict(String key) {
    if (scope == DictScope.SYSTEM) {
      return commonAPI.translateDictFromTable(table, text, code, key);
    }
    if (dictTableLookup == null) {
      dictTableLookup = resolveLocalLookup();
    }
    if (dictTableLookup != null) {
      return dictTableLookup.translate(table, text, code, key);
    }
    // No local lookup available (e.g. outside a Spring context); keep the previous behaviour.
    return commonAPI.translateDictFromTable(table, text, code, key);
  }

  private DictTableLookup resolveLocalLookup() {
    try {
      return SpringContextUtils.getBean(DictTableLookup.class);
    } catch (Exception e) {
      log.warn("DictTableLookup bean unavailable; falling back to CommonAPI for table={}", table);
      return null;
    }
  }
}