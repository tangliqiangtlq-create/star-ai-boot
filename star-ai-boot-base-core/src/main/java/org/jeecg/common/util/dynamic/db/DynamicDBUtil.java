package org.jeecg.common.util.dynamic.db;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.DynamicDataSourceModel;
import org.jeecg.common.util.ReflectHelper;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring JDBC real-time database access
 *
 * @author chenguobin
 * @version 1.0
 * @date 2014-09-05
 */
@Slf4j
public class DynamicDBUtil {

    /**
     * Get data source [lowest-level method; do not call casually]
     *
     * @param dbSource
     * @return
     */
    private static DruidDataSource getJdbcDataSource(final DynamicDataSourceModel dbSource) {
        DruidDataSource dataSource = new DruidDataSource();

        String driverClassName = dbSource.getDbDriver();
        String url = dbSource.getDbUrl();
        // Configuring the url to "123" will trigger an infinite loop in Druid and keep trying to connect repeatedly.
        if (oConvertUtils.isEmpty(url) || !url.toLowerCase().startsWith("jdbc:")) {
            throw new JeecgBootException("Data source URL configuration format is incorrect!");
        }
        
        String dbUser = dbSource.getDbUsername();
        String dbPassword = dbSource.getDbPassword();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        //dataSource.setValidationQuery("SELECT 1 FROM DUAL");
        dataSource.setTestWhileIdle(true);
        dataSource.setTestOnBorrow(false);
        dataSource.setTestOnReturn(false);
        dataSource.setBreakAfterAcquireFailure(true);
        //Set timeout to 60 seconds
        dataSource.setLoginTimeout(60);
        dataSource.setConnectionErrorRetryAttempts(0);
        dataSource.setUsername(dbUser);
        dataSource.setMaxWait(30000);
        dataSource.setPassword(dbPassword);

        log.info("******************************************");
        log.info("*                                        *");
        log.info("*====【"+dbSource.getCode()+"===== Druid connection pool enabled ====*");
        log.info("*                                        *");
        log.info("******************************************");
        return dataSource;
    }

    /**
     * Get the data source through dbKey
     *
     * @param dbKey
     * @return
     */
    public static DruidDataSource getDbSourceByDbKey(final String dbKey) {
        //Get multiple data source configuration
        DynamicDataSourceModel dbSource = DataSourceCachePool.getCacheDynamicDataSourceModel(dbKey);
        //First determine whether there is a database link in the cache
        DruidDataSource cacheDbSource = DataSourceCachePool.getCacheBasicDataSource(dbKey);
        if (cacheDbSource != null && !cacheDbSource.isClosed()) {
            log.debug("--------getDbSourceBydbKey------------------Fetch DB connection from cache-------------------");
            return cacheDbSource;
        } else {
            DruidDataSource dataSource = getJdbcDataSource(dbSource);
            if(dataSource!=null && dataSource.isEnable()){
                DataSourceCachePool.putCacheBasicDataSource(dbKey, dataSource);
            }else{
                throw new JeecgBootException("Dynamic data source connection failed, dbKey: "+dbKey);
            }
            log.info("--------getDbSourceBydbKey------------------Create DB connection-------------------");
            return dataSource;
        }
    }

    /**
     * Close database connection pool.
     *
     * @param dbKey
     * @return
     */
    public static void closeDbKey(final String dbKey) {
        DruidDataSource dataSource = getDbSourceByDbKey(dbKey);
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.getConnection().commit();
                dataSource.getConnection().close();
                dataSource.close();
            }
        } catch (SQLException e) {
            log.error("operation failed", e);
        }
    }


    private static JdbcTemplate getJdbcTemplate(String dbKey) {
        DruidDataSource dataSource = getDbSourceByDbKey(dbKey);
        return new JdbcTemplate(dataSource);
    }

    /**
     * Get NamedParameterJdbcTemplate by data source
     * @param dbKey
     * @return
     */
    private static NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(String dbKey) {
        DruidDataSource dataSource = getDbSourceByDbKey(dbKey);
        return new NamedParameterJdbcTemplate(dataSource);
    }
 
    public static int update(final String dbKey, String sql, Map<String, Object> params) {
        NamedParameterJdbcTemplate named = getNamedParameterJdbcTemplate(dbKey);
        return named.update(sql, params == null ? java.util.Collections.emptyMap() : params);
    }

    /**
     * Executes the SQL statement in this <code>PreparedStatement</code> object,
     * which must be an SQL Data Manipulation Language (DML) statement, such as <code>INSERT</code>, <code>UPDATE</code> or
     * <code>DELETE</code>; or an SQL statement that returns nothing,
     * such as a DDL statement.
     */
    public static int update(final String dbKey, String sql, Object... param) {
        int effectCount;
        JdbcTemplate jdbcTemplate = getJdbcTemplate(dbKey);
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());
        if (ArrayUtils.isEmpty(param)) {
            effectCount = namedParameterJdbcTemplate.update(sql, java.util.Collections.emptyMap());
        } else {
            throw new IllegalArgumentException("Please use named parameter overload update(dbKey, sql, Map<String,Object>)");
        }
        return effectCount;
    }

    /**
     * Update supporting miniDao syntax
     *
     * @param dbKey data source key
     * @param sql   SQL statement; supports miniDao syntax
     * @param data  data for SQL condition evaluation and injection-safe concatenation
     * @return
     */
    public static int updateByHash(final String dbKey, String sql, HashMap<String, Object> data) {
        int effectCount;
        JdbcTemplate jdbcTemplate = getJdbcTemplate(dbKey);
        //Get SQL from template
        sql = FreemarkerParseFactory.parseTemplateContent(sql, data);
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());
        effectCount = namedParameterJdbcTemplate.update(sql, data);
        return effectCount;
    }

    public static Object findOne(final String dbKey, String sql, Object... param) {
        List<Map<String, Object>> list;
        list = findList(dbKey, sql, param);
        if (oConvertUtils.listIsEmpty(list)) {
            log.error("Except one, but not find actually");
            return null;
        }
        if (list.size() > 1) {
            log.error("Except one, but more than one actually");
        }
        return list.get(0);
    }

    /**
     * Query supporting miniDao syntax; returns HashMap
     *
     * @param dbKey data source key
     * @param sql   SQL statement; supports miniDao syntax
     * @param data  data for SQL condition evaluation and injection-safe concatenation
     * @return
     */
    public static Object findOneByHash(final String dbKey, String sql, HashMap<String, Object> data) {
        List<Map<String, Object>> list;
        list = findListByHash(dbKey, sql, data);
        if (oConvertUtils.listIsEmpty(list)) {
            log.error("Except one, but not find actually");
        }
        if (list.size() > 1) {
            log.error("Except one, but more than one actually");
        }
        return list.get(0);
    }

    /**
     * Direct SQL query; return single instance by clazz
     *
     * @param dbKey data source key
     * @param sql   SQL to execute
     * @param clazz Class of returned instance
     * @param param
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> Object findOne(final String dbKey, String sql, Class<T> clazz, Object... param) {
        Map<String, Object> map = (Map<String, Object>) findOne(dbKey, sql, param);
        return ReflectHelper.setAll(clazz, map);
    }

    /**
     * Query supporting miniDao syntax; returns a single instance
     *
     * @param dbKey data source key
     * @param sql   SQL statement; supports miniDao syntax
     * @param clazz return instance Class
     * @param data  data for SQL condition evaluation and injection-safe concatenation
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> Object findOneByHash(final String dbKey, String sql, Class<T> clazz, HashMap<String, Object> data) {
        Map<String, Object> map = (Map<String, Object>) findOneByHash(dbKey, sql, data);
        return ReflectHelper.setAll(clazz, map);
    }

    public static List<Map<String, Object>> findList(final String dbKey, String sql, Object... param) {
        List<Map<String, Object>> list;
        JdbcTemplate jdbcTemplate = getJdbcTemplate(dbKey);
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());

        if (ArrayUtils.isEmpty(param)) {
            list = namedParameterJdbcTemplate.queryForList(sql, java.util.Collections.emptyMap());
        } else {
            throw new IllegalArgumentException("Please use named parameter overload findListByNamedParam(dbKey, sql, Map<String,Object>)");
        }
        return list;
    }

    /**
     * Query count
     * @param dbKey
     * @param sql
     * @param param
     * @return
     */
    public static Map<String, Object> queryCount(String dbKey, String sql, Map<String, Object> param){
        NamedParameterJdbcTemplate npJdbcTemplate = getNamedParameterJdbcTemplate(dbKey);
        return npJdbcTemplate.queryForMap(sql, param);
    }

    /**
     * QueryListData
     * @param dbKey
     * @param sql
     * @param param
     * @return
     */
    public static List<Map<String, Object>> findListByNamedParam(final String dbKey, String sql, Map<String, Object> param) {
        NamedParameterJdbcTemplate npJdbcTemplate = getNamedParameterJdbcTemplate(dbKey);
        List<Map<String, Object>> list = npJdbcTemplate.queryForList(sql, param);
        return list;
    }
 
    public static <T> List<T> findListByNamedParam(final String dbKey, String sql, Class<T> clazz, Map<String, Object> param) {
        NamedParameterJdbcTemplate npJdbcTemplate = getNamedParameterJdbcTemplate(dbKey);
        return npJdbcTemplate.query(sql, param, new org.springframework.jdbc.core.SingleColumnRowMapper<>(clazz));
    }

    /**
     * Query supporting miniDao syntax
     *
     * @param dbKey data source key
     * @param sql   SQL statement; supports miniDao syntax
     * @param data  data for SQL condition evaluation and injection-safe concatenation
     * @return
     */
    public static List<Map<String, Object>> findListByHash(final String dbKey, String sql, HashMap<String, Object> data) {
        List<Map<String, Object>> list;
        JdbcTemplate jdbcTemplate = getJdbcTemplate(dbKey);
        //Get SQL from template
        sql = FreemarkerParseFactory.parseTemplateContent(sql, data);
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());
        list = namedParameterJdbcTemplate.queryForList(sql, data);
        return list;
    }

    /**
     * This method can only return a single column, not an entity class
     * @param dbKey data source key
     * @param sql sal
     * @param clazz class
     * @param param parameters
     * @param <T>
     * @return
     */
    public static <T> List<T> findList(final String dbKey, String sql, Class<T> clazz, Object... param) {
        List<T> list;
        JdbcTemplate jdbcTemplate = getJdbcTemplate(dbKey);
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());

        if (ArrayUtils.isEmpty(param)) {
            list = namedParameterJdbcTemplate.queryForList(sql, java.util.Collections.emptyMap(), clazz);
        } else {
            throw new IllegalArgumentException("Please use named parameter overload findListByNamedParam(dbKey, sql, Class<T>, Map<String,Object>)");
        }
        return list;
    }

    /**
     * Query supporting miniDao syntax; returns a single-column list
     *
     * @param dbKey data source key
     * @param sql   SQL statement; supports miniDao syntax
     * @param clazz type such as Long, String, etc.
     * @param data  data for SQL condition evaluation and injection-safe concatenation
     * @return
     */
    public static <T> List<T> findListByHash(final String dbKey, String sql, Class<T> clazz, HashMap<String, Object> data) {
        List<T> list;
        JdbcTemplate jdbcTemplate = getJdbcTemplate(dbKey);
        //Get SQL from template
        sql = FreemarkerParseFactory.parseTemplateContent(sql, data);
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());
        list = namedParameterJdbcTemplate.queryForList(sql, data, clazz);
        return list;
    }

    /**
     * Direct SQL query; return entity list
     *
     * @param dbKey data source key
     * @param sql   SQL to execute; supports minidao syntax
     * @param clazz class of returned entity list
     * @param param data needed for SQL parameter injection
     * @return
     */
    public static <T> List<T> findListEntities(final String dbKey, String sql, Class<T> clazz, Object... param) {
        List<Map<String, Object>> queryList = findList(dbKey, sql, param);
        return ReflectHelper.transList2Entrys(queryList, clazz);
    }

    /**
     * Query supporting miniDao syntax; returns a list of entity objects
     *
     * @param dbKey data source identifier
     * @param sql   SQL to execute; supports minidao syntax
     * @param clazz class of the returned entity list
     * @param data  data for SQL condition checks and injection in SQL concatenation
     * @return
     */
    public static <T> List<T> findListEntitiesByHash(final String dbKey, String sql, Class<T> clazz, HashMap<String, Object> data) {
        List<Map<String, Object>> queryList = findListByHash(dbKey, sql, data);
        return ReflectHelper.transList2Entrys(queryList, clazz);
    }
}
