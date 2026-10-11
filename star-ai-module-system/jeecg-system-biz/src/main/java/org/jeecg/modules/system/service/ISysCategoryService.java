package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.system.entity.SysCategory;
import org.jeecg.modules.system.model.TreeSelectModel;

import java.util.List;
import java.util.Map;

/**
 * @Description: Category dictionary
 * @Author: jeecg-boot
 * @Date:   2019-05-29
 * @Version: V1.0
 */
public interface ISysCategoryService extends IService<SysCategory> {

	/**Root node parent id value*/
	public static final String ROOT_PID_VALUE = "0";

    /**
     * Has child nodes
     */
    public static final String HAS_CHILD = "1";

    /**
     * Add category dictionary
     * @param sysCategory
     */
	void addSysCategory(SysCategory sysCategory);

    /**
     * Update category dictionary.
     * @param sysCategory
     */
	void updateSysCategory(SysCategory sysCategory);
	
	/**
     * Load category dictionary data by parent code
	 * @param pcode
	 * @return
     * @throws JeecgBootException
	 */
	public List<TreeSelectModel> queryListByCode(String pcode) throws JeecgBootException;
	
	/**
	  * Query child nodes by pid
	 * @param pid
	 * @return
	 */
	public List<TreeSelectModel> queryListByPid(String pid);

	/**
	 * Query child nodes by pid with optional filter conditions
	 * @param pid
	 * @param condition
	 * @return
	 */
	public List<TreeSelectModel> queryListByPid(String pid, Map<String,String> condition);

	/**
	 * BycodeQueryid
	 * @param code
	 * @return
	 */
	public String queryIdByCode(String code);

	/**
	 * When deleting a node, also delete child nodes and update the parent
	 * @param ids
	 */
	void deleteSysCategory(String ids);

	/**
	 * Category dictionary control data display [form page]
	 *
	 * @param ids
	 * @return
	 */
	List<String> loadDictItem(String ids);

	/**
	 * Category dictionary control data display [form page]
	 *
	 * @param ids
	 * @param delNotExist whether to remove non-existent items; if false, return the key itself when missing in DB
	 * @return
	 */
	List<String> loadDictItem(String ids, boolean delNotExist);

	/**
	 * [Import only] Reverse translation for category dictionary control
	 *
	 * @param names
	 * @param delNotExist whether to remove non-existent items; if false, return the key itself when missing in DB
	 * @return
	 */
	List<String> loadDictItemByNames(String names, boolean delNotExist);

}
