package org.jeecg.modules.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.system.vo.DictModelMany;
import org.jeecg.common.system.vo.DictQuery;
import org.jeecg.modules.system.entity.SysDict;
import org.jeecg.modules.system.model.DuplicateCheckVo;
import org.jeecg.modules.system.model.TreeSelectModel;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * DictionaryTable Mapper API
 * </p>
 *
 * @Author zhangweijian
 * @since 2018-12-28
 */
public interface SysDictMapper extends BaseMapper<SysDict> {
	
	/**
	 * Duplicate-check SQL
     * @param duplicateCheckVo
	 * @return
	 */
	@Deprecated
	public Long duplicateCheckCountSql(DuplicateCheckVo duplicateCheckVo);

    /**
     * Duplicate-check SQL
     * @param duplicateCheckVo
     * @return
     */
	@Deprecated
	public Long duplicateCheckCountSqlNoDataId(DuplicateCheckVo duplicateCheckVo);

    /**
     * ApproveDictionarycodeGetDictionaryData
     * @param code Dictionarycode
     * @return  List<DictModel>
     */
	public List<DictModel> queryDictItemsByCode(@Param("code") String code);

	/**
	 * Query active dictionary items
	 * @param code
	 * @return
	 */
	List<DictModel> queryEnableDictItemsByCode(@Param("code") String code);


	/**
	 * Get dictionary data by multiple dict codes
	 *
	 * @param dictCodeList
	 * @return
	 */
	public List<DictModelMany> queryDictItemsByCodeList(@Param("dictCodeList") List<String> dictCodeList);

    /**
     * ApproveDictionarycodeGetDictionaryData
     * @param code
     * @param key
     * @return
     */
	@Select("select s.item_text from sys_dict_item s where s.dict_id = (select id from sys_dict where dict_code = #{code} order by id limit 1) and s.item_value = #{key} order by s.id limit 1")
	// 2026-10-01: sys_dict/sys_dict_item are global lookups, NOT tenant-scoped; anonymous contexts
	// (MQ consumer SendEmailTopic.sendExceptionReport / unauthenticated calls) have no
	// TenantContext and C4TenantLineHandler fail-closes with C4_TENANT_CONTEXT_NULL, killing the
	// exception-report email. Tenant intercept is skipped for this method only.
	@InterceptorIgnore(tenantLine = "true")
	public String queryDictTextByKey(@Param("code") String code,@Param("key") String key);

	/**
	 * Translate display text by multiple dictionary codes
	 * @param dictCodeList multiple dictionary codes
	 * @param keys data list
	 * @return
	 */
	List<DictModelMany> queryManyDictByKeys(@Param("dictCodeList") List<String> dictCodeList, @Param("keys") List<String> keys);

	/**
	 * Query all system dictionary items
	 * @return
	 */
	public List<DictModelMany> queryAllDictItems(List<Integer> tenantIdList);
	
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
	
	/**
	  * Query tree by table name, display field, and storage field
	 * @param table
	 * @param text
	 * @param code
	 * @param pid
	 * @param hasChildField
     * @param query
     * @param pidField
	 * @return
	 */
	@Deprecated
	List<TreeSelectModel> queryTreeList(@Param("query") Map<String, String> query, @Param("table") String table, @Param("text") String text, @Param("code") String code,
										@Param("pidField") String pidField, @Param("pid") String pid, @Param("hasChildField") String hasChildField,
										@Param("converIsLeafVal") int converIsLeafVal);

	/**
	 * Delete
	 * @param id
	 */
	@Select("delete from sys_dict where id = #{id}")
	public void deleteOneById(@Param("id") String id);

	/**
	 * Query logically deleted data
	 * @return
	 */
	@Select("select * from sys_dict where del_flag = 1")
	public List<SysDict> queryDeleteList();

	/**
	 * Update status value
	 * @param delFlag
	 * @param id
	 */
	@Update("update sys_dict set del_flag = #{flag,jdbcType=INTEGER} where id = #{id,jdbcType=VARCHAR}")
	public void updateDictDelFlag(@Param("flag") int delFlag, @Param("id") String id);


	/**
	 * PaginationQueryDictionaryTableData
	 * @param page
	 * @param query
	 * @return
	 */
	@Deprecated
	public Page<DictModel> queryDictTablePageList(Page page, @Param("query") DictQuery query);


	/**
	 * Query dictionary table with filter conditions and pagination
	 * @param page
	 * @param table
	 * @param text
	 * @param code
	 * @return
	 */
	@Deprecated
	IPage<DictModel> queryPageTableDictWithFilter(Page<DictModel> page, @Param("table") String table, @Param("text") String text, @Param("code") String code);

	/**
	 * Query dictionary table with filter conditions; return all
	 * @param table
	 * @param text
	 * @param code
	 * @return
	 */
	@Deprecated
	List<DictModel> queryTableDictWithFilter(@Param("table") String table, @Param("text") String text, @Param("code") String code);

	/**
	 * Table-dict query with a single allowlisted equality condition; used by dictCode 4th segment
	 * (e.g. {@code dcs_currency,code,code,type='1'}). {@code filterColumn} must already be validated
	 * as a plain identifier and whitelisted; {@code filterValue} is bound as a parameter.
	 */
	List<DictModel> queryTableDictWithFilterAndColumn(
			@Param("table") String table,
			@Param("text") String text,
			@Param("code") String code,
			@Param("filterColumn") String filterColumn,
			@Param("filterValue") String filterValue);

	/**
	 * Query dictionary table data
	 * @param table table name
	 * @param text   display field name
	 * @param code   storage field name
	 * @param codeValues storage field values as IN query condition
	 * @return
	 */
	@Deprecated
	List<DictModel> queryTableDictByKeysAndFilterSql(@Param("table") String table, @Param("text") String text, @Param("code") String code,
													 @Param("codeValues") List<String> codeValues);

	/**
	 * Get dictionary list and details by app id
	 * @param lowAppId
	 * @param tenantId
	 * @return
	 */
	@InterceptorIgnore(tenantLine = "true")
    List<SysDict> getDictListByLowAppId(@Param("lowAppId") String lowAppId, @Param("tenantId") Integer tenantId);

	/**
	 * Query logically deleted data (by tenant id)
	 * @return
	 */
	@Select("select * from sys_dict where del_flag = 1 and tenant_id = #{tenantId}")
	List<SysDict> queryDeleteListBtTenantId(@Param("tenantId") Integer tenantId);
}
