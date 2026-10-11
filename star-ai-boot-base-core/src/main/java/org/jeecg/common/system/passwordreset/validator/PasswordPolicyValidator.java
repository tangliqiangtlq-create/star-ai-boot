package org.jeecg.common.system.passwordreset.validator;

import lombok.Data;
import org.jeecg.common.constant.CommonConstant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.regex.Pattern;

/**
 * Password strength checker (can be reused by any module, placed in the base-core public layer).
 *
 * Default Strict Mode vs. Existing CommonConstant.PASSWORD_REGEX Alignment:
 *   ^(?=.*[\W_])(?=.*[A-Z])(?=.*[a-z])(?=.*\d{3,}).{12,}$
 *   —— Contains special characters/Underlines, uppercase and lowercase letters,≥3 digits,≥12 Bit.
 *
 * Passable Nacos `jeecg.password-reset.policy.*` Covering a single requirement,
 * or set `jeecg.password-reset.policy.use-common-constant-regex=false` + Each configuration can be freely combined.
 */
@Data
@Component
public class PasswordPolicyValidator {

    @Value("${jeecg.password-reset.policy.use-common-constant-regex:true}")
    private boolean useCommonConstantRegex = true;

    @Value("${jeecg.password-reset.policy.min-length:12}")
    private int minLength = 12;

    @Value("${jeecg.password-reset.policy.max-length:64}")
    private int maxLength = 64;

    @Value("${jeecg.password-reset.policy.require-uppercase:true}")
    private boolean requireUppercase = true;

    @Value("${jeecg.password-reset.policy.require-lowercase:true}")
    private boolean requireLowercase = true;

    @Value("${jeecg.password-reset.policy.require-number:true}")
    private boolean requireNumber = true;

    @Value("${jeecg.password-reset.policy.require-number-at-least:3}")
    private int requireNumberAtLeast = 3;

    @Value("${jeecg.password-reset.policy.require-symbol:true}")
    private boolean requireSymbol = true;

    @Value("${jeecg.password-reset.policy.disallow-username-substring:true}")
    private boolean disallowUsernameSubstring = true;

    @Value("${jeecg.password-reset.policy.disallow-username-substring-length:4}")
    private int disallowUsernameSubstringLength = 4;

    private Pattern cachedCommonConstantPattern;

    @PostConstruct
    public void init() {
        if (useCommonConstantRegex) {
            cachedCommonConstantPattern = Pattern.compile(CommonConstant.PASSWORD_REGEX);
        }
    }

    /**
     * Validate password strength.
     *
     * @param plainPassword plaintext password (after RSA decryption)
     * @param username      target account (for username substring check; may be null)
     * @param extraContext  phone/email etc. (extensible; currently only username is used)
     * @return validation result: valid=true if valid; valid=false with message providing hint
     */
    public ValidationResult validate(String plainPassword, String username, String... extraContext) {
        if (!StringUtils.hasText(plainPassword)) {
            return ValidationResult.fail("Password cannot be empty");
        }
        if (useCommonConstantRegex) {
            if (!cachedCommonConstantPattern.matcher(plainPassword).matches()) {
                return ValidationResult.fail("Password must be at least 12 characters and include uppercase, lowercase, at least 3 digits, and a special character");
            }
            // Even if the pattern hits, additional substring judgment will be done (independent of regularity)
            if (disallowUsernameSubstring && StringUtils.hasText(username)) {
                if (containsSubstringLongerThan(username.toLowerCase(), plainPassword.toLowerCase(), disallowUsernameSubstringLength)) {
                    return ValidationResult.fail("Password must not contain a consecutive substring from the username (≥" + disallowUsernameSubstringLength + " characters)");
                }
            }
            return ValidationResult.ok();
        }
        // Free Mode: Fieldcheck by Fieldcheck
        int len = plainPassword.codePointCount(0, plainPassword.length());
        if (len < minLength) return ValidationResult.fail("Password must be at least " + minLength + " characters");
        if (len > maxLength) return ValidationResult.fail("Password must be at most " + maxLength + " characters");
        if (requireUppercase && !plainPassword.chars().anyMatch(Character::isUpperCase)) {
            return ValidationResult.fail("Password must include an uppercase letter");
        }
        if (requireLowercase && !plainPassword.chars().anyMatch(Character::isLowerCase)) {
            return ValidationResult.fail("Password must include a lowercase letter");
        }
        if (requireNumber) {
            long digits = plainPassword.chars().filter(Character::isDigit).count();
            if (digits < requireNumberAtLeast) {
                return ValidationResult.fail("Password must include at least " + requireNumberAtLeast + " digits");
            }
        }
        if (requireSymbol) {
            boolean hasSymbol = plainPassword.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
            if (!hasSymbol) {
                return ValidationResult.fail("Password must include a special character");
            }
        }
        if (disallowUsernameSubstring && StringUtils.hasText(username)) {
            if (containsSubstringLongerThan(username.toLowerCase(), plainPassword.toLowerCase(), disallowUsernameSubstringLength)) {
                return ValidationResult.fail("Password must not contain a consecutive substring from the username (≥" + disallowUsernameSubstringLength + " characters)");
            }
        }
        return ValidationResult.ok();
    }

    private static boolean containsSubstringLongerThan(String needleSrc, String haystack, int minLen) {
        if (needleSrc.length() < minLen) return false;
        for (int i = 0; i + minLen <= needleSrc.length(); i++) {
            String sub = needleSrc.substring(i, i + minLen);
            if (haystack.contains(sub)) return true;
        }
        return false;
    }

    @Data
    public static class ValidationResult {
        private boolean valid;
        private String message;

        public static ValidationResult ok() {
            ValidationResult r = new ValidationResult();
            r.valid = true;
            return r;
        }

        public static ValidationResult fail(String message) {
            ValidationResult r = new ValidationResult();
            r.valid = false;
            r.message = message;
            return r;
        }
    }
}
