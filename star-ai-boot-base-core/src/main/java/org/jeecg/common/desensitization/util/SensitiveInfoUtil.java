package org.jeecg.common.desensitization.util;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.desensitization.annotation.SensitiveField;
import org.jeecg.common.desensitization.enums.SensitiveEnum;
import org.jeecg.common.util.encryption.AesEncryptUtil;
import org.jeecg.common.util.oConvertUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.List;

/**
 * Sensitive information processing tools
 * @author taoYan
 * @date 2022/4/20 18:01
 **/
@Slf4j
public class SensitiveInfoUtil {

    /**
     * Process nested object
     * @param obj method return value
     * @param entity entity class
     * @param isEncode whether to encrypt (true: encrypt / false: decrypt)
     * @throws IllegalAccessException
     */
    public static void handleNestedObject(Object obj, Class entity, boolean isEncode) throws IllegalAccessException {
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            if(field.getType().isPrimitive()){
                continue;
            }
            if(field.getType().equals(entity)){
                // There is an entity inside the object
                Object nestedObject = org.jeecg.common.util.ReflectionAccessUtil.getFieldValue(field, obj);
                handlerObject(nestedObject, isEncode);
                break;
            }else{
                // Inside the object is List<entity>
                if(field.getGenericType() instanceof ParameterizedType){
                    ParameterizedType pt = (ParameterizedType)field.getGenericType();
                    if(pt.getRawType().equals(List.class)){
                        if(pt.getActualTypeArguments()[0].equals(entity)){
                            Object nestedObject = org.jeecg.common.util.ReflectionAccessUtil.getFieldValue(field, obj);
                            handleList(nestedObject, entity, isEncode);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Process Object
     * @param obj method return value
     * @param isEncode whether to encrypt (true: encrypt / false: decrypt)
     * @return
     * @throws IllegalAccessException
     */
    public static Object handlerObject(Object obj, boolean isEncode) throws IllegalAccessException {
        if (oConvertUtils.isEmpty(obj)) {
            return obj;
        }
        long startTime=System.currentTimeMillis();
        log.debug(" obj --> "+ obj.toString());
        
        // Determine whether it is an object
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            boolean isSensitiveField = field.isAnnotationPresent(SensitiveField.class);
            if(isSensitiveField){
                // Must have SensitiveField annotation for processing
                if(field.getType().isAssignableFrom(String.class)){
                    //Must be a string Type for processing
                    String realValue = (String) org.jeecg.common.util.ReflectionAccessUtil.getFieldValue(field, obj);
                    if(realValue==null || "".equals(realValue)){
                        continue;
                    }
                    SensitiveField sf = field.getAnnotation(SensitiveField.class);
                    if(isEncode==true){
                        //Encrypt
                        String value = SensitiveInfoUtil.getEncodeData(realValue,  sf.type());
                        org.jeecg.common.util.ReflectionAccessUtil.setFieldValue(field, obj, value);
                    }else{
                        //Decryption only processes encodeType
                        if(sf.type().equals(SensitiveEnum.ENCODE)){
                            String value = SensitiveInfoUtil.getDecodeData(realValue);
                            org.jeecg.common.util.ReflectionAccessUtil.setFieldValue(field, obj, value);
                        }
                    }
                }
            }
        }
        //long endTime=System.currentTimeMillis();
        //log.info((isEncode ? "Encrypt operation," : "Decryption operation,") + "Current program Elapsed time:" + (endTime - startTime) + "ms");
        return obj;
    }

    /**
     * Process List<Entity>
     * @param obj
     * @param entity
     * @param isEncode (true: encrypt / false: decrypt)
     */
    public static void handleList(Object obj, Class entity, boolean isEncode){
        List list = (List)obj;
        if(list.size()>0){
            Object first = list.get(0);
            if(first.getClass().equals(entity)){
                for(int i=0; i<list.size(); i++){
                    Object temp = list.get(i);
                    try {
                        handlerObject(temp, isEncode);
                    } catch (IllegalAccessException e) {
                        log.error("operation failed", e);
                    }
                }
            }
        }
    }


    /**
     * Process data: get decrypted data
     * @param data
     * @return
     */
    public static String getDecodeData(String data){
        String result = null;
        try {
            result = AesEncryptUtil.desEncrypt(data);
        } catch (Exception exception) {
            log.debug("Data decryption error, original data:"+data);
        }
        //Solve the problem that in debug mode, encryption and decryption fail, causing Chinese to be decrypted and become empty.
        if(oConvertUtils.isEmpty(result) && oConvertUtils.isNotEmpty(data)){
            result = data;
        }
        return result;
    }

    /**
     * Process data: get encrypted or formatted data
     * @param data string
     * @param sensitiveEnum type
     * @return processed string
     */
    public static String getEncodeData(String data, SensitiveEnum sensitiveEnum){
        String result;
        switch (sensitiveEnum){
            case ENCODE:
                try {
                    result = AesEncryptUtil.encrypt(data);
                } catch (Exception exception) {
                    log.error("Data encryption error");
                    result = data;
                }
                break;
            case CHINESE_NAME:
                result = chineseName(data);
                break;
            case ID_CARD:
                result = idCardNum(data);
                break;
            case FIXED_PHONE:
                result = fixedPhone(data);
                break;
            case MOBILE_PHONE:
                result = mobilePhone(data);
                break;
            case ADDRESS:
                result = address(data, 3);
                break;
            case EMAIL:
                result = email(data);
                break;
            case BANK_CARD:
                result = bankCard(data);
                break;
            case CNAPS_CODE:
                result = cnapsCode(data);
                break;
            default:
                result = data;
        }
        return result;
    }


    /**
     * [Chinese name] Show only the first character; mask the rest with two asterisks.
     * @param fullName full name
     * @return <example: Li**>
     */
    public static String chineseName(String fullName) {
        if (oConvertUtils.isEmpty(fullName)) {
            return "";
        }
        return formatRight(fullName, 1);
    }

    /**
     * [Chinese name] Show only the first character; mask the rest with two asterisks.
     * @param familyName surname
     * @param firstName given name
     * @return <example: Li**>
     */
    public static String chineseName(String familyName, String firstName) {
        if (oConvertUtils.isEmpty(familyName) || oConvertUtils.isEmpty(firstName)) {
            return "";
        }
        return chineseName(familyName + firstName);
    }

    /**
     * [ID number] Show the last four digits; mask the rest. Total length is 18 or 15 digits.
     * @param id ID number
     * @return <example: *************5762>
     */
    public static String idCardNum(String id) {
        if (oConvertUtils.isEmpty(id)) {
            return "";
        }
        return formatLeft(id, 4);

    }

    /**
     * [Landline] Show the last four digits; mask the rest.
     * @param num landline number
     * @return <example: ****1234>
     */
    public static String fixedPhone(String num) {
        if (oConvertUtils.isEmpty(num)) {
            return "";
        }
        return formatLeft(num, 4);
    }

    /**
     * [Mobile number] Show the first three and last four digits; mask the rest.
     * @param num mobile number
     * @return <example: 138******1234>
     */
    public static String mobilePhone(String num) {
        if (oConvertUtils.isEmpty(num)) {
            return "";
        }
        int len = num.length();
        if(len<11){
            return num;
        }
        return formatBetween(num, 3, 4);
    }

    /**
     * [Address] Show region only, not the detailed address, to enhance personal information protection.
     * @param address address
     * @param sensitiveSize sensitive segment length
     * @return <example: Beijing Haidian ****>
     */
    public static String address(String address, int sensitiveSize) {
        if (oConvertUtils.isEmpty(address)) {
            return "";
        }
        int len = address.length();
        if(len<sensitiveSize){
            return address;
        }
        return formatRight(address, sensitiveSize);
    }

    /**
     * [Email] Show only the first letter of the local part; mask the rest with asterisks; show @ and the domain.
     * @param email email address
     * @return <example: g**@163.com>
     */
    public static String email(String email) {
        if (oConvertUtils.isEmpty(email)) {
            return "";
        }
        int index = email.indexOf("@");
        if (index <= 1){
            return email;
        }
        String begin = email.substring(0, 1);
        String end = email.substring(index);
        String stars = "**";
        return begin + stars + end;
    }

    /**
     * [Bank card number] Show the first six and last four digits; mask the rest with one asterisk per digit.
     * @param cardNum bank card number
     * @return <example: 6222600**********1234>
     */
    public static String bankCard(String cardNum) {
        if (oConvertUtils.isEmpty(cardNum)) {
            return "";
        }
        return formatBetween(cardNum, 6, 4);
    }

    /**
     * [Company bank branch code] Show the first two digits; mask the rest with one asterisk per digit.
     * @param code company bank branch code
     * @return <example: 12********>
     */
    public static String cnapsCode(String code) {
        if (oConvertUtils.isEmpty(code)) {
            return "";
        }
        return formatRight(code, 2);
    }


    /**
     * Mask the right portion with *
     * @param str string
     * @param reservedLength number of characters to keep
     * @return masked string
     */
    public static String formatRight(String str, int reservedLength){
        String name = str.substring(0, reservedLength);
        String stars = String.join("", Collections.nCopies(str.length()-reservedLength, "*"));
        return name + stars;
    }

    /**
     * Mask the left portion with *
     * @param str string
     * @param reservedLength number of characters to keep
     * @return masked string
     */
    public static String formatLeft(String str, int reservedLength){
        int len = str.length();
        String show = str.substring(len-reservedLength);
        String stars = String.join("", Collections.nCopies(len-reservedLength, "*"));
        return stars + show;
    }

    /**
     * Mask the middle portion with *
     * @param str string
     * @param beginLen leading characters to keep
     * @param endLen trailing characters to keep
     * @return masked string
     */
    public static String formatBetween(String str, int beginLen, int endLen){
        int len = str.length();
        String begin = str.substring(0, beginLen);
        String end = str.substring(len-endLen);
        String stars = String.join("", Collections.nCopies(len-beginLen-endLen, "*"));
        return begin + stars + end;
    }

}
