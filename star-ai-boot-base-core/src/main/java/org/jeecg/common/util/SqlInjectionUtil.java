package org.jeecg.common.util;

import cn.hutool.core.util.ReUtil;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.exception.JeecgSqlInjectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL injection processing tool class
 * 
 * @author zhoujf
 */
public class SqlInjectionUtil {	
	private static final Logger log = LoggerFactory.getLogger(SqlInjectionUtil.class);
	/**
	 * Default SQL injection keywords
	 */
	private final static String XSS_STR = "and |exec |peformance_schema|information_schema|extractvalue|updatexml|geohash|gtid_subset|gtid_subtract|insert |select |delete |update |drop |count |chr |mid |master |truncate |char |declare |;|or |+|--";
	/**
	 * Online report only — SQL injection keywords
	 */
	private static String specialReportXssStr = "exec |peformance_schema|information_schema|extractvalue|updatexml|geohash|gtid_subset|gtid_subtract|insert |alter |delete |grant |update |drop |master |truncate |declare |--";
	/**
	 * Dictionary only — SQL injection keywords
	 */
	private static String specialDictSqlXssStr = "exec |peformance_schema|information_schema|extractvalue|updatexml|geohash|gtid_subset|gtid_subtract|insert |select |delete |update |drop |count |chr |mid |master |truncate |char |declare |;|+|--";
	/**
	 * Exact-match key; leading spaces are ignored
	 */
	private static List<String> FULL_MATCHING_KEYWRODS = new ArrayList<>();
	static {
		FULL_MATCHING_KEYWRODS.add(";");
		FULL_MATCHING_KEYWRODS.add("+");
		FULL_MATCHING_KEYWRODS.add("--");
	}
	
	
	/**
	 * Regex keywords for SQL injection risk
	 *
	 * Function matching requires regex mode
	 */
	private final static String[] XSS_REGULAR_STR_ARRAY = new String[]{
			"chr\\s*\\(",
			"mid\\s*\\(",
			" char\\s*\\(",
			"sleep\\s*\\(",
			"user\\s*\\(",
			"show\\s+tables",
			"user[\\s]*\\([\\s]*\\)",
			"show\\s+databases",
			"sleep\\(\\d*\\)",
			"sleep\\(.*\\)",
	};
	/**
	 * Regex for SQL comments
	 */
	private final static Pattern SQL_ANNOTATION = Pattern.compile("/\\*[\\s\\S]*\\*/");
	private final static  String SQL_ANNOTATION2 = "--";
	
	/**
	 * SQL injection warning message
	 */
	private final static String SQL_INJECTION_KEYWORD_TIP = "Note: SQL injection keyword detected---> {}";
	private final static String SQL_INJECTION_TIP = "Note: value may have SQL injection risk!--->";
	private final static String SQL_INJECTION_TIP_VARIABLE = "Note: value may have SQL injection risk!---> {}";
	

	/**
	 * SQL injection filter; throws on injection keywords
	 * @param values
	 */
	public static void filterContentMulti(String... values) {
		filterContent(values, null);
	}

	/**
	 * Stricter validation
	 * 
	 * SQL injection filter; throws on injection keywords
	 *
	 * @param value
	 * @return
	 */
	public static void filterContent(String value, String customXssString) {
		if (value == null || "".equals(value)) {
			return;
		}
		// 1. Validate SQL comments: SQL comments are not allowed
		checkSqlAnnotation(value);
		// Convert to lowercase for subsequent comparisons
		value = value.toLowerCase().trim();
		
		// 2. SQL injection detection has bypass risk (plain text check)
		//https://gitee.com/jeecg/jeecg-boot/issues/I4NZGE
		String[] xssArr = XSS_STR.split("\\|");
		for (int i = 0; i < xssArr.length; i++) {
			if (value.indexOf(xssArr[i]) > -1) {
				log.error(SqlInjectionUtil.SQL_INJECTION_KEYWORD_TIP, xssArr[i]);
				log.error(SqlInjectionUtil.SQL_INJECTION_TIP_VARIABLE, value);
				throw new JeecgSqlInjectionException(SqlInjectionUtil.SQL_INJECTION_TIP + value);
			}
		}
		// 3. There is a risk of bypassing SQL injection detection (customized normal text check)
		if (customXssString != null) {
			String[] xssArr2 = customXssString.split("\\|");
			for (int i = 0; i < xssArr2.length; i++) {
				if (value.indexOf(xssArr2[i]) > -1) {
					log.error(SqlInjectionUtil.SQL_INJECTION_KEYWORD_TIP, xssArr2[i]);
					log.error(SqlInjectionUtil.SQL_INJECTION_TIP_VARIABLE, value);
					throw new JeecgSqlInjectionException(SqlInjectionUtil.SQL_INJECTION_TIP + value);
				}
			}
		}

		// 4. There is a risk of bypassing SQL injection detection (regular check)
		for (String regularOriginal : XSS_REGULAR_STR_ARRAY) {
			String regular = ".*" + regularOriginal + ".*";
			if (Pattern.matches(regular, value)) {
				log.error(SqlInjectionUtil.SQL_INJECTION_KEYWORD_TIP, regularOriginal);
				log.error(SqlInjectionUtil.SQL_INJECTION_TIP_VARIABLE, value);
				throw new JeecgSqlInjectionException(SqlInjectionUtil.SQL_INJECTION_TIP + value);
			}
		}
		return;
	}

	/**
	 * Check whether a string contains SQL injection keywords
	 *
	 * @param keyword
	 * @return
	 */
	@SuppressWarnings("AlibabaUndefineMagicConstant")
	private static boolean isExistSqlInjectKeyword(String sql, String keyword) {
		if (sql.startsWith(keyword.trim())) {
			return true;
		} else if (sql.contains(keyword)) {
			// Need to match, sql injection keywords
			String matchingText = " " + keyword;
			if(FULL_MATCHING_KEYWRODS.contains(keyword)){
				matchingText = keyword;
			}
			
			if (sql.contains(matchingText)) {
				return true;
			} else {
				String regularStr = "\\s+\\S+" + keyword;
				List<String> resultFindAll = ReUtil.findAll(regularStr, sql, 0, new ArrayList<String>());
				for (String res : resultFindAll) {
					log.info("isExistSqlInjectKeyword — matched SQL injection keyword: {}", res);
					/**
					 * Characters that can replace spaces in SQL injection (%09  %0A  %0D  + can all substitute for space)
					 * http://blog.chinaunix.net/uid-12501104-id-2932639.html
					 * https://www.cnblogs.com/Vinson404/p/7253255.html
					 * */
					if (res.contains("%") || res.contains("+") || res.contains("#") || res.contains("/") || res.contains(")")) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * SQL injection filter; throws on injection keywords
	 * 
	 * @param values
	 * @return
	 */
	public static void filterContent(String[] values, String customXssString) {
		for (String val : values) {
			if (oConvertUtils.isEmpty(val)) {
				return;
			}
			filterContent(val, customXssString);
		}
		return;
	}

	/**
	 * [Note: not generic]
	 * Dictionary condition SQL params only; injection filter
	 *
	 * @param value
	 * @return
	 */
	public static void specialFilterContentForDictSql(String value) {
		String[] xssArr = specialDictSqlXssStr.split("\\|");
		if (value == null || "".equals(value)) {
			return;
		}
		// 1. Validate SQL comments: SQL comments are not allowed
		checkSqlAnnotation(value);
		value = value.toLowerCase().trim();
		
		// 2. SQL injection detection has bypass risk (plain text check)
		for (int i = 0; i < xssArr.length; i++) {
			if (isExistSqlInjectKeyword(value, xssArr[i])) {
				log.error(SqlInjectionUtil.SQL_INJECTION_KEYWORD_TIP, xssArr[i]);
				log.error(SqlInjectionUtil.SQL_INJECTION_TIP_VARIABLE, value);
				throw new JeecgSqlInjectionException(SqlInjectionUtil.SQL_INJECTION_TIP + value);
			}
		}

		// 3. SQL injection checks may be bypassed (regex validation)
		for (String regularOriginal : XSS_REGULAR_STR_ARRAY) {
			String regular = ".*" + regularOriginal + ".*";
			if (Pattern.matches(regular, value)) {
				log.error(SqlInjectionUtil.SQL_INJECTION_KEYWORD_TIP, regularOriginal);
				log.error(SqlInjectionUtil.SQL_INJECTION_TIP_VARIABLE, value);
				throw new JeecgSqlInjectionException(SqlInjectionUtil.SQL_INJECTION_TIP + value);
			}
		}
		return;
	}

    /**
	 * [Note: not generic]
     *  Online report SQL parsing only; injection filter
     * @param value
     * @return
     */
	public static void specialFilterContentForOnlineReport(String value) {
		String[] xssArr = specialReportXssStr.split("\\|");
		if (value == null || "".equals(value)) {
			return;
		}
		// 1. Validate SQL comments: SQL comments are not allowed
		checkSqlAnnotation(value);
		value = value.toLowerCase().trim();
		
		// 2. SQL injection detection has bypass risk (plain text check)
		for (int i = 0; i < xssArr.length; i++) {
			if (isExistSqlInjectKeyword(value, xssArr[i])) {
				log.error(SqlInjectionUtil.SQL_INJECTION_KEYWORD_TIP, xssArr[i]);
				log.error(SqlInjectionUtil.SQL_INJECTION_TIP_VARIABLE, value);
				throw new JeecgSqlInjectionException(SqlInjectionUtil.SQL_INJECTION_TIP + value);
			}
		}

		// 3. SQL injection checks may be bypassed (regex validation)
		for (String regularOriginal : XSS_REGULAR_STR_ARRAY) {
			String regular = ".*" + regularOriginal + ".*";
			if (Pattern.matches(regular, value)) {
				log.error(SqlInjectionUtil.SQL_INJECTION_KEYWORD_TIP, regularOriginal);
				log.error(SqlInjectionUtil.SQL_INJECTION_TIP_VARIABLE, value);
				throw new JeecgSqlInjectionException(SqlInjectionUtil.SQL_INJECTION_TIP + value);
			}
		}
		return;
	}


	/**
	 * Check for SQL comments 
	 * @return
	 */
	public static void checkSqlAnnotation(String str){
		if(str.contains(SQL_ANNOTATION2)){
			String error = "Note: SQL must not contain comments, security risk!";
			log.error(error);
			throw new RuntimeException(error);
		}

		
		Matcher matcher = SQL_ANNOTATION.matcher(str);
		if(matcher.find()){
			String error = "Note: value may have SQL injection risk---> \\*.*\\";
			log.error(error);
			throw new JeecgSqlInjectionException(error);
		}
	}


	/**
	 * Return query table name
	 * <p>
	 * SQL injection filter; throws on injection keywords
	 *
	 * @param table
	 */
	private static Pattern tableNamePattern = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_\\$]{0,63}$");
	public static String getSqlInjectTableName(String table) {
		if(oConvertUtils.isEmpty(table)){
			return table;
		}

		//update-begin---author:scott ---date:2024-05-28 for: Form designer list translation exists in Table name with Condition, causing translation problems----
		int index = table.toLowerCase().indexOf(" where ");
		if (index != -1) {
			table = table.substring(0, index);
			log.info("New table name after truncating where clause: " + table);
		}
		//update-end---author:scott ---date::2024-05-28 for: Form designer list translation exists in Table name with Condition, causing translation problems----

		table = table.trim();
		/**
		 * Validate whether a table name is legal
		 *
		 * Table names may only contain letters, digits, and underscores.
		 * Table names must start with a letter.
		 * Table name length is usually limited, e.g. at most 64 characters.
		 */
		boolean isValidTableName = tableNamePattern.matcher(table).matches();
		if (!isValidTableName) {
			String errorMsg = "Invalid table name, SQL injection risk exists!--->" + table;
			log.error(errorMsg);
			throw new JeecgSqlInjectionException(errorMsg);
		}

		//Further check for SQL injection risk
		filterContentMulti(table);
		return table;
	}


	/**
	 * Return query field
	 * <p>
	 * SQL injection filter; throws on injection keywords
	 *
	 * @param field
	 */
	static final Pattern fieldPattern = Pattern.compile("^[a-zA-Z0-9_]+$");
	public static String getSqlInjectField(String field) {
		if(oConvertUtils.isEmpty(field)){
			return field;
		}
		
		field = field.trim();

		if (field.contains(SymbolConstant.COMMA)) {
			return getSqlInjectField(field.split(SymbolConstant.COMMA));
		}

		/**
		 * Validate whether a table field is valid
		 *
		 * Field names may only contain letters, digits, and underscores (no spaces, escape sequences, etc.)
		 */
		boolean isValidField = fieldPattern.matcher(field).matches();
		if (!isValidField) {
			String errorMsg = "Invalid field, SQL injection risk! --->" + field;
			log.error(errorMsg);
			throw new JeecgSqlInjectionException(errorMsg);
		}

		//Further check for SQL injection risk
		filterContentMulti(field);
		return field;
	}

	/**
	 * Get multiple fields
	 * Returns: comma-separated
	 *
	 * @param fields
	 * @return
	 */
	public static String getSqlInjectField(String... fields) {
		for (String s : fields) {
			getSqlInjectField(s);
		}
		return String.join(SymbolConstant.COMMA, fields);
	}


	/**
	 * Get sort field
	 * Returns: string
	 *
	 * 1. Convert camelCase to snake_case
	 * 2. Prevent SQL injection
	 * @param sortField sort field
	 * @return
	 */
	public static String getSqlInjectSortField(String sortField) {
		String field = SqlInjectionUtil.getSqlInjectField(oConvertUtils.camelToUnderline(sortField));
		return field;
	}

	/**
	 * Get multiple sort fields
	 * Returns: array
	 *
	 * 1. Convert camelCase to snake_case
	 * 2. Prevent SQL injection
	 * @param sortFields multiple sort fields
	 * @return
	 */
	public static List getSqlInjectSortFields(String... sortFields) {
		List list = new ArrayList<String>();
		for (String sortField : sortFields) {
			list.add(getSqlInjectSortField(sortField));
		}
		return list;
	}

	/**
	 * Get orderBy type
	 * Returns: string
	 * <p>
	 * 1. Validate asc or desc
	 * 2. Prevent SQL injection
	 *
	 * @param orderType
	 * @return
	 */
	public static String getSqlInjectOrderType(String orderType) {
		if (orderType == null) {
			return null;
		}
		orderType = orderType.trim();
		if (CommonConstant.ORDER_TYPE_ASC.equalsIgnoreCase(orderType)) {
			return CommonConstant.ORDER_TYPE_ASC;
		} else {
			return CommonConstant.ORDER_TYPE_DESC;
		}
	}

}
