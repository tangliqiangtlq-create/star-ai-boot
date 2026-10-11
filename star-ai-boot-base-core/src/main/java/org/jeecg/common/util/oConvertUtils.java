package org.jeecg.common.util;

import com.alibaba.fastjson.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.springframework.beans.BeanUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.sql.Date;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * @Author Zhang Daihao
 *
 */
@Slf4j
public class oConvertUtils {
	public static boolean isEmpty(Object object) {
		if (object == null) {
			return (true);
		}
		if ("".equals(object)) {
			return (true);
		}
		if (CommonConstant.STRING_NULL.equals(object)) {
			return (true);
		}
		return (false);
	}
	
	public static boolean isNotEmpty(Object object) {
		if (object != null && !"".equals(object) && !object.equals(CommonConstant.STRING_NULL)) {
			return (true);
		}
		return (false);
	}

	
	/**
	 * Return decoded decrypted string
	 * 
	 * @param inStr
	 * @return
	 */
	public static String decodeString(String inStr) {
		if (oConvertUtils.isEmpty(inStr)) {
			return null;
		}

		try {
			inStr = URLDecoder.decode(inStr, "UTF-8");
		} catch (Exception e) {
			// Solution: URLDecoder: Illegal hex characters in escape (%) pattern - For input string: "automatic"
			//log.error("operation failed", e);
		}
		return inStr;
	}

	public static String decode(String strIn, String sourceCode, String targetCode) {
		String temp = code2code(strIn, sourceCode, targetCode);
		return temp;
	}

	@SuppressWarnings("AlibabaLowerCamelCaseVariableNaming")
    public static String StrToUTF(String strIn, String sourceCode, String targetCode) {
		strIn = "";
		try {
			strIn = new String(strIn.getBytes("ISO-8859-1"), "GBK");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			log.error("operation failed", e);
		}
		return strIn;

	}

	private static String code2code(String strIn, String sourceCode, String targetCode) {
		String strOut = null;
		if (strIn == null || "".equals(strIn.trim())) {
			return strIn;
		}
		try {
			byte[] b = strIn.getBytes(sourceCode);
			for (int i = 0; i < b.length; i++) {
				log.debug(String.valueOf(b[i] + "  "));
			}
			strOut = new String(b, targetCode);
		} catch (Exception e) {
			log.error("operation failed", e);
			return null;
		}
		return strOut;
	}

	public static int getInt(String s, int defval) {
		if (s == null || "".equals(s)) {
			return (defval);
		}
		try {
			return (Integer.parseInt(s));
		} catch (NumberFormatException e) {
			return (defval);
		}
	}

	public static int getInt(String s) {
		if (s == null || "".equals(s)) {
			return 0;
		}
		try {
			return (Integer.parseInt(s));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static int getInt(String s, Integer df) {
		if (s == null || "".equals(s)) {
			return df;
		}
		try {
			return (Integer.parseInt(s));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static Integer[] getInts(String[] s) {
		if (s == null) {
			return null;
		}
		Integer[] integer = new Integer[s.length];
		for (int i = 0; i < s.length; i++) {
			integer[i] = Integer.parseInt(s[i]);
		}
		return integer;

	}

	public static double getDouble(String s, double defval) {
		if (s == null || "".equals(s)) {
			return (defval);
		}
		try {
			return (Double.parseDouble(s));
		} catch (NumberFormatException e) {
			return (defval);
		}
	}

	public static double getDou(Double s, double defval) {
		if (s == null) {
			return (defval);
		}
		return s;
	}

	/*public static Short getShort(String s) {
		if (StringUtil.isNotEmpty(s)) {
			return (Short.parseShort(s));
		} else {
			return null;
		}
	}*/

	public static int getInt(Object object, int defval) {
		if (isEmpty(object)) {
			return (defval);
		}
		try {
			return (Integer.parseInt(object.toString()));
		} catch (NumberFormatException e) {
			return (defval);
		}
	}
	
	public static Integer getInteger(Object object, Integer defval) {
		if (isEmpty(object)) {
			return (defval);
		}
		try {
			return (Integer.parseInt(object.toString()));
		} catch (NumberFormatException e) {
			return (defval);
		}
	}
	
	public static Integer getInt(Object object) {
		if (isEmpty(object)) {
			return null;
		}
		try {
			return (Integer.parseInt(object.toString()));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static int getInt(BigDecimal s, int defval) {
		if (s == null) {
			return (defval);
		}
		return s.intValue();
	}

	public static Integer[] getIntegerArry(String[] object) {
		int len = object.length;
		Integer[] result = new Integer[len];
		try {
			for (int i = 0; i < len; i++) {
				result[i] = new Integer(object[i].trim());
			}
			return result;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static String getString(String s) {
		return (getString(s, ""));
	}

	/**
	 * Escape to Unicode encoding
	 * @param s
	 * @return
	 */
	/*public static String escapeJava(Object s) {
		return StringEscapeUtils.escapeJava(getString(s));
	}*/
	
	public static String getString(Object object) {
		if (isEmpty(object)) {
			return "";
		}
		return (object.toString().trim());
	}

	public static String getString(int i) {
		return (String.valueOf(i));
	}

	public static String getString(float i) {
		return (String.valueOf(i));
	}

	/**
	 * Return normalized string (keep digits, letters, and CJK characters only)
	 *
	 * @param input
	 * @return
	 */
	public static String getNormalString(String input) {
		if (oConvertUtils.isEmpty(input)) {
			return null;
		}
		String result = input.replaceAll("[^0-9a-zA-Z\\u4e00-\\u9fa5]", "");
		return result;
	}

	public static String getString(String s, String defval) {
		if (isEmpty(s)) {
			return (defval);
		}
		return (s.trim());
	}

	public static String getString(Object s, String defval) {
		if (isEmpty(s)) {
			return (defval);
		}
		return (s.toString().trim());
	}

	public static long stringToLong(String str) {
		Long test = new Long(0);
		try {
			test = Long.valueOf(str);
		} catch (Exception e) {
		}
		return test.longValue();
	}

	/**
	 * Get local IP
	 */
	public static String getIp() {
		String ip = null;
		try {
			InetAddress address = InetAddress.getLocalHost();
			ip = address.getHostAddress();

		} catch (UnknownHostException e) {
			log.error("operation failed", e);
		}
		return ip;
	}

	/**
	 * Check whether a class is a primitive type.
	 * 
	 * @param clazz
	 *            class to check.
	 * @return true if primitive type.
	 */
	private static boolean isBaseDataType(Class clazz) throws Exception {
		return (clazz.equals(String.class) || clazz.equals(Integer.class) || clazz.equals(Byte.class) || clazz.equals(Long.class) || clazz.equals(Double.class) || clazz.equals(Float.class) || clazz.equals(Character.class) || clazz.equals(Short.class) || clazz.equals(BigDecimal.class) || clazz.equals(BigInteger.class) || clazz.equals(Boolean.class) || clazz.equals(Date.class) || clazz.isPrimitive());
	}

	/**
	 * Decode base64
	 *
	 * @param base64Str base64 string
	 * @return decoded string
	 */
	public static String decodeBase64Str(String base64Str) {
		byte[] byteContent = Base64.decodeBase64(base64Str);
		if (byteContent == null) {
			return null;
		}
		String decodedString = new String(byteContent);
		return decodedString;
	}
	
	
	/**
	 * @param request
	 *            IP
	 * @return IP Address
	 */
	public static String getIpAddrByRequest(HttpServletRequest request) {
		// A1-3: delegate to IpUtils (trusted-proxy hop count); do not take XFF first segment
		return IpUtils.getIpAddr(request);
	}

	/**
	 * @return local IP address
	 * @throws SocketException
	 */
	public static String getRealIp() throws SocketException {
        // Local IP, return it if Public IP is not configured
		String localip = null;
        // Public IP
		String netip = null;

		Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();
		InetAddress ip = null;
        // Whether the Public IP is found
		boolean finded = false;
		while (netInterfaces.hasMoreElements() && !finded) {
			NetworkInterface ni = netInterfaces.nextElement();
			Enumeration<InetAddress> address = ni.getInetAddresses();
			while (address.hasMoreElements()) {
				ip = address.nextElement();
                // Public IP
				if (!ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1) {
					netip = ip.getHostAddress();
					finded = true;
					break;
				} else if (ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1) {
                    // Intranet IP
				    localip = ip.getHostAddress();
				}
			}
		}

		if (netip != null && !"".equals(netip)) {
			return netip;
		} else {
			return localip;
		}
	}

	/**
	 * Remove spaces, carriage returns, line breaks, and tabs from a Java string
	 * 
	 * @param str
	 * @return
	 */
	public static String replaceBlank(String str) {
		String dest = "";
		if (str != null) {
		    String reg = "\\s*|\t|\r|\n";
			Pattern p = Pattern.compile(reg);
			Matcher m = p.matcher(str);
			dest = m.replaceAll("");
		}
		return dest;

	}

	/**
	 * Check whether an element is in the array
	 * 
	 * @param child
	 * @param all
	 * @return
	 */
	public static boolean isIn(String child, String[] all) {
		if (all == null || all.length == 0) {
			return false;
		}
		for (int i = 0; i < all.length; i++) {
			String aSource = all[i];
			if (aSource.equals(child)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check whether an element is in the array
	 *
	 * @param childArray
	 * @param all
	 * @return
	 */
	public static boolean isArrayIn(String[] childArray, String[] all) {
		if (all == null || all.length == 0) {
			return false;
		}
		for (String v : childArray) {
			if (!isIn(v, all)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check whether an element is in the array
	 *
	 * @param childArray
	 * @param all
	 * @return
	 */
	public static boolean isJsonArrayIn(JSONArray childArray, String[] all) {
		if (all == null || all.length == 0) {
			return false;
		}

		String[] childs = childArray.toJavaList(String.class).toArray(new String[0]);
		for (String v : childs) {
			if (!isIn(v, all)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * GetMapObject
	 */
	public static Map<Object, Object> getHashMap() {
		return new HashMap<>(5);
	}

	/**
	 * Convert SET to MAP
	 * 
	 * @param str
	 * @return
	 */
	public static Map<Object, Object> setToMap(Set<Object> setobj) {
		Map<Object, Object> map = getHashMap();
		for (Iterator iterator = setobj.iterator(); iterator.hasNext();) {
			Map.Entry<Object, Object> entry = (Map.Entry<Object, Object>) iterator.next();
			map.put(entry.getKey().toString(), entry.getValue() == null ? "" : entry.getValue().toString().trim());
		}
		return map;

	}

	public static boolean isInnerIp(String ipAddress) {
		boolean isInnerIp = false;
		long ipNum = getIpNum(ipAddress);
		/**
		 * Private IP: Class A 10.0.0.0-10.255.255.255 Class B 172.16.0.0-172.31.255.255 Class C 192.168.0.0-192.168.255.255; 127.x.x.x is loopback
		 **/
		long aBegin = getIpNum("10.0.0.0");
		long aEnd = getIpNum("10.255.255.255");
		long bBegin = getIpNum("172.16.0.0");
		long bEnd = getIpNum("172.31.255.255");
		long cBegin = getIpNum("192.168.0.0");
		long cEnd = getIpNum("192.168.255.255");
		String localIp = "127.0.0.1";
		isInnerIp = isInner(ipNum, aBegin, aEnd) || isInner(ipNum, bBegin, bEnd) || isInner(ipNum, cBegin, cEnd) || localIp.equals(ipAddress);
		return isInnerIp;
	}

	private static long getIpNum(String ipAddress) {
		String[] ip = ipAddress.split("\\.");
		long a = Integer.parseInt(ip[0]);
		long b = Integer.parseInt(ip[1]);
		long c = Integer.parseInt(ip[2]);
		long d = Integer.parseInt(ip[3]);

		long ipNum = a * 256 * 256 * 256 + b * 256 * 256 + c * 256 + d;
		return ipNum;
	}

	private static boolean isInner(long userIp, long begin, long end) {
		return (userIp >= begin) && (userIp <= end);
	}
	
	/**
	 * Convert underscore-uppercase string to camelCase.
	 * Returns empty string if input is empty.</br>
	 * Example: hello_world->helloWorld
	 * 
	 * @param name
	 *            underscore-uppercase string before conversion
	 * @return camelCase string after conversion
	 */
	public static String camelName(String name) {
		StringBuilder result = new StringBuilder();
		// Quick check
		if (name == null || name.isEmpty()) {
			// No conversion needed
			return "";
		} else if (!name.contains(SymbolConstant.UNDERLINE)) {
			// No underscore; only lower-case the first letter
			//update-begin--Author:zhoujf  Date:20180503 for: TASK #2500 [code generator] develop a common template generation feature
			//update-begin--Author:zhoujf  Date:20180503 for: TASK #2500 [code generator] develop a common template generation feature
			return name.substring(0, 1).toLowerCase() + name.substring(1).toLowerCase();
			//update-end--Author:zhoujf  Date:20180503 for: TASK #2500 [code generator] common template generation
		}
		// Split the original string by underscores
		String[] camels = name.split("_");
		for (String camel : camels) {
			// Skip leading/trailing underscores or double underscores in the original string
			if (camel.isEmpty()) {
				continue;
			}
			// Handling real camel fragments
			if (result.length() == 0) {
				// The first camel fragment, all letters in lowercase
				result.append(camel.toLowerCase());
			} else {
				// Other camelCase segments: capitalize the first letter
				result.append(camel.substring(0, 1).toUpperCase());
				result.append(camel.substring(1).toLowerCase());
			}
		}
		return result.toString();
	}
	
	/**
	 * Convert underscore-uppercase string to camelCase.
	 * Returns empty string if input is empty.</br>
	 * Example: hello_world,test_id->helloWorld,testId
	 * 
	 * @param name
	 *            underscore-uppercase string before conversion
	 * @return camelCase string after conversion
	 */
	public static String camelNames(String names) {
		if(names==null||"".equals(names)){
			return null;
		}
		StringBuffer sf = new StringBuffer();
		String[] fs = names.split(",");
		for (String field : fs) {
			field = camelName(field);
			sf.append(field + ",");
		}
		String result = sf.toString();
		return result.substring(0, result.length() - 1);
	}
	
	//update-begin--Author:zhoujf  Date:20180503 for: TASK #2500 [code generator] develop a common template generation feature
	/**
	 * Convert underscore-uppercase string to camelCase (capitalize first letter).
	 * Returns empty string if input is empty.</br>
	 * Example: hello_world->HelloWorld
	 * 
	 * @param name
	 *            underscore-uppercase string before conversion
	 * @return camelCase string after conversion
	 */
	public static String camelNameCapFirst(String name) {
		StringBuilder result = new StringBuilder();
		// Quick check
		if (name == null || name.isEmpty()) {
			// No conversion needed
			return "";
		} else if (!name.contains(SymbolConstant.UNDERLINE)) {
			// No underscore; only lower-case the first letter
			return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
		}
		// Split the original string by underscores
		String[] camels = name.split("_");
		for (String camel : camels) {
			// Skip leading/trailing underscores or double underscores in the original string
			if (camel.isEmpty()) {
				continue;
			}
			// Other camelCase segments: capitalize the first letter
			result.append(camel.substring(0, 1).toUpperCase());
			result.append(camel.substring(1).toLowerCase());
		}
		return result.toString();
	}
	//update-end--Author:zhoujf  Date:20180503 for: TASK #2500 [code generator] common template generation
	
	/**
	 * Convert camelCase to underscore
	 * @param para
	 * @return
	 */
	public static String camelToUnderline(String para){
	    int length = 3;
        if(para.length()<length){
        	return para.toLowerCase(); 
        }
        StringBuilder sb=new StringBuilder(para);
        //position
        int temp=0;
        //Start with the third character to avoid naming irregularities 
        for(int i=2;i<para.length();i++){
            if(Character.isUpperCase(para.charAt(i))){
                sb.insert(i+temp, "_");
                temp+=1;
            }
        }
        return sb.toString().toLowerCase(); 
	}

	/**
	 * Random number
	 * @param place digit length
	 */
	public static String randomGen(int place) {
		String base = "qwertyuioplkjhgfdsazxcvbnmQAZWSXEDCRFVTGBYHNUJMIKLOP0123456789";
		StringBuffer sb = new StringBuffer();
		Random rd = new Random();
		for(int i=0;i<place;i++) {
			sb.append(base.charAt(rd.nextInt(base.length())));
		}
		return sb.toString();
	}
	
	/**
	 * Get all class fields, including superclasses
	 * 
	 * @param object
	 * @return
	 */
	public static Field[] getAllFields(Object object) {
		Class<?> clazz = object.getClass();
		List<Field> fieldList = new ArrayList<>();
		while (clazz != null) {
			fieldList.addAll(new ArrayList<>(Arrays.asList(clazz.getDeclaredFields())));
			clazz = clazz.getSuperclass();
		}
		Field[] fields = new Field[fieldList.size()];
		fieldList.toArray(fields);
		return fields;
	}
	
	/**
	  * Convert all map keys to lowercase
	 * @param list
	 * @return
	 */
	public static List<Map<String, Object>> toLowerCasePageList(List<Map<String, Object>> list){
		List<Map<String, Object>> select = new ArrayList<>();
		for (Map<String, Object> row : list) {
			 Map<String, Object> resultMap = new HashMap<>(5);
			 Set<String> keySet = row.keySet(); 
			 for (String key : keySet) { 
				 String newKey = key.toLowerCase(); 
				 resultMap.put(newKey, row.get(key)); 
			 }
			 select.add(resultMap);
		}
		return select;
	}

	/**
	 * Convert entityList to modelList
	 * @param fromList
	 * @param tClass
	 * @param <F>
	 * @param <T>
	 * @return
	 */
	public static<F,T> List<T> entityListToModelList(List<F> fromList, Class<T> tClass){
		if(fromList == null || fromList.isEmpty()){
			return null;
		}
		List<T> tList = new ArrayList<>();
		for(F f : fromList){
			T t = entityToModel(f, tClass);
			tList.add(t);
		}
		return tList;
	}

	public static<F,T> T entityToModel(F entity, Class<T> modelClass) {
		log.debug("entityToModel : Assign Entity property values to Model");
		Object model = null;
		if (entity == null || modelClass ==null) {
			return null;
		}

		try {
			model = modelClass.newInstance();
		} catch (InstantiationException e) {
			log.error("entityToModel : Instantiation exception", e);
		} catch (IllegalAccessException e) {
			log.error("entityToModel : Security permission exception", e);
		}
		BeanUtils.copyProperties(entity, model);
		return (T)model;
	}

	/**
	 * Check whether list is empty
	 *
	 * @param list
	 * @return true or false
	 * list == null		: true
	 * list.size() == 0	: true
	 */
	public static boolean listIsEmpty(Collection list) {
		return (list == null || list.size() == 0);
	}

	/**
	 * Check whether old and new values are equal
	 *
	 * @param oldVal
	 * @param newVal
	 * @return
	 */
	public static boolean isEqual(Object oldVal, Object newVal) {
		if (oldVal != null && newVal != null) {
			if (isArray(oldVal)) {
				return equalityOfArrays((Object[]) oldVal, (Object[]) newVal);
			}else if(oldVal instanceof JSONArray){
				if(newVal instanceof JSONArray){
					return equalityOfJSONArray((JSONArray) oldVal, (JSONArray) newVal);
				}else{
					if (isEmpty(newVal) && (oldVal == null || ((JSONArray) oldVal).size() == 0)) {
						return true;
					}
					List<Object> arrayStr = Arrays.asList(newVal.toString().split(","));
					JSONArray newValArray = new JSONArray(arrayStr);
					return equalityOfJSONArray((JSONArray) oldVal, newValArray);
				}
			}else{
				return oldVal.equals(newVal);
			}
			
		} else {
			if (oldVal == null && newVal == null) {
				return true;
			} else {
				return false;
			}
		}
	}

	/**
	 * Method Description Determines whether an object is an array
	 *
	 * @param obj
	 * @return
	 * @author yaomy
	 * @date February 5, 2018 5:03:00 pm
	 */
	public static boolean isArray(Object obj) {
		if (obj == null) {
			return false;
		}
		return obj.getClass().isArray();
	}

	/**
	 * Get collection size
	 * 
	 * @param collection
	 * @return
	 */
	public static int getCollectionSize(Collection<?> collection) {
		return collection != null ? collection.size() : 0;
	}
	
	/**
	 * Check whether two arrays are equal (order ignored)
	 *
	 * @param oldVal
	 * @param newVal
	 * @return
	 */
	public static boolean equalityOfJSONArray(JSONArray oldVal, JSONArray newVal) {
		if (oldVal != null && newVal != null) {
			Object[] oldValArray = oldVal.toArray();
			Object[] newValArray = newVal.toArray();
			return equalityOfArrays(oldValArray,newValArray);
		} else {
			if ((oldVal == null || oldVal.size() == 0) && (newVal == null || newVal.size() == 0)) {
				return true;
			} else {
				return false;
			}
		}
	}

	/**
	 * Compare comma-separated strings
	 * QQYUN-5212 [Simple Flow] date trigger: multi-select user component does not trigger when selection order differs; same issue for multi-select department component
	 * @param oldVal
	 * @param newVal
	 * @return
	 */
	public static boolean equalityOfStringArrays(String oldVal, String newVal) {
		if(oldVal.equals(newVal)){
			return true;
		}
		if(oldVal.indexOf(",")>=0 && newVal.indexOf(",")>=0){
			String[] arr1 = oldVal.split(",");
			String[] arr2 = newVal.split(",");
			if(arr1.length == arr2.length){
				boolean flag = true;
				Map<String, Integer> map = new HashMap<>();
				for(String s1: arr1){
					map.put(s1, 1);
				}
				for(String s2: arr2){
					if(map.get(s2) == null){
						flag = false;
						break;
					}
				}
				return flag;
			}
		}
		return false;
	}
	
	/**
	 * Check whether two arrays are equal (order ignored)
	 *
	 * @param oldVal
	 * @param newVal
	 * @return
	 */
	public static boolean equalityOfArrays(Object[] oldVal, Object newVal[]) {
		if (oldVal != null && newVal != null) {
			Arrays.sort(oldVal);
			Arrays.sort(newVal);
			return Arrays.equals(oldVal, newVal);
		} else {
			if ((oldVal == null || oldVal.length == 0) && (newVal == null || newVal.length == 0)) {
				return true;
			} else {
				return false;
			}
		}
	}

//	public static void main(String[] args) {
////		String[] a = new String[]{"1", "2"};
////		String[] b = new String[]{"2", "1"};
//		Integer a = null;
//		Integer b = 1;
//		log.debug(String.valueOf(oConvertUtils.isEqual(a, b)));
//	}
	
	/**
	 * Check whether list is not empty
	 *
	 * @param list
	 * @return true or false
	 * list == null		: false
	 * list.size() == 0	: false
	 */
	public static boolean listIsNotEmpty(Collection list) {
		return !listIsEmpty(list);
	}

	/**
	 * Read static text content
	 * @param url
	 * @return
	 */
	public static String readStatic(String url) {
		String json = "";
		try {
			//Change the way of writing to solve the problem of springboot reading files in jar packages
			InputStream stream = oConvertUtils.class.getClassLoader().getResourceAsStream(url.replace("classpath:", ""));
			json = IOUtils.toString(stream,"UTF-8");
		} catch (IOException e) {
			log.error("operation failed", e);
		}
		return json;
	}

	/**
	 * Convert List to JSONArray
	 * @return
	 */
	public static JSONArray list2JSONArray(List<String> list){
		if(list==null || list.size()==0){
			return null;
		}
		JSONArray array = new JSONArray();
		for(String str: list){
			array.add(str);
		}
		return array;
	}

	/**
	 * Check whether two lists contain exactly the same elements
	 * QQYUN-5326 [LiteFlow] org/user fetch single/multi filter has no department filter
	 * @return
	 */
	public static boolean isEqList(List<String> list1, List<String> list2){
		if(list1.size() != list2.size()){
			return false;
		}
		for(String str1: list1){
			boolean flag = false;
			for(String str2: list2){
				if(str1.equals(str2)){
					flag = true;
					break;
				}
			}
			if(!flag){
				return false;
			}
		}
		return true;
	}


	/**
	 * Check whether any element in list1 appears in list2
	 * QQYUN-5326 [LiteFlow] org/user fetch single/multi filter has no department filter
	 * @param list1
	 * @param list2
	 * @return
	 */
	public static boolean isInList(List<String> list1, List<String> list2){
		for(String str1: list1){
			boolean flag = false;
			for(String str2: list2){
				if(str1.equals(str2)){
					flag = true;
					break;
				}
			}
			if(flag){
				return true;
			}
		}
		return false;
	}

	/**
	 * Convert file size to MB
	 * @param uploadCount
	 * @return
	 */
	public static Double calculateFileSizeToMb(Long uploadCount){
		double count = 0.0;
		if(uploadCount>0) {
			BigDecimal bigDecimal = new BigDecimal(uploadCount);
			//Convert to MB
			BigDecimal divide = bigDecimal.divide(new BigDecimal(1048576));
			count = divide.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
			return count;
		}
		return count;
	}

	/**
	 * map to str
	 *
	 * @param map
	 * @return
	 */
	public static String mapToString(Map<String, String[]> map) {
		if (map == null || map.size() == 0) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String[]> entry : map.entrySet()) {
			String key = entry.getKey();
			String[] values = entry.getValue();
			sb.append(key).append("=");
			sb.append(values != null ? StringUtils.join(values, ",") : "");
			sb.append("&");
		}

		String result = sb.toString();
		if (result.endsWith("&")) {
			result = result.substring(0, sb.length() - 1);
		}
		return result;
	}
	
}
