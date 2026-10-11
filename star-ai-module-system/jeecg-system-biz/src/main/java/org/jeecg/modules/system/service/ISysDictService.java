package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.system.vo.DictQuery;
import org.jeecg.modules.system.entity.SysDict;
import org.jeecg.modules.system.entity.SysDictItem;
import org.jeecg.modules.system.model.DuplicateCheckVo;
import org.jeecg.modules.system.model.TreeSelectModel;
import org.jeecg.modules.system.vo.lowapp.SysDictVo;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * Dictionary table service
 * </p>
 *
 * @Author zhangweijian
 * @since 2018-12-28
 */
public interface ISysDictService extends IService<SysDict> {

	/**
	 *  Validate data availability; no duplicate records
	 *  
	 * @param duplicateCheckVo
	 * @return
	 */
	@Deprecated
	public boolean duplicateCheckData(DuplicateCheckVo duplicateCheckVo);

    /**
     * ApproveDictionarycodeGetDictionaryData
     * @param code
     * @return
     */
    public List<DictModel> queryDictItemsByCode(String code);

	/**
	 * Query active dictionary items
	 * @param code
	 * @return
	 */
	List<DictModel> queryEnableDictItemsByCode(String code);

	/**
	 * Get dictionary data by multiple dict codes
	 *
	 * @param dictCodeList
	 * @return key = dict code, value = corresponding dict options
	 */
	Map<String, List<DictModel>> queryDictItemsByCodeList(List<String> dictCodeList);

    /**
     * Load system dictionary on login
     * @return
     */
    public Map<String,List<DictModel>> queryAllDictItems();

    /**
     * Get dictionary by querying text and code of the specified table
     * @param tableFilterSql
     * @param text
     * @param code
     * @return
     */
    @Deprecated
    List<DictModel> queryTableDictItemsByCode(String tableFilterSql, String text, String code);

    /**
     * Query dict by table text/code with conditions
     * @param table
     * @param text
     * @param code
     * @return
     */
    @Deprecated
	public List<DictModel> queryTableDictItemsByCodeAndFilter(String table, String text, String code);

	/**
	 * Table-dict query with an optional single-column equality filter (dictCode 4th segment, e.g.
	 * {@code type='1'}). Unsupported filter syntaxes are ignored.
	 */
	public List<DictModel> queryTableDictItemsByCodeAndFilter(
			String table, String text, String code, String filter);

    /**
     * Get dict text by dict code and item value
     * @param code
     * @param key
     * @return
     */
    public String queryDictTextByKey(String code, String key);

	/**
	 * Translate display text by multiple dictionary codes
	 * @param dictCodeList multiple dictionary codes
	 * @param keys data list
	 * @return
	 */
	Map<String, List<DictModel>> queryManyDictByKeys(List<String> dictCodeList, List<String> keys);

    /**
     * Query dict value by table text/code/key
     * @param table
     * @param text
     * @param code
     * @param key
     * @return
     */
    @Deprecated
	String queryTableDictTextByKey(String table, String text, String code, String key);

	//update-begin---author:chenrui ---date:20231221  for: [issues/#5643] fix distributed cross-DB table-dict query ------------
	/**
	 * Get dictionary values by table text/code/key; supports batch query
	 *
	 * @param table
	 * @param text
	 * @param code
	 * @param keys
	 * @param dataSource data source
	 * @return
	 */
	List<DictModel> queryTableDictTextByKeys(String table, String text, String code, List<String> keys, String dataSource);
	//update-end---author:chenrui ---date:20231221  for: [issues/#5643] fix distributed cross-DB table-dict query ------------

    /**
     * Query dict value by table text/code/key including value
     * @param table table name
     * @param text
     * @param code
     * @param keys
     * @return
     */
	@Deprecated
	List<String> queryTableDictByKeys(String table, String text, String code, String keys);

    /**
     * Query dict value by table text/code/key including value
     * @param table
     * @param text
     * @param code
     * @param keys
     * @param delNotExist
     * @return
     */
	@Deprecated
	List<String> queryTableDictByKeys(String table, String text, String code, String keys,boolean delNotExist);

    /**
     * Delete corresponding data in relation table by dictionary type
     *
     * @param sysDict
     * @return
     */
    boolean deleteByDictId(SysDict sysDict);

    /**
     * Add one-to-many
     * @param sysDict
     * @param sysDictItemList
     * @return Integer
     */
    public Integer saveMain(SysDict sysDict, List<SysDictItem> sysDictItemList);

    /**
	 * Query all departments as dictionary: id --> value, departName --> text
	 * @return
	 */
	public List<DictModel> queryAllDepartBackDictModel();

	/**
	 * Query all users as dictionary: username --> value, realname --> text
	 * @return
	 */
	public List<DictModel> queryAllUserBackDictModel();

//	/**
//	 Query dictionary table by keyword
//	 * @param table
//	 * @param text
//	 * @param code
//	 * @param keyword
//	 * @return
//	 */
//	@Deprecated
//	public List<DictModel> queryTableDictItems(String table, String text, String code,String keyword);

	/**
	 * Query dictionary table data; return first 10 rows only
	 * @param table
	 * @param text
	 * @param code
	 * @param keyword
     * @param condition
     * @param pageSize page size
	 * @return
	 */
	@Deprecated
	public List<DictModel> queryLittleTableDictItems(String table, String text, String code, String condition, String keyword, int pageSize);

	/**
	 * Query all dictionary table data
	 * @param table
	 * @param text
	 * @param code
	 * @param condition
	 * @param keyword
	 * @return
	 */
	@Deprecated
	public List<DictModel> queryAllTableDictItems(String table, String text, String code, String condition, String keyword);
	/**
	  * Query tree by table name, display field, and storage field
	 * @param table
	 * @param text
	 * @param code
	 * @param pidField
	 * @param pid
	 * @param hasChildField
     * @param query
	 * @return
	 */
	@Deprecated
	List<TreeSelectModel> queryTreeList(Map<String, String> query,String table, String text, String code, String pidField,String pid,String hasChildField,int converIsLeafVal);

	/**
	 * Physical delete
	 * @param id
	 */
	public void deleteOneDictPhysically(String id);

	/**
	 * UpdatedelFlag
	 * @param delFlag
	 * @param id
	 */
	public void updateDictDelFlag(int delFlag,String id);

	/**
	 * Query logically deleted data
	 * @return
	 */
	public List<SysDict> queryDeleteList(String tenantId);

	/**
	 * PaginationQuery
	 * @param query
	 * @param pageSize
	 * @param pageNo
	 * @return
	 */
	@Deprecated
	public List<DictModel> queryDictTablePageList(DictQuery query,int pageSize, int pageNo);

    /**
     * Get dictionary data
     * @param dictCode dict code
     * @param dictCode table name, text field, code field | e.g. sys_user,realname,id
     * @return
     */
    List<DictModel> getDictItems(String dictCode);

    /**
     * [JSearchSelectTag dropdown search component API]
     * Large dictionary tables use async loading; the frontend filters data by input.
     *
     * @param dictCode dict code format: table,text,code
     * @param keyword
     * @param pageSize page size
     * @return
     */
    List<DictModel> loadDict(String dictCode, String keyword, Integer pageSize);

	/**
	 * Get dictionary list and details by app id
	 * @param lowAppId
	 * @return
	 */
	List<SysDictVo> getDictListByLowAppId(String lowAppId);

	/**
	 * Create dictionary
	 * @param sysDictVo
	 */
	String addDictByLowAppId(SysDictVo sysDictVo);

	/**
	 * EditDictionary
	 * @param sysDictVo
	 */
	void editDictByLowAppId(SysDictVo sysDictVo);

	void editValueTxn(String code, cn.hutool.json.JSONObject data);
}
