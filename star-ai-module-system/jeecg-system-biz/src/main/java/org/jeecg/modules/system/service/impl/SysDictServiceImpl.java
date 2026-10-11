package org.jeecg.modules.system.service.impl;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.DataBaseConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.exception.JeecgSqlInjectionException;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.security.CommonRoleGuard;
import org.jeecg.common.util.security.C3FieldReflectionInspector;
import org.jeecg.common.system.util.ResourceUtil;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.system.vo.DictModelMany;
import org.jeecg.common.system.vo.DictQuery;
import org.jeecg.common.util.security.LedgerWriteGuard;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.DictI18nUtil;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.SqlInjectionUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.common.util.OpsNotifyEmailUtil;
import org.jeecg.config.firewall.SensitiveTableBlacklistInterceptor;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.system.entity.SysDict;
import org.jeecg.modules.system.entity.SysDictItem;
import org.jeecg.modules.system.mapper.SysDictItemMapper;
import org.jeecg.modules.system.mapper.SysDictMapper;
import org.jeecg.modules.system.model.DuplicateCheckVo;
import org.jeecg.modules.system.model.TreeSelectModel;
import org.jeecg.modules.system.security.DictQueryBlackListHandler;
import org.jeecg.modules.system.service.ISysDictService;
import org.jeecg.modules.system.service.ISysDictItemService;
import org.jeecg.modules.system.vo.lowapp.SysDictVo;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>
 * Dictionary table service implementation
 * </p>
 *
 * @Author zhangweijian
 * @since 2018-12-28
 */
@Service
@Slf4j
public class SysDictServiceImpl extends ServiceImpl<SysDictMapper, SysDict> implements ISysDictService {

    private static final Set<String> REQUIRED_FIELDS_FOR_THIS_TABLE =
        Set.of("delFlag", "updateBy", "updateTime");


	@Lazy
	@Lazy
	@Lazy
	@Override
	public boolean duplicateCheckData(DuplicateCheckVo duplicateCheckVo) {
		Long count = null;

		// 1.escape/check table/column names using ${}
		String table = SqlInjectionUtil.getSqlInjectTableName(duplicateCheckVo.getTableName());
		String fieldName = SqlInjectionUtil.getSqlInjectField(duplicateCheckVo.getFieldName());
		duplicateCheckVo.setTableName(table);
		duplicateCheckVo.setFieldName(fieldName);
		
		// 2.SQL injection check (block illegal DB tampering)
		//Association table dictionary (example: sys_user, realname, id)
		SqlInjectionUtil.filterContentMulti(table, fieldName);

		String checkSql = table + SymbolConstant.COMMA + fieldName + SymbolConstant.COMMA;
		// [QQYUN-6533] Table-dict whitelist check
		sysBaseAPI.dictTableWhiteListCheckByDict(table, fieldName);
		// 3.table-dict blacklist check
		dictQueryBlackListHandler.isPass(checkSql);

		// 4.execute SQL to check value exists
		try{
			String dataId = duplicateCheckVo.getDataId();
			// Frontend FormData/undefined can arrive as literal "undefined"/"null" — treat as add-path
			if (StringUtils.isNotBlank(dataId)
					&& !"undefined".equalsIgnoreCase(dataId)
					&& !"null".equalsIgnoreCase(dataId)) {
				// [1].Edit-page validation
				count = sysDictMapper.duplicateCheckCountSql(duplicateCheckVo);
			} else {
				// [2].add page check
				count = sysDictMapper.duplicateCheckCountSqlNoDataId(duplicateCheckVo);
			}
		}catch(MyBatisSystemException e){
			log.error("operation failed", e);
			String errorCause = "Query exception, please check unique validation configuration!";
			throw new JeecgBootException(errorCause);
		}

		// 4.return result
		if (count == null || count == 0) {
			// Value is available
			return true;
		} else {
			// Value is not available
			log.info("This value is unavailable, already exists in the system!");
			return false;
		}
	}


	/**
	 * Get dictionary by specified code
	 * @param code
	 * @return
	 */
	@Override
	@Cacheable(value = CacheConstant.SYS_DICT_CACHE,key = "#code", unless = "#result == null ")
	public List<DictModel> queryDictItemsByCode(String code) {
		log.debug("Called here when dictCache is not cached!");
		return sysDictMapper.queryDictItemsByCode(code);
	}

	@Override
	@Cacheable(value = CacheConstant.SYS_ENABLE_DICT_CACHE,key = "#code", unless = "#result == null ")
	public List<DictModel> queryEnableDictItemsByCode(String code) {
		log.debug("Called here when dictCache is not cached!");
		return sysDictMapper.queryEnableDictItemsByCode(code);
	}

	@Override
	public Map<String, List<DictModel>> queryDictItemsByCodeList(List<String> dictCodeList) {
		List<DictModelMany> list = sysDictMapper.queryDictItemsByCodeList(dictCodeList);
		Map<String, List<DictModel>> dictMap = new HashMap(5);
		for (DictModelMany dict : list) {
			List<DictModel> dictItemList = dictMap.computeIfAbsent(dict.getDictCode(), i -> new ArrayList<>());
			
			//update-begin-author:taoyan date:2023-4-28 for: QQYUN-5183simple-flow multi-field join needs translate for select fields
			//dict.setDictCode(null);
			//update-end-author:taoyan date:2023-4-28 for: QQYUN-5183simple-flow multi-field join needs translate for select fields
			
			dictItemList.add(new DictModel(dict.getValue(), dict.getText(), dict.getColor()));
		}
		return dictMap;
	}

	@Override
	public Map<String, List<DictModel>> queryAllDictItems() {
		log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
		long start = System.currentTimeMillis();
		Map<String, List<DictModel>> sysAllDictItems = new HashMap(5);
		List<Integer> tenantIds = null;
		//------------------------------------------------------------------------------------------------
		//Whether to enable multi-tenant data isolation for the system module [SAAS multi-tenant mode]
		if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
			tenantIds = new ArrayList<>();
			tenantIds.add(0);
			if (TenantContext.getTenant() != null) {
				tenantIds.add(oConvertUtils.getInt(TenantContext.getTenant()));
			}
		}
		//------------------------------------------------------------------------------------------------
		List<DictModelMany> sysDictItemList = sysDictMapper.queryAllDictItems(tenantIds);
		// Use groupingBy to group based on dictCode
		sysAllDictItems = sysDictItemList.stream()
				.collect(Collectors.groupingBy(DictModelMany::getDictCode,
						Collectors.mapping(d -> new DictModel(d.getValue(), d.getText(), d.getColor()), Collectors.toList())));
		log.info("      >>> 1 System dictionary item fetch elapsed (SQL): " + (System.currentTimeMillis() - start) + "ms");

		Map<String, List<DictModel>> enumRes = ResourceUtil.getEnumDictData();
		sysAllDictItems.putAll(enumRes);
		log.info("      >>> 2 System dictionary item fetch elapsed (Enum): " + (System.currentTimeMillis() - start) + "ms");
		
		log.info("      >>> end Total dictionary library fetch elapsed: " + (System.currentTimeMillis() - start) + "ms");
		log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

		//log.info("------- load system dict on login -----" + sysAllDictItems.toString());
		return sysAllDictItems;
	}

	/**
	 * Get dictionary text by specified code
	 * @param code
	 * @param key
	 * @return
	 */

	@Override
	@Cacheable(value = CacheConstant.SYS_DICT_CACHE,key = "#code+':'+#key", unless = "#result == null ")
	public String queryDictTextByKey(String code, String key) {
		log.debug("Called here when dictText is not cached!");
		return sysDictMapper.queryDictTextByKey(code, key);
	}

	@Override
	public Map<String, List<DictModel>> queryManyDictByKeys(List<String> dictCodeList, List<String> keys) {
		List<DictModelMany> list = sysDictMapper.queryManyDictByKeys(dictCodeList, keys);
		Map<String, List<DictModel>> dictMap = new HashMap(5);
		for (DictModelMany dict : list) {
			List<DictModel> dictItemList = dictMap.computeIfAbsent(dict.getDictCode(), i -> new ArrayList<>());
			dictItemList.add(new DictModel(dict.getValue(), dict.getText()));
		}
		//update-begin-author:taoyan date:2022-7-8 for: system dict should include custom java enum classes
		Map<String, List<DictModel>> enumRes = ResourceUtil.queryManyDictByKeys(dictCodeList, keys);
		dictMap.putAll(enumRes);
		//update-end-author:taoyan date:2022-7-8 for: system dict should include custom java enum classes
		return dictMap;
	}

	/**
	 * Get dictionary by table text/code
	 * dictTableCache uses Redis with 10-minute TTL
	 * @param tableFilterSql
	 * @param text
	 * @param code
	 * @return
	 */
	@Override
	@Deprecated
	public List<DictModel> queryTableDictItemsByCode(String tableFilterSql, String text, String code) {
		log.debug("Called here when dictTableList is not cached!");
		String str = tableFilterSql+","+text+","+code;
		// [QQYUN-6533] Table-dict whitelist check
		sysBaseAPI.dictTableWhiteListCheckByDict(tableFilterSql, text, code);
		// 1. Table-dict blacklist check
		if(!dictQueryBlackListHandler.isPass(str)){
			log.error(dictQueryBlackListHandler.getError());
			return null;
		}

		// 2.split SQL into table and condition
		String table = null;
		String filterSql = null;
		if(tableFilterSql.toLowerCase().indexOf(DataBaseConstant.SQL_WHERE)>0){
			String[] arr = tableFilterSql.split(" (?i)where ");
			table = arr[0];
			filterSql = oConvertUtils.getString(arr[1], null);
		}else{
			table = tableFilterSql;
		}
		
		// 3. SQL injection check
		SqlInjectionUtil.filterContentMulti(table, text, code);
		SqlInjectionUtil.specialFilterContentForDictSql(filterSql);
		
		// 4. Escape/check table/column names using ${}
		table = SqlInjectionUtil.getSqlInjectTableName(table);
		text = SqlInjectionUtil.getSqlInjectField(text);
		code = SqlInjectionUtil.getSqlInjectField(code);
		
		//return sysDictMapper.queryTableDictItemsByCode(tableFilterSql,text,code);
		return sysDictMapper.queryTableDictWithFilter(table,text,code);
	}

	@Override
	public List<DictModel> queryTableDictItemsByCodeAndFilter(String table, String text, String code) {
		return this.queryTableDictItemsByCodeAndFilter(table, text, code, null);
	}

	/**
	 * Table-dict query with an optional single-column equality filter — the dictCode 4th segment, e.g.
	 * {@code dcs_currency,code,code,type='1'}.
	 *
	 * <p>[1cf862ac] That 4th segment used to be a raw SQL fragment concatenated as {@code
	 * ${filterSql}}; the parameter was removed because it was an injection sink (see the "E2 fix"
	 * note in SysDictMapper.xml). Functionality is restored here strictly as "allowlisted
	 * column-based conditions": only a single {@code col = value} equality is accepted, the column
	 * must be a plain identifier and must pass the table-dict whitelist, and the value is bound as a
	 * parameter. Anything else is ignored (never concatenated), preserving fail-safe behavior.
	 */
	@Override
	public List<DictModel> queryTableDictItemsByCodeAndFilter(String table, String text, String code, String filter) {
		log.debug("Called here when dictTableList is not cached!");

		// 1.SQL injection check (block illegal DB tampering)
		SqlInjectionUtil.specialFilterContentForDictSql(table);
		SqlInjectionUtil.filterContentMulti(text, code);

		TableDictFilter parsedFilter = parseTableDictFilter(filter);
		if (parsedFilter == null && oConvertUtils.isNotEmpty(filter)) {
			log.warn("Unsupported table-dict filter syntax, ignored: table={}, filter={}", table, filter);
		}

		String str = table+","+text+","+code;
		// [QQYUN-6533] Table-dict whitelist check (the filter column must be whitelisted as well)
		if (parsedFilter == null) {
			sysBaseAPI.dictTableWhiteListCheckByDict(table, text, code);
		} else {
			sysBaseAPI.dictTableWhiteListCheckByDict(table, text, code, parsedFilter.column);
		}
		// 2.table-dict blacklist check
		if(!dictQueryBlackListHandler.isPass(str)){
			log.error(dictQueryBlackListHandler.getError());
			return null;
		}

		// 3.escape/check table/column names using ${}
		table = SqlInjectionUtil.getSqlInjectTableName(table);
		text = SqlInjectionUtil.getSqlInjectField(text);
		code = SqlInjectionUtil.getSqlInjectField(code);

		if (parsedFilter == null) {
			return sysDictMapper.queryTableDictWithFilter(table,text,code);
		}
		// 4.re-validate the filter column as a plain identifier; the value stays a bound parameter
		String filterColumn = SqlInjectionUtil.getSqlInjectField(parsedFilter.column);
		return sysDictMapper.queryTableDictWithFilterAndColumn(table, text, code, filterColumn, parsedFilter.value);
	}

	/**
	 * Single equality condition: column must be a plain identifier; value is a quoted string, a
	 * double-quoted string, or a bare token without whitespace/quotes/backticks/semicolons.
	 */
	private static final Pattern TABLE_DICT_FILTER_PATTERN =
			Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(?:'([^']*)'|\"([^\"]*)\"|([^\\s'\"`;]+))");

	/**
	 * Parse the dictCode 4th segment into a single-column equality filter.
	 *
	 * <p>Accepted: {@code col = 'value'}, {@code col = "value"}, {@code col = 1}; surrounding
	 * whitespace is allowed. The column must match {@code [A-Za-z_][A-Za-z0-9_]*} and a quoted value
	 * may not contain its own quote. Everything else (multiple conditions, and/or, functions,
	 * sub-selects, {@code is null}, comments...) returns {@code null} so the caller treats it as "no
	 * filter" instead of concatenating it into SQL.
	 */
	static TableDictFilter parseTableDictFilter(String filter) {
		if (oConvertUtils.isEmpty(filter)) {
			return null;
		}
		Matcher matcher = TABLE_DICT_FILTER_PATTERN.matcher(filter.trim());
		if (!matcher.matches()) {
			return null;
		}
		String value = matcher.group(2) != null ? matcher.group(2)
				: (matcher.group(3) != null ? matcher.group(3) : matcher.group(4));
		return new TableDictFilter(matcher.group(1), value);
	}

	/** Single-column equality filter parsed from a dictCode 4th segment. */
	static final class TableDictFilter {
		final String column;
		final String value;

		TableDictFilter(String column, String value) {
			this.column = column;
			this.value = value;
		}
	}
	
	/**
	 * Get dictionary text by table text/code
	 * dictTableCache uses Redis with 10-minute TTL
	 * @param table
	 * @param text
	 * @param code
	 * @param key
	 * @return
	 */
	@Override
	@Cacheable(value = CacheConstant.SYS_DICT_TABLE_CACHE, unless = "#result == null ")
	public String queryTableDictTextByKey(String table,String text,String code, String key) {
		log.debug("Called here when dictTable is not cached!");
		
		String str = table+","+text+","+code;
		// [QQYUN-6533] Table-dict whitelist check
		sysBaseAPI.dictTableWhiteListCheckByDict(table, text, code);
		// 1. Table-dict blacklist check
		if(!dictQueryBlackListHandler.isPass(str)){
			log.error(dictQueryBlackListHandler.getError());
			return null;
		}
		// 2.SQL injectioncheck
		SqlInjectionUtil.filterContentMulti(table, text, code, key);

		// 3.escape/check table/column names using ${}
		table = SqlInjectionUtil.getSqlInjectTableName(table);
		text = SqlInjectionUtil.getSqlInjectField(text);
		code = SqlInjectionUtil.getSqlInjectField(code);
		
		List<DictModel> dictModeList = sysDictMapper.queryTableDictByKeysAndFilterSql(table, text, code, Arrays.asList(key));
		if(CollectionUtils.isEmpty(dictModeList)){
			return null;
		}else{
			return dictModeList.get(0).getText();
		}
		
		//This method delete(20230902)
		//return sysDictMapper.queryTableDictTextByKey(table,text,code,key);
	}

	@Override
	public List<DictModel> queryTableDictTextByKeys(String table, String text, String code, List<String> codeValues, String dataSource) {
		String str = table+","+text+","+code;
		boolean isCustomDataSource = oConvertUtils.isNotEmpty(dataSource);

		sysBaseAPI.dictTableWhiteListCheckByDict(table, text, code);
		if (!dictQueryBlackListHandler.isPass(str)) {
			log.error(dictQueryBlackListHandler.getError());
			return null;
		}

		if (isCustomDataSource) {
			validateCustomDataSource(dataSource);
		}

		String filterSql = null;
		if(table.toLowerCase().indexOf(DataBaseConstant.SQL_WHERE)>0){
			String[] arr = table.split(" (?i)where ");
			table = arr[0];
			filterSql = arr[1];
		}

		SqlInjectionUtil.filterContentMulti(table, text, code);
		SqlInjectionUtil.specialFilterContentForDictSql(filterSql);

		table = SqlInjectionUtil.getSqlInjectTableName(table);
		text = SqlInjectionUtil.getSqlInjectField(text);
		code = SqlInjectionUtil.getSqlInjectField(code);

		try {
			if (isCustomDataSource) {
				DynamicDataSourceContextHolder.push(dataSource);
			}
			List<DictModel> restData = sysDictMapper.queryTableDictByKeysAndFilterSql(table, text, code, codeValues);
			return restData;
		} finally {
			if (isCustomDataSource) {
				DynamicDataSourceContextHolder.clear();
			}
		}
	}

	@Override
	public List<String> queryTableDictByKeys(String table, String text, String code, String keys) {
		String str = table+","+text+","+code;
		// [QQYUN-6533] Table-dict whitelist check
		sysBaseAPI.dictTableWhiteListCheckByDict(table, text, code);
		// 1. Table-dict blacklist check
		if(!dictQueryBlackListHandler.isPass(str)){
			log.error(dictQueryBlackListHandler.getError());
			return null;
		}
		
		return this.queryTableDictByKeys(table, text, code, keys, true);
	}

	/**
	 * Get the dictionary by querying the text code of the specified table, including text and value
	 * dictTableCache uses Redis with 10-minute TTL
	 * @param table
	 * @param text
	 * @param code
	 * @param codeValuesStr (comma-separated)
	 * @param delNotExist whether to remove missing keys (default true); if false, return the key itself when absent in DB
	 * @return
	 */
	@Override
	public List<String> queryTableDictByKeys(String table, String text, String code, String codeValuesStr, boolean delNotExist) {
		if(oConvertUtils.isEmpty(codeValuesStr)){
			return null;
		}

		//1.split SQL into table name and condition SQL
		String filterSql = null;
		if(table.toLowerCase().indexOf("where")!=-1){
			String[] arr = table.split(" (?i)where ");
			table = arr[0];
			filterSql = arr[1];
		}

		// 2.SQL injectioncheck
		SqlInjectionUtil.filterContentMulti(table, text, code);
		SqlInjectionUtil.specialFilterContentForDictSql(filterSql);

		String str = table+","+text+","+code;
		// [QQYUN-6533] Table-dict whitelist check
		sysBaseAPI.dictTableWhiteListCheckByDict(table, text, code);
		// 3.table-dict blacklist check
		if(!dictQueryBlackListHandler.isPass(str)){
			log.error(dictQueryBlackListHandler.getError());
			return null;
		}
		
		// 4. Escape/check table/column names using ${}
		table = SqlInjectionUtil.getSqlInjectTableName(table);
		text = SqlInjectionUtil.getSqlInjectField(text);
		code = SqlInjectionUtil.getSqlInjectField(code);
		
		//dictionary condition value
		String[] codeValues = codeValuesStr.split(",");
		// 5.Query dictionary data
		List<DictModel> dicts = sysDictMapper.queryTableDictByKeysAndFilterSql(SqlInjectionUtil.getSqlInjectTableName(table), 
				SqlInjectionUtil.getSqlInjectField(text), SqlInjectionUtil.getSqlInjectField(code), Arrays.asList(codeValues));
		
		List<String> texts = new ArrayList<>(dicts.size());
		// 6.result order may be random; sort it
		for (String conditionalVal : codeValues) {
			List<DictModel> res = dicts.stream().filter(i -> conditionalVal.equals(i.getValue())).collect(Collectors.toList());
			if (res.size() > 0) {
				texts.add(res.get(0).getText());
			} else if (!delNotExist) {
				texts.add(conditionalVal);
			}
		}
		return texts;
	}

    /**
     * Delete corresponding data in relation table by dictionary type id
     */
    // C3-WHITELIST-CTRLLED: field-level only (not updateById whole entity)
    @Override
    public boolean deleteByDictId(SysDict sysDict) {
        LambdaUpdateWrapper<SysDict> uw = Wrappers.lambdaUpdate();
        uw.eq(SysDict::getId, sysDict.getId())
          .set(SysDict::getDelFlag, CommonConstant.DEL_FLAG_1)
          .set(SysDict::getUpdateBy, StpUtil.getLoginIdAsString())
          .set(SysDict::getUpdateTime, new Date());
        Set<String> actual = C3FieldReflectionInspector.extractSetFieldNames(uw);
        if (!actual.containsAll(REQUIRED_FIELDS_FOR_THIS_TABLE)) { Set<String> miss = new HashSet<>(REQUIRED_FIELDS_FOR_THIS_TABLE); miss.removeAll(actual); throw new JeecgBootException("C3 BLOCK MISSING_WRITE_FIELDS table=sys_dict missing=" + miss); }
        // LedgerWriteGuard.assertLambdaUpdateWrapperFields("sys_dict", uw, "update_time", "update_by"); // table not whitelisted in LedgerWriteGuard
        return baseMapper.update(null, uw) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer saveMain(SysDict sysDict, List<SysDictItem> sysDictItemList) {
		int insert=0;
    	try{
			 insert = sysDictMapper.insert(sysDict);
			if (sysDictItemList != null) {
				for (SysDictItem entity : sysDictItemList) {
                    //update-begin---author:wangshuai ---date:20220211 for: [JTC-1168]skip import when dict item value empty------------
				    if(oConvertUtils.isEmpty(entity.getItemValue())){
				        return -1;
                    }
                    //update-end---author:wangshuai ---date:20220211 for: [JTC-1168]skip import when dict item value empty------------
					entity.setDictId(sysDict.getId());
					entity.setStatus(1);
					sysDictItemMapper.insert(entity);
				}
			}
		}catch(Exception e){
			return insert;
		}
		return insert;
    }

	@Override
	public List<DictModel> queryAllDepartBackDictModel() {
		return baseMapper.queryAllDepartBackDictModel();
	}

	@Override
	public List<DictModel> queryAllUserBackDictModel() {
		return baseMapper.queryAllUserBackDictModel();
	}
	
//	@Override
//	public List<DictModel> queryTableDictItems(String table, String text, String code, String keyword) {
//		return baseMapper.queryTableDictItems(table, text, code, "%"+keyword+"%");
//	}

	@Override
	public List<DictModel> queryLittleTableDictItems(String tableSql, String text, String code, String condition, String keyword, int pageSize) {
    	Page<DictModel> page = new Page<DictModel>(1, pageSize);
		page.setSearchCount(false);
		
		//SQL injection guard (Jeecg escapes params when building SQL)
		// 1. escape/check table/column names using ${}
		String table = SqlInjectionUtil.getSqlInjectTableName(CommonUtils.getTableNameByTableSql(tableSql));
		text = SqlInjectionUtil.getSqlInjectField(text);
		code = SqlInjectionUtil.getSqlInjectField(code);

		// 2. query conditional SQL (obtaining conditional sql method includes sql injection check)
		String filterSql = getFilterSql(tableSql, text, code, condition, keyword);
		
		// 3. return table-dict data
		IPage<DictModel> pageList = baseMapper.queryPageTableDictWithFilter(page, table, text, code);
		return pageList.getRecords();
	}

	/**
	 * Get condition clause (dropdown search supports sort in query)
	 * 
	 * @param text
	 * @param code
	 * @param condition
	 * @param keyword
	 * @return
	 */
	private String getFilterSql(String tableSql, String text, String code, String condition, String keyword){
		String filterSql = "";
		String keywordSql = null;
		String sqlWhere = "where ";
		String sqlAnd = " and ";
		
		//【JTC-631】if table has WHERE, append with AND to avoid errors
		boolean tableHasWhere = tableSql.toLowerCase().contains(sqlWhere);
        if (tableHasWhere) {
			sqlWhere = CommonUtils.getFilterSqlByTableSql(tableSql);
		}

		// Drop-down search component supports incoming sorting information query sorting
		String orderField = "", orderType = "";
		if (oConvertUtils.isNotEmpty(keyword)) {
			// If the sorting information is written in the keyword xxxxx[orderby:create_time,desc]
			String orderKey = "[orderby";
			if (keyword.indexOf(orderKey) >= 0 && keyword.endsWith("]")) {
				String orderInfo = keyword.substring(keyword.indexOf(orderKey) + orderKey.length() + 1, keyword.length() - 1);
				keyword = keyword.substring(0, keyword.indexOf(orderKey));
				String[] orderInfoArray = orderInfo.split(SymbolConstant.COMMA);
				orderField = orderInfoArray[0];
				orderType = orderInfoArray[1];
			}

			if (oConvertUtils.isNotEmpty(keyword)) {
				// Determine whether it is multiple selection
				if (keyword.contains(SymbolConstant.COMMA)) {
					//update-begin--author:scott--date:20220105--for: JTC-529form designer edit error caused by double quotes in IN params ----
					String inKeywords = "'" + String.join("','", keyword.split(",")) + "'";
					//update-end--author:scott--date:20220105--for: JTC-529form designer edit error caused by double quotes in IN params----
					keywordSql = "(" + text + " in (" + inKeywords + ") or " + code + " in (" + inKeywords + "))";
				} else {
					keywordSql = "("+text + " like '%"+keyword+"%' or "+ code + " like '%"+keyword+"%')";
				}
			}
		}
		
		//Drop-down search component supports incoming sorting information query sorting
		//update-begin---author:chenrui ---date:20240327 for: [QQYUN-8514]online dropdown search SQL error: extra AND ------------
        if (oConvertUtils.isNotEmpty(condition) && oConvertUtils.isNotEmpty(keywordSql)) {
            filterSql += sqlWhere + (tableHasWhere ? sqlAnd : " ") + condition + sqlAnd + keywordSql;
        } else if (oConvertUtils.isNotEmpty(condition)) {
            filterSql += sqlWhere + (tableHasWhere ? sqlAnd : " ") + condition;
        } else if (oConvertUtils.isNotEmpty(keywordSql)) {
            filterSql += sqlWhere + (tableHasWhere ? sqlAnd : " ") + keywordSql;
        } else if (tableHasWhere) {
            filterSql += sqlWhere;
        }
		//update-end---author:chenrui ---date:20240327 for: [QQYUN-8514]online dropdown search SQL error: extra AND ------------
		// Add sorting logic
		if (oConvertUtils.isNotEmpty(orderField)) {
			filterSql += " order by " + orderField + " " + orderType;
		}

		// ProcessReturnCondition
		// 1.1 return condition SQL (strip leading where)
		final String wherePrefix = "(?i)where "; // (?i) means case-insensitive
		String filterSqlString = filterSql.trim().replaceAll(wherePrefix, "");
		// 1.2 vulnerability-check condition SQL
		SqlInjectionUtil.specialFilterContentForDictSql(filterSqlString);
		// 1.3 if condition starts with order by, prefix 1=1
		if (oConvertUtils.isNotEmpty(filterSqlString) && filterSqlString.trim().toUpperCase().startsWith("ORDER")) {
			filterSqlString = " 1=1 " + filterSqlString;
		}
		return filterSqlString;
	}
	
	
	@Override
	public List<DictModel> queryAllTableDictItems(String table, String text, String code, String condition, String keyword) {
		// 1.get condition SQL
		String filterSql = getFilterSql(table, text, code, condition, keyword);

		// SQL injection guard (Jeecg escapes params when building SQL)
		// 2.escape/check table/column names using ${}
		table = SqlInjectionUtil.getSqlInjectTableName(table);
		text = SqlInjectionUtil.getSqlInjectField(text);
		code = SqlInjectionUtil.getSqlInjectField(code);
		
		List<DictModel> ls = baseMapper.queryTableDictWithFilter(table, text, code);
    	return ls;
	}

	@Override
	public List<TreeSelectModel> queryTreeList(Map<String, String> query, String table, String text, String code, String pidField, String pid, String hasChildField, int converIsLeafVal) {
		//SQL injection guard (Jeecg escapes params when building SQL)
		// 1.escape/check table/column names using ${}
		table = SqlInjectionUtil.getSqlInjectTableName(table);
		text = SqlInjectionUtil.getSqlInjectField(text);
		code = SqlInjectionUtil.getSqlInjectField(code);
		pidField = SqlInjectionUtil.getSqlInjectField(pidField);
		hasChildField = SqlInjectionUtil.getSqlInjectField(hasChildField);

		if(oConvertUtils.isEmpty(text) || oConvertUtils.isEmpty(code)){
			log.warn("text={}，code={}", text, code);
			log.warn("Invalid tree dictionary parameters; text and code must not be empty!");
			return null;
		}
		
		// 2.check final SQL for injection risk
		String dictCode = table + "," + text + "," + code;
		SqlInjectionUtil.filterContentMulti(dictCode);

		// [QQYUN-6533] Table-dict whitelist check
		sysBaseAPI.dictTableWhiteListCheckByDict(table, text, code);
		// 3.table-dict SQL table-name blacklist check
		if(!dictQueryBlackListHandler.isPass(dictCode)){
			log.error("SqlException：{}", dictQueryBlackListHandler.getError());
			return null;
		}
		// 4.check query condition for SQL injection
		Map<String, String> queryParams = null;
		if (query != null) {
			queryParams = new HashMap<>(5);
			for (Map.Entry<String, String> searchItem : query.entrySet()) {
				String fieldName = searchItem.getKey();
				queryParams.put(SqlInjectionUtil.getSqlInjectField(fieldName), searchItem.getValue());
			}
		}
		
		return baseMapper.queryTreeList(queryParams, table, text, code, pidField, pid, hasChildField, converIsLeafVal);
	}

	@Override
	public void deleteOneDictPhysically(String id) {
		this.baseMapper.deleteOneById(id);
		this.sysDictItemMapper.delete(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getDictId,id));
	}

	@Override
	public void updateDictDelFlag(int delFlag, String id) {
		baseMapper.updateDictDelFlag(delFlag,id);
	}

	@Override
	public List<SysDict> queryDeleteList(String tenantId) {
		//update-begin---author:wangshuai---date:2024-02-27---for:【QQYUN-8340】recycle soft-delete ignored multi-tenant; could recover other tenants #5907---
		if(MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL){
			if(oConvertUtils.isEmpty(tenantId)){
				return new ArrayList<>();
			}
			return baseMapper.queryDeleteListBtTenantId(oConvertUtils.getInt(tenantId));
		}
		//update-end---author:wangshuai---date:2024-02-27---for:【QQYUN-8340】recycle soft-delete ignored multi-tenant; could recover other tenants #5907---
		return baseMapper.queryDeleteList();
	}

	@Override
	public List<DictModel> queryDictTablePageList(DictQuery query, int pageSize, int pageNo) {
		Page page = new Page(pageNo,pageSize,false);
		
		//SQL injection guard (Jeecg escapes params when building SQL)
		// 1. escape/check table/column names using ${}
		String table = SqlInjectionUtil.getSqlInjectTableName(query.getTable());
		String text = SqlInjectionUtil.getSqlInjectTableName(query.getText());
		String code = SqlInjectionUtil.getSqlInjectTableName(query.getCode());
		query.setCode(table);
		query.setTable(text);
		query.setText(code);
		
		String dictCode = table+","+text+","+code;
		// [QQYUN-6533] Table-dict whitelist check
		sysBaseAPI.dictTableWhiteListCheckByDict(table, text, code);
		// 2.table-dict blacklist check
		if(!dictQueryBlackListHandler.isPass(dictCode)){
			log.error(dictQueryBlackListHandler.getError());
			return null;
		}
		
		// 3. SQL injection check
		SqlInjectionUtil.filterContentMulti(dictCode);
		
		Page<DictModel> pageList = baseMapper.queryDictTablePageList(page, query);
		return pageList.getRecords();
	}

	/**
	 * Hard-reject a plain (non table) dict code whose name collides with a sensitive table — audit gap
	 * "敏感 dict 必须 403/硬拒绝". {@code loadDict} only accepts table-dict codes, so a plain name such
	 * as {@code sys_user_mfa} can never be a legitimate request there. Sensitive <b>tables</b> are
	 * handled separately: they stay usable when explicitly whitelisted (sanctioned
	 * {@code sys_user}/{@code sys_role} dropdowns) and otherwise hard-reject in
	 * {@link #getDictItems(String)}.
	 */
	private void denySensitivePlainDictCode(String dictCode) {
		if (oConvertUtils.isEmpty(dictCode) || dictCode.contains(SymbolConstant.COMMA)) {
			return;
		}
		if (SensitiveTableBlacklistInterceptor.isSensitiveTableName(dictCode)) {
			log.warn("[SEC-DICT-SENSITIVE-DENY] plain dictCode={}", dictCode);
			throw new NotPermissionException("sys:dict:sensitive:" + dictCode);
		}
	}

	@Override
	public List<DictModel> getDictItems(String dictCode) {
		List<DictModel> ls;
		if (dictCode.contains(SymbolConstant.COMMA)) {
			//Association table dictionary (example: sys_user, realname, id)
			String[] params = dictCode.split(",");
			if (params.length < 3) {
				// Invalid dict code format
				return null;
			}
	
			try {
				if (params.length == 4) {
					ls = this.queryTableDictItemsByCodeAndFilter(params[0], params[1], params[2], params[3]);
				} else if (params.length == 3) {
					ls = this.queryTableDictItemsByCode(params[0], params[1], params[2]);
				} else {
					// Invalid dict code format
					return null;
				}
			} catch (JeecgSqlInjectionException e) {
				// A sensitive table that is not explicitly whitelisted must be a hard reject (403),
				// never a swallowed 200 + empty list.
				if (SensitiveTableBlacklistInterceptor.isSensitiveTableName(params[0])) {
					log.warn("[SEC-DICT-SENSITIVE-DENY] table={} reason={}", params[0], e.getMessage());
					throw new NotPermissionException("sys:dict:sensitive:" + params[0]);
				}
				throw e;
			}
		} else {
			//Dictionary
			ls = this.queryDictItemsByCode(dictCode);
		}
		//update-begin-author:taoyan date:2022-8-30 for: dict fetch can load enum-class data
		if (ls == null || ls.size() == 0) {
			Map<String, List<DictModel>> map = ResourceUtil.getEnumDictData();
			if (map.containsKey(dictCode)) {
				ls = map.get(dictCode);
			}
		}
		//update-end-author:taoyan date:2022-8-30 for: dict fetch can load enum-class data
		// A plain dict code colliding with a sensitive table that resolves to nothing is a hard reject:
		// answering 200 + [] is not an acceptable audit outcome (gap: "敏感 dict 必须 403/硬拒绝").
		// Registered plain dicts such as sys_config keep working because they return items above.
		if (!dictCode.contains(SymbolConstant.COMMA)
				&& (ls == null || ls.isEmpty())
				&& SensitiveTableBlacklistInterceptor.isSensitiveTableName(dictCode)) {
			log.warn("[SEC-DICT-SENSITIVE-DENY] plain dictCode={}", dictCode);
			throw new NotPermissionException("sys:dict:sensitive:" + dictCode);
		}
		// Parse multilingual JSON by request header lang; copy list to avoid polluting original item_text in cache
		return localizeDictModels(ls);
	}

	/**
	 * Resolve dictionary item text to display label for current locale (does not modify cached original object).
	 */
	private List<DictModel> localizeDictModels(List<DictModel> source) {
		if (source == null || source.isEmpty()) {
			return source;
		}
		List<DictModel> result = new ArrayList<>(source.size());
		for (DictModel item : source) {
			if (item == null) {
				continue;
			}
			result.add(
					new DictModel(
							item.getValue(),
							DictI18nUtil.resolveLocalizedText(item.getText()),
							item.getColor()));
		}
		return result;
	}

	@Override
	public List<DictModel> loadDict(String dictCode, String keyword, Integer pageSize) {
		denySensitivePlainDictCode(dictCode);
		// [QQYUN-6533] Table-dict whitelist check
		sysBaseAPI.dictTableWhiteListCheckByDict(dictCode);
		// 1. Table-dict blacklist check
		if(!dictQueryBlackListHandler.isPass(dictCode)){
			log.error(dictQueryBlackListHandler.getError());
			return null;
		}
		
		// 2.dict SQL injection risk check
		SqlInjectionUtil.specialFilterContentForDictSql(dictCode);

		if (dictCode.contains(SymbolConstant.COMMA)) {
			//update-begin-author:taoyan date:20210329 for: dropdown search did not support table WHERE suffix
			String[] params = dictCode.split(",");
			String condition = null;
			if (params.length != 3 && params.length != 4) {
				// Invalid dict code format
				return null;
			} else if (params.length == 4) {
				condition = params[3];
				// update-begin-author:taoyan date:20220314 for: online dropdown table-dict #{sys_org_code} error #3500
				if(condition.indexOf(SymbolConstant.SYS_VAR_PREFIX)>=0){
					condition =  QueryGenerator.getSqlRuleValue(condition);
				}
				// update-end-author:taoyan date:20220314 for: online dropdown table-dict #{sys_org_code} error #3500
			}

			// Dictionary Code format is incorrect [Table name is empty]
			if(oConvertUtils.isEmpty(params[0])){
				return null;
			}
			List<DictModel> ls;
			if (pageSize != null) {
				ls = this.queryLittleTableDictItems(params[0], params[1], params[2], condition, keyword, pageSize);
			} else {
				ls = this.queryAllTableDictItems(params[0], params[1], params[2], condition, keyword);
			}
			//update-end-author:taoyan date:20210329 for: dropdown search did not support table WHERE suffix
			return ls;
		} else {
			// Invalid dict code format
			return null;
		}
	}

	@Override
	public List<SysDictVo> getDictListByLowAppId(String lowAppId) {
		int tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
		List<SysDict> list =  baseMapper.getDictListByLowAppId(lowAppId,tenantId);
		//Dictionary items under query dictionary
		List<SysDictVo> dictVoList = new ArrayList<>();
		for (SysDict dict:list) {
			SysDictVo dictVo = new SysDictVo();
			BeanUtils.copyProperties(dict,dictVo);
			List<SysDictItem> sysDictItems = sysDictItemMapper.selectItemsByMainId(dict.getId());
			dictVo.setDictItemsList(sysDictItems);
			dictVoList.add(dictVo);
		}
		return dictVoList;
	}

	@Override
	public String addDictByLowAppId(SysDictVo sysDictVo) {
		String[] dictResult = this.addDict(sysDictVo.getDictName(),sysDictVo.getLowAppId(),sysDictVo.getTenantId());
		String id = dictResult[0];
		String code = dictResult[1];
		this.addDictItem(id,sysDictVo.getDictItemsList());
		return code;
	}

	@Override
	public void editDictByLowAppId(SysDictVo sysDictVo) {
		String id = sysDictVo.getId();
		SysDict dict = baseMapper.selectById(id);
		if(null == dict){
			throw new JeecgBootException("DictionaryData does not exist");
		}
		//Determine whether the application ID is consistent with the one in the database. If the application ID is inconsistent, edit will not be allowed.
		if(!dict.getLowAppId().equals(sysDictVo.getLowAppId())){
			throw new JeecgBootException("DictionaryData does not exist");
		}
		SysDict sysDict = new SysDict();
		sysDict.setDictName(sysDictVo.getDictName());
		sysDict.setId(id);
		baseMapper.updateById(sysDict);
		this.updateDictItem(id,sysDictVo.getDictItemsList());
		// DeleteDictionaryCache
		redisUtil.removeAll(CacheConstant.SYS_DICT_CACHE + "::" + dict.getDictCode());
	}

	/**
	 * AddDictionary
	 * @param dictName
	 */
	private String[] addDict(String dictName,String lowAppId, Integer tenantId) {
		SysDict dict = new SysDict();
		dict.setDictName(dictName);
		dict.setDictCode(RandomUtil.randomString(10));
		dict.setDelFlag(Integer.valueOf(CommonConstant.STATUS_0));
		dict.setLowAppId(lowAppId);
		dict.setTenantId(tenantId);
		baseMapper.insert(dict);
		String[] dictResult = new String[]{dict.getId(), dict.getDictCode()};
		return dictResult;
	}

	/**
	 * Add dictionary items
	 * @param id
	 * @param dictItemList
	 */
	private void addDictItem(String id,List<SysDictItem> dictItemList) {
		if(null!=dictItemList && dictItemList.size()>0){
			for (SysDictItem dictItem:dictItemList) {
				SysDictItem sysDictItem = new SysDictItem();
				BeanUtils.copyProperties(dictItem,sysDictItem);
				sysDictItem.setDictId(id);
				sysDictItem.setId("");
				sysDictItem.setStatus(Integer.valueOf(CommonConstant.STATUS_1));
				sysDictItemMapper.insert(sysDictItem);
			}
		}
	}

	/**
	 * Update dictionary child items
	 * @param id
	 * @param dictItemList
	 */
	private void updateDictItem(String id,List<SysDictItem> dictItemList){
		//Delete first and then add because the sorting may be inconsistent.
		LambdaQueryWrapper<SysDictItem> query = new LambdaQueryWrapper<>();
		query.eq(SysDictItem::getDictId,id);
		sysDictItemMapper.delete(query);
		//Add new subitem
		this.addDictItem(id,dictItemList);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void editValueTxn(String code, JSONObject data) {
		SysDict sysDict = getOne(new QueryWrapper<SysDict>().eq("dict_code", code));
		if (sysDict == null) {
			sysDict = new SysDict();
			sysDict.setDictName(code);
			sysDict.setDictCode(code);
			sysDict.setDelFlag(0);
			save(sysDict);
		}

		List<SysDictItem> batch = new ArrayList<>();
		for (String key : data.keySet()) {
			SysDictItem sysDictItem = sysDictItemService.getOne(
				new QueryWrapper<SysDictItem>().eq("item_value", key).eq("dict_id", sysDict.getId()));
			if (sysDictItem == null) {
				sysDictItem = new SysDictItem();
				sysDictItem.setDictId(sysDict.getId());
			}
			sysDictItem.setItemValue(key);
			String text = data.getStr(key);
			if ("ops_notify_email".equals(key)) {
				text = OpsNotifyEmailUtil.normalizeEmails(text);
			}
			sysDictItem.setItemText(text);
			batch.add(sysDictItem);
		}
		sysDictItemService.saveOrUpdateBatch(batch);
	}

	private void validateCustomDataSource(String code) {
		if (!CommonRoleGuard.hasAnyOwnerAdminRole()) {
			String uid = safeLoginId();
			log.warn("[SHARD-DICT-BLOCK] non-admin try customDataSource code={} user={}", code, uid);
			throw new JeecgBootException("未授权访问 customDataSource", CommonConstant.SC_INTERNAL_SERVER_ERROR_500);
		}
		SqlInjectionUtil.filterContentMulti(code);
		long n = 0;
		try {
		} catch (Exception e) {
			throw new JeecgBootException("dataSource 校验异常", CommonConstant.SC_INTERNAL_SERVER_ERROR_500);
		}
		if (n <= 0) {
			throw new JeecgBootException("dataSource code 不存在: " + code, CommonConstant.SC_INTERNAL_SERVER_ERROR_500);
		}
		log.info("[SHARD-DICT] admin user={}  use customDataSource code={}", safeLoginId(), code);
	}

	private String safeLoginId() {
		try {
			Object o = StpUtil.getLoginIdDefaultNull();
			return o == null ? "anon" : String.valueOf(o);
		} catch (Exception ignore) {
			return "anon";
		}
	}
}
