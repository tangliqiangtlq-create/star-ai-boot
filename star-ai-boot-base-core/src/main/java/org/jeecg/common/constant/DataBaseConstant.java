package org.jeecg.common.constant;
/**
 * Database context constants
 * @author: jeecg-boot
 */
public interface DataBaseConstant {
	//*********DatabaseType****************************************

    /**MYSQLDatabase*/
	public static final String DB_TYPE_MYSQL = "MYSQL";

    /** ORACLE*/
	public static final String DB_TYPE_ORACLE = "ORACLE";

    /**Dameng database*/
	public static final String DB_TYPE_DM = "DM";

    /**postgreSQLDameng database*/
	public static final String DB_TYPE_POSTGRESQL = "POSTGRESQL";
	
    /**Renmin University of Finance and Economics database*/
	public static final String DB_TYPE_KINGBASEES = "KINGBASEES";

    /**sqlserverDatabase*/
	public static final String DB_TYPE_SQLSERVER = "SQLSERVER";

    /**mariadb Database*/
	public static final String DB_TYPE_MARIADB = "MARIADB";

    /**DB2 Database*/
	public static final String DB_TYPE_DB2 = "DB2";

    /**HSQL Database*/
	public static final String DB_TYPE_HSQL = "HSQL";

//	// Database type，correspond database_type dictionary
//	public static final String DB_TYPE_MYSQL_NUM = "1";
//	public static final String DB_TYPE_MYSQL7_NUM = "6";
//	public static final String DB_TYPE_ORACLE_NUM = "2";
//	public static final String DB_TYPE_SQLSERVER_NUM = "3";
//	public static final String DB_TYPE_POSTGRESQL_NUM = "4";
//	public static final String DB_TYPE_MARIADB_NUM = "5";

	//********* System context variables ****************************************
	/**
	 * Data — owning organization code
	 */
	public static final String SYS_ORG_CODE = "sysOrgCode";
	/**
	 * Data — owning organization code
	 */
	public static final String SYS_ORG_CODE_TABLE = "sys_org_code";
	/**
	 * Data — owning organization code
	 */
	public static final String SYS_MULTI_ORG_CODE = "sysMultiOrgCode";
	/**
	 * Data — owning organization code
	 */
	public static final String SYS_MULTI_ORG_CODE_TABLE = "sys_multi_org_code";
	/**
	 * Data — owning organization ID
	 */
	public static final String SYS_ORG_ID = "sysOrgId";
	/**
	 * Data — owning organization ID
	 */
	public static final String SYS_ORG_ID_TABLE = "sys_org_id";
	/**
	 * Data — owning role codes (comma-separated)
	 */
	public static final String SYS_ROLE_CODE = "sysRoleCode";
	/**
	 * Data — owning role codes (comma-separated)
	 */
	public static final String SYS_ROLE_CODE_TABLE = "sys_role_code";
	/**
	 * Data — system user code (login account)
	 */
	public static final String SYS_USER_CODE = "sysUserCode";
	/**
	 * Data — system user code (login account)
	 */
	public static final String SYS_USER_CODE_TABLE = "sys_user_code";
	/**
	 * Logged-in user ID
	 */
	public static final String SYS_USER_ID = "sysUserId";
	/**
	 * Logged-in user ID
	 */
	public static final String SYS_USER_ID_TABLE = "sys_user_id";
	/**
	 * Logged-in user real name
	 */
	public static final String SYS_USER_NAME = "sysUserName";
	/**
	 * Logged-in user real name
	 */
	public static final String SYS_USER_NAME_TABLE = "sys_user_name";
	/**
	 * System date "yyyy-MM-dd"
	 */
	public static final String SYS_DATE = "sysDate";
	/**
	 * System date "yyyy-MM-dd"
	 */
	public static final String SYS_DATE_TABLE = "sys_date";
	/**
	 * System time "yyyy-MM-dd HH:mm"
	 */
	public static final String SYS_TIME = "sysTime";
	/**
	 * System time "yyyy-MM-dd HH:mm"
	 */
	public static final String SYS_TIME_TABLE = "sys_time";
	/**
	 * Data — owning organization code
	 */
	public static final String SYS_BASE_PATH = "sys_base_path";
	//********* System context variables ****************************************
	
	
	//********* Standard system table fields ****************************************
	/**
	 * Creator login name
	 */
	public static final String CREATE_BY_TABLE = "create_by";
	/**
	 * Creator login name
	 */
	public static final String CREATE_BY = "createBy";
	/**
	 * Creation datetime
	 */
	public static final String CREATE_TIME_TABLE = "create_time";
	/**
	 * Creation datetime
	 */
	public static final String CREATE_TIME = "createTime";
	/**
	 * Update user login name
	 */
	public static final String UPDATE_BY_TABLE = "update_by";
	/**
	 * Update user login name
	 */
	public static final String UPDATE_BY = "updateBy";
	/**
	 * Update datetime
	 */
	public static final String UPDATE_TIME = "updateTime";
	/**
	 * Update datetime
	 */
	public static final String UPDATE_TIME_TABLE = "update_time";
	
	/**
	 * Business process status
	 */
	public static final String BPM_STATUS = "bpmStatus";
	/**
	 * Business process status
	 */
	public static final String BPM_STATUS_TABLE = "bpm_status";
	//********* Standard system table fields ****************************************

    /**
     * SQL where clause.
     */
    String SQL_WHERE = "where";

    /**
     * SQL asc clause.
     */
    String SQL_ASC = "asc";

    /**
     * SQL Server database name with embedded spaces.
     */
    String DB_TYPE_SQL_SERVER_BLANK = "sql server";
}
