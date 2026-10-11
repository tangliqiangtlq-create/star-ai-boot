package org.jeecg.common.util;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Zhang Daihao
 * @desc dynamically calls get and set methods through reflection
 */
@Slf4j
public class ReflectHelper {

    private Class cls;

    /**
     * Incoming object.
     */
    private Object obj;

    /**
     * Store get method
     */
    private Hashtable<String, Method> getMethods = null;
    /**
     * Store set method
     */
    private Hashtable<String, Method> setMethods = null;

    /**
     * Define constructor — typically a POJO
     *
     * @param o target object
     */
    public ReflectHelper(Object o) {
        obj = o;
        initMethods();
    }

    /**
     * @desc Initialize
     */
    public void initMethods() {
        getMethods = new Hashtable<String, Method>();
        setMethods = new Hashtable<String, Method>();
        cls = obj.getClass();
        Method[] methods = cls.getMethods();
        // Define regular expressions to filter out getter/setter functions from methods.
        String gs = "get(\\w+)";
        Pattern getM = Pattern.compile(gs);
        String ss = "set(\\w+)";
        Pattern setM = Pattern.compile(ss);
        // Remove "set" or "get" from the method
        String rapl = "$1";
        String param;
        for (int i = 0; i < methods.length; ++i) {
            Method m = methods[i];
            String methodName = m.getName();
            if (Pattern.matches(gs, methodName)) {
                param = getM.matcher(methodName).replaceAll(rapl).toLowerCase();
                getMethods.put(param, m);
            } else if (Pattern.matches(ss, methodName)) {
                param = setM.matcher(methodName).replaceAll(rapl).toLowerCase();
                setMethods.put(param, m);
            } else {
                // logger.info(methodName + "Not a getter, setter method!");
            }
        }
    }

    /**
     * @desc InvokesetMethod
     */
    public boolean setMethodValue(String property, Object object) {
        Method m = setMethods.get(property.toLowerCase());
        if (m != null) {
            try {
                // Call the setter function of the target class
                m.invoke(obj, object);
                return true;
            } catch (Exception ex) {
                log.info("invoke getter on " + property + " error: " + ex.toString());
                return false;
            }
        }
        return false;
    }

    /**
     * @desc InvokesetMethod
     */
    public Object getMethodValue(String property) {
        Object value = null;
        Method m = getMethods.get(property.toLowerCase());
        if (m != null) {
            try {
                /*
                 * Invoke the obj class setter method
                 */
                value = m.invoke(obj, new Object[]{});

            } catch (Exception ex) {
                log.info("invoke getter on " + property + " error: " + ex.toString());
            }
        }
        return value;
    }

    /**
     * Inject all map entries into obj
     *
     * @param data
     * @return
     */
    public Object setAll(Map<String, Object> data) {
        if (data == null || data.keySet().size() <= 0) {
            return null;
        }
        for (Entry<String, Object> entry : data.entrySet()) {
            this.setMethodValue(entry.getKey(), entry.getValue());
        }
        return obj;
    }

    /**
     * Inject all map entries into obj
     *
     * @param o
     * @param data
     * @return
     */
    public static Object setAll(Object o, Map<String, Object> data) {
        ReflectHelper reflectHelper = new ReflectHelper(o);
        reflectHelper.setAll(data);
        return o;
    }

    /**
     * Inject all map entries into a new instance
     *
     * @param clazz
     * @param data
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T setAll(Class<T> clazz, Map<String, Object> data) {
        T o = null;
        try {
            o = clazz.newInstance();
        } catch (Exception e) {
            log.error("operation failed", e);
            o = null;
            return o;
        }
        return (T) setAll(o, data);
    }

    /**
     * Convert mapList to entity list by the given class
     *
     * @param mapist
     * @param clazz
     * @return
     */
    public static <T> List<T> transList2Entrys(List<Map<String, Object>> mapist, Class<T> clazz) {
        List<T> list = new ArrayList<T>();
        if (mapist != null && mapist.size() > 0) {
            for (Map<String, Object> data : mapist) {
                list.add(ReflectHelper.setAll(clazz, data));
            }
        }
        return list;
    }

    /**
     * Get property value by property name
     */
    public static Object getFieldValueByName(String fieldName, Object o) {
        try {
            String firstLetter = fieldName.substring(0, 1).toUpperCase();
            String getter = "get" + firstLetter + fieldName.substring(1);
            Method method = o.getClass().getMethod(getter, new Class[]{});
            Object value = method.invoke(o, new Object[]{});
            return value;
        } catch (Exception e) {
            log.error("operation failed", e);
            return null;
        }
    }

    /**
     * Get property value
     */
    public static Object getFieldVal(String fieldName, Object o) {
        try {
            Field filed = o.getClass().getDeclaredField(fieldName);
            return org.jeecg.common.util.ReflectionAccessUtil.getFieldValue(filed, o);
        } catch (Exception e) {
            log.error("operation failed", e);
            return null;
        }
    }

    /**
     * Get property name array
     */
    public static String[] getFiledName(Object o) {
        Field[] fields = o.getClass().getDeclaredFields();
        String[] fieldNames = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            //log.info(fields[i].getType());
            fieldNames[i] = fields[i].getName();
        }
        return fieldNames;
    }

    /**
     * Get list of maps with type, name, and value
     */
    public static List<Map> getFiledsInfo(Object o) {
        Field[] fields = o.getClass().getDeclaredFields();
        String[] fieldNames = new String[fields.length];
        List<Map> list = new ArrayList<Map>();
        Map<String, Object> infoMap = null;
        for (int i = 0; i < fields.length; i++) {
            infoMap = new HashMap<>(5);
            infoMap.put("type", fields[i].getType().toString());
            infoMap.put("name", fields[i].getName());
            infoMap.put("value", getFieldValueByName(fields[i].getName(), o));
            list.add(infoMap);
        }
        return list;
    }

    /**
     * Get all property values of object; return object array
     */
    public static Object[] getFiledValues(Object o) {
        String[] fieldNames = getFiledName(o);
        Object[] value = new Object[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            value[i] = getFieldValueByName(fieldNames[i], o);
        }
        return value;
    }

    /**
     * Check whether the given field is a property of the class.
     * @param field field name
     * @param clazz class object
     * @return
     */
    public static boolean isClassField(String field, Class clazz){
        Field[] fields = clazz.getDeclaredFields();
        for(int i=0;i<fields.length;i++){
            String fieldName = fields[i].getName();
            String tableColumnName = oConvertUtils.camelToUnderline(fieldName);
            if(fieldName.equalsIgnoreCase(field) || tableColumnName.equalsIgnoreCase(field)){
                return true;
            }
        }
        return false;
    }

    /**
     * Get class fields including parent class
     * @param clazz
     * @return
     */
    public static List<Field> getClassFields(Class<?> clazz) {
        List<Field> list = new ArrayList<Field>();
        Field[] fields;
        do{
            fields = clazz.getDeclaredFields();
            for(int i = 0;i<fields.length;i++){
                list.add(fields[i]);
            }
            clazz = clazz.getSuperclass();
        }while(clazz!= Object.class&&clazz!=null);
        return list;
    }

    /**
     * Get table field name
     * @param clazz
     * @param name
     * @return
     */
    public static String getTableFieldName(Class<?> clazz, String name) {
        try {
            //If the Field is annotated with @TableField(exist = false), DB query will not be performed.
            Field field = null;
            try {
                field = clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                //log.error("operation failed", e);
            }

            //If it is empty, go to the parent class to find Field
            if (field == null) {
                List<Field> allFields = getClassFields(clazz);
                List<Field> searchFields = allFields.stream().filter(a -> a.getName().equals(name)).collect(Collectors.toList());
                if(searchFields!=null && searchFields.size()>0){
                    field = searchFields.get(0);
                }
            }

            if (field != null) {
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null){
                    if(tableField.exist() == false){
                        //If TableField is set to false, this Field does not need to be processed.
                        return null;
                    }else{
                        String column = tableField.value();
                        //If TableField value is set, this Field is an entity Field
                        if(!"".equals(column)){
                            return column;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("operation failed", e);
        }
        return name;
    }

}