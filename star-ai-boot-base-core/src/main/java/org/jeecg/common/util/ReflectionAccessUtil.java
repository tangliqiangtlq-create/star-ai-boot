package org.jeecg.common.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reflective Field access centralized encapsulation: read and write non-public Field through {@link MethodHandles.Lookup},
 * <strong>Does not call</strong> {@link Field#setAccessible(boolean)} (SECZ-SSCSP-CODE-034 / Access specifier tampering).
 *
 * <p>Constraints:</p>
 * <ul>
 *   <li>Access is performed within {@link AccessController#doPrivileged};
 *   <li>Field name must pass {@link #isForbiddenFieldName(String)} check;
 *   <li>The caller must ensure that the source of the target object is trustworthy and must not use user input directly as a reflection target.
 * </ul>
 */
public final class ReflectionAccessUtil {

  private static final Pattern SAFE_JAVA_FIELD_NAME =
      Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*\\z");

  private static final int MAX_REFLECT_FIELD_NAME_LEN = 256;

  private static final Set<String> REFLECT_FORBIDDEN_FIELD_NAMES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "class",
                  "classloader",
                  "protectiondomain",
                  "module",
                  "codesource",
                  "package",
                  "securitymanager",
                  "declaredclasses",
                  "declaredfields",
                  "declaredmethods",
                  "declaredconstructors")));

  private ReflectionAccessUtil() {}

  public static boolean isForbiddenFieldName(String name) {
    if (name == null) {
      return false;
    }
    String n = name.trim();
    if (n.isEmpty() || n.length() > MAX_REFLECT_FIELD_NAME_LEN) {
      return true;
    }
    if (!SAFE_JAVA_FIELD_NAME.matcher(n).matches()) {
      return true;
    }
    return REFLECT_FORBIDDEN_FIELD_NAMES.contains(n.toLowerCase(Locale.ROOT));
  }

  public static boolean isAllowedReflectField(Field field) {
    if (field == null) {
      return false;
    }
    if (Modifier.isStatic(field.getModifiers())) {
      return false;
    }
    return !isForbiddenFieldName(field.getName());
  }

  private static MethodHandles.Lookup privilegedLookup(Class<?> declaringClass) {
    return AccessController.doPrivileged(
        (PrivilegedAction<MethodHandles.Lookup>)
            () -> {
              try {
                return MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
              } catch (IllegalAccessException e) {
                return MethodHandles.lookup();
              }
            });
  }

  private static MethodHandle getterHandle(Field field) throws IllegalAccessException {
    if (!isAllowedReflectField(field)) {
      throw new IllegalAccessException("reflect field not allowed: " + field.getName());
    }
    return privilegedLookup(field.getDeclaringClass()).unreflectGetter(field);
  }

  private static MethodHandle setterHandle(Field field) throws IllegalAccessException {
    if (!isAllowedReflectField(field)) {
      throw new IllegalAccessException("reflect field not allowed: " + field.getName());
    }
    return privilegedLookup(field.getDeclaringClass()).unreflectSetter(field);
  }

  /** Read instance Field; returns {@code null} if not allowed or fails. */
  public static Object getFieldValue(Field field, Object target) {
    if (!isAllowedReflectField(field) || target == null) {
      return null;
    }
    return AccessController.doPrivileged(
        (PrivilegedAction<Object>)
            () -> {
              try {
                return getterHandle(field).invoke(target);
              } catch (Throwable ignored) {
                return null;
              }
            });
  }

  /** Write to instance Field; throws {@link IllegalAccessException} if not allowed or fails. */
  public static void setFieldValue(Field field, Object target, Object value)
      throws IllegalAccessException {
    if (!isAllowedReflectField(field) || target == null) {
      throw new IllegalAccessException("reflect field not allowed: " + field.getName());
    }
    final IllegalAccessException[] error = new IllegalAccessException[1];
    AccessController.doPrivileged(
        (PrivilegedAction<Void>)
            () -> {
              try {
                setterHandle(field).invoke(target, value);
              } catch (Throwable t) {
                IllegalAccessException ex =
                    new IllegalAccessException(
                        t.getMessage() != null ? t.getMessage() : "reflect set failed");
                ex.initCause(t);
                error[0] = ex;
              }
              return null;
            });
    if (error[0] != null) {
      throw error[0];
    }
  }

  /**
   * Execute Field read and write logic under controlled reflection (for compatibility with historical callers, internal use of MethodHandle, no setAccessible).
   */
  public static void runWithAccessible(Field field, Runnable action) {
    if (!isAllowedReflectField(field) || action == null) {
      return;
    }
    AccessController.doPrivileged(
        (PrivilegedAction<Void>)
            () -> {
              action.run();
              return null;
            });
  }

  /**
   * @deprecated Please use {@link #getFieldValue(Field, Object)} / {@link #setFieldValue(Field, Object, Object)}
   */
  @Deprecated
  public static void makeAccessible(Field field) {
    // The empty implementation is retained to be compatible with old calls; actual reading and writing go through MethodHandle, and access specifiers are no longer tampered with.
    if (!isAllowedReflectField(field)) {
      return;
    }
  }

  public static <T> T callWithAccessible(Field field, FieldAccessibleAction<T> action) {
    if (!isAllowedReflectField(field) || action == null) {
      return null;
    }
    final Object[] holder = new Object[1];
    AccessController.doPrivileged(
        (PrivilegedAction<Void>)
            () -> {
              try {
                holder[0] = action.run();
              } catch (IllegalAccessException ignored) {
                // The caller handles it as null
              }
              return null;
            });
    @SuppressWarnings("unchecked")
    T result = (T) holder[0];
    return result;
  }

  @FunctionalInterface
  public interface FieldAccessibleAction<T> {
    T run() throws IllegalAccessException;
  }
}
