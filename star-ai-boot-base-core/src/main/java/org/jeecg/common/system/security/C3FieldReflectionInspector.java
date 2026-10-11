package org.jeecg.common.system.security;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jeecg.common.util.ReflectionAccessUtil;
import org.jeecg.common.util.oConvertUtils;

public final class C3FieldReflectionInspector {

    private C3FieldReflectionInspector() {}

    public static <T> Set<String> collectDirtyFields(T entity, Set<String> allowedFields) {
        Set<String> dirty = new HashSet<>();
        if (entity == null) {
            return dirty;
        }
        if (allowedFields == null || allowedFields.isEmpty()) {
            return dirty;
        }
        Field[] fields = oConvertUtils.getAllFields(entity);
        if (fields == null) {
            return dirty;
        }
        for (Field f : fields) {
            if (f == null) {
                continue;
            }
            String name = f.getName();
            if (name == null) {
                continue;
            }
            if (!allowedFields.contains(name)) {
                continue;
            }
            Object value;
            try {
                value = ReflectionAccessUtil.getFieldValue(f, entity);
            } catch (Exception e) {
                continue;
            }
            if (value != null) {
                dirty.add(name);
            }
        }
        return dirty;
    }

    public static boolean containsAll(Set<String> actual, Set<String> expected) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (actual == null) {
            actual = Collections.emptySet();
        }
        return actual.containsAll(expected);
    }
}
