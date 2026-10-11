package org.jeecg.common.util.dynamic.db;

import com.baomidou.mybatisplus.annotation.DbType;
import org.jeecg.common.constant.DataBaseConstant;

import java.util.HashMap;
import java.util.Map;

/**
 * Database type judgment
 * [Some database engines are the same to achieve reuse purposes]
 * @author: jeecg-boot
 */
public class DbTypeUtils {

    public static Map<String, String> dialectMap = new HashMap<String, String>();
    static{
        dialectMap.put("mysql", "org.hibernate.dialect.MySQL5InnoDBDialect");
        // mariadbDatabase 1  --
        dialectMap.put("mariadb", "org.hibernate.dialect.MariaDBDialect");
        //oracleDatabase 1
        dialectMap.put("oracle", "org.hibernate.dialect.OracleDialect");
        // TODO not found not sure
        dialectMap.put("oracle12c", "org.hibernate.dialect.OracleDialect");
        // db2Database 1xx
        dialectMap.put("db2", "org.hibernate.dialect.DB2390Dialect");
        // H2Database
        dialectMap.put("h2", "org.hibernate.dialect.HSQLDialect");
        // HSQLDatabase  1
        dialectMap.put("hsql", "org.hibernate.dialect.HSQLDialect");
        //SQLite database application platform mobile
        dialectMap.put("sqlite", "org.jeecg.modules.online.config.dialect.SQLiteDialect");
        //PostgreSQLDatabase1  --
        dialectMap.put("postgresql", "org.hibernate.dialect.PostgreSQLDialect");
        dialectMap.put("sqlserver2005", "org.hibernate.dialect.SQLServer2005Dialect");
        //sqlserverDatabase1
        dialectMap.put("sqlserver", "org.hibernate.dialect.SQLServerDialect");
        //Dameng database [domestic] 1--
        dialectMap.put("dm", "org.hibernate.dialect.DmDialect");
        //Virtual Valley Database
        dialectMap.put("xugu", "org.hibernate.dialect.HSQLDialect");
        //Renmin University of Finance and Economics [Domestic] 1
        dialectMap.put("kingbasees", "org.hibernate.dialect.PostgreSQLDialect");
        // Phoenix HBaseDatabase
        dialectMap.put("phoenix", "org.hibernate.dialect.HSQLDialect");
        // Gauss Database
        dialectMap.put("zenith", "org.hibernate.dialect.PostgreSQLDialect");
        //Alibaba Cloud PolarDB
        dialectMap.put("clickhouse", "org.hibernate.dialect.MySQLDialect");
        // NTU General Database TODO Not Found Uncertain
        dialectMap.put("gbase", "org.hibernate.dialect.PostgreSQLDialect");
        //Shentong database [domestic] TODO not found, not sure
        dialectMap.put("oscar", "org.hibernate.dialect.PostgreSQLDialect");
        //Sybase ASE Database
        dialectMap.put("sybase", "org.hibernate.dialect.SybaseDialect");
        dialectMap.put("oceanbase", "org.hibernate.dialect.PostgreSQLDialect");
        dialectMap.put("Firebird", "org.hibernate.dialect.FirebirdDialect");
        //Hangao database
        dialectMap.put("highgo", "org.hibernate.dialect.HSQLDialect");
        dialectMap.put("other", "org.hibernate.dialect.PostgreSQLDialect");
    }

    public static boolean dbTypeIsMySql(DbType dbType) {
        return dbTypeIf(dbType, DbType.MYSQL, DbType.MARIADB, DbType.CLICK_HOUSE, DbType.SQLITE);
    }

    public static boolean dbTypeIsOracle(DbType dbType) {
        return dbTypeIf(dbType, DbType.ORACLE, DbType.ORACLE_12C, DbType.DM);
    }

    public static boolean dbTypeIsSqlServer(DbType dbType) {
        return dbTypeIf(dbType, DbType.SQL_SERVER, DbType.SQL_SERVER2005);
    }

    public static boolean dbTypeIsPostgre(DbType dbType) {
        return dbTypeIf(dbType, DbType.POSTGRE_SQL, DbType.KINGBASE_ES, DbType.GAUSS);
    }



    /**
     *  Get database type string from enum
     * @param dbType
     * @return
     */
    public static String getDbTypeString(DbType dbType){
        if(DbType.DB2.equals(dbType)){
            return DataBaseConstant.DB_TYPE_DB2;
        }else if(DbType.HSQL.equals(dbType)){
            return DataBaseConstant.DB_TYPE_HSQL;
        }else if(dbTypeIsOracle(dbType)){
            return DataBaseConstant.DB_TYPE_ORACLE;
        }else if(dbTypeIsSqlServer(dbType)){
            return DataBaseConstant.DB_TYPE_SQLSERVER;
        }else if(dbTypeIsPostgre(dbType)){
            return DataBaseConstant.DB_TYPE_POSTGRESQL;
        }
        return DataBaseConstant.DB_TYPE_MYSQL;
    }

    /**
     *  Get database dialect string from enum
     * @param dbType
     * @return
     */
    public static String getDbDialect(DbType dbType){
        return dialectMap.get(dbType.getDb());
    }

    /**
     * Determine database type.
     */
    public static boolean dbTypeIf(DbType dbType, DbType... correctTypes) {
        for (DbType type : correctTypes) {
            if (type.equals(dbType)) {
                return true;
            }
        }
        return false;
    }

}
