package org.jeecg.common.valid.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.jeecg.common.valid.DictionaryValidator;
import org.jeecg.common.valid.ImageValidator;

/** Generic image validation annotations. */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ImageValidator.class) // Specify validator
public @interface ValidImage {

  // DefaultErrorMessage
  String message() default "Invalid image value";

  // Groups, typically for grouped validation
  Class<?>[] groups() default {};

  // Payload; may carry extra info
  Class<? extends Payload>[] payload() default {};

  // Whether to ignore administrator roles
  boolean ignoreAdmin() default false;

  int size() default 1;
}
