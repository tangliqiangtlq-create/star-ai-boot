package org.jeecg.common.valid.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.jeecg.common.valid.DictScope;
import org.jeecg.common.valid.DictionaryValidator;

/** Universal dictionary validation annotations. Two verification methods are supported: based on table, text, code, key or code and key. */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DictionaryValidator.class) // Specify validator
public @interface ValidDictionary {

  // DefaultErrorMessage
  String message() default "Invalid dictionary value";

  // Groups, typically for grouped validation
  Class<?>[] groups() default {};

  // Payload; may carry extra info
  Class<? extends Payload>[] payload() default {};

  // Dictionary table name (optional)
  String table() default "";

  // DictionaryField (optional)
  String text() default "";

  // Dictionary code (required)
  String code() default "";

  // Which data source the table lookup runs against; defaults to this service's own schema
  DictScope scope() default DictScope.LOCAL;
}
