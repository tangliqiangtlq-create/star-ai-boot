package org.jeecg.modules.system.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.system.entity.SysDepart;
import org.jeecg.modules.system.entity.SysUserDepart;
import org.jeecg.modules.system.model.DepartIdModel;
import org.jeecg.modules.system.model.SysDepartTreeModel;
import org.jeecg.modules.system.vo.SysDepartExportVo;
import org.jeecg.modules.system.vo.lowapp.ExportDepartVo;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * <p>
 * DepartmentTable Service implementation
 * <p>
 * 
 * @Author:Steve
 * @Since：   2019-01-22
 */
public interface ISysDepartService extends IService<SysDepart>{

    /**
     * Query my department information, displayed by node
     * @param departIds department IDs
     * @return
     */
    List<SysDepartTreeModel> queryMyDeptTreeList(String departIds);

    /**
     * Query all department information, displayed by node
     * @return
     */
    List<SysDepartTreeModel> queryTreeList();


    /**
     * Query all department information by IDs, displayed by node
     * @param ids multiple department IDs
     * @return
     */
    List<SysDepartTreeModel> queryTreeList(String ids);

    /**
     * Query all department DepartId information, displayed by node
     * @return
     */
    public List<DepartIdModel> queryDepartIdTreeList();

    /**
     * Save department data.
     * @param sysDepart
     * @param username username
     */
    void saveDepartData(SysDepart sysDepart,String username);

    /**
     * Update depart data
     * @param sysDepart
     * @param username username
     * @return
     */
    Boolean updateDepartDataById(SysDepart sysDepart,String username);
    
    /**
     * DeletedepartData
     * @param id
     * @return
     */
	/* boolean removeDepartDataById(String id); */
    
    /**
     * Search related department data by keyword
     * @param keyWord
     * @param myDeptSearch
     * @param departIds multiple department IDs
     * @return
     */
    List<SysDepartTreeModel> searchByKeyWord(String keyWord,String myDeptSearch,String departIds);
    
    /**
     * Delete department by id and any child departments
     * @param id
     * @return
     */
    boolean delete(String id);
    
    /**
     * Query SysDepart collection
     * @param userId
     * @return
     */
	public List<SysDepart> queryUserDeparts(String userId);

    /**
     * Query department by username
     *
     * @param username
     * @return
     */
    List<SysDepart> queryDepartsByUsername(String username);
    
    /**
     * ByUserIDQueryDepartment
     *
     * @param userId
     * @return
     */
    List<String> queryDepartsByUserId(String userId);

	 /**
     * Batch delete departments by ids and any child departments
     * @param ids multiple department ids
     * @return
     */
	void deleteBatchWithChildren(List<String> ids);

    /**
     *  Query current and all sub-department IDs by department id
     * @param departId
     * @return
     */
    List<String> getSubDepIdsByDepId(String departId);

    /**
     * Get all sub-department IDs under my department
     * @param departIds multiple department ids
     * @return
     */
    List<String> getMySubDepIdsByDepId(String departIds);
    /**
     * Get department information by keyword (address book)
     * @param keyWord search term
     * @return
     */
    List<SysDepartTreeModel> queryTreeByKeyWord(String keyWord);
    /**
     * Get all sub-departments under my department
     * @param parentId parent id
     * @param ids multiple department ids
     * @param primaryKey primary key field (id or orgCode)
     * @return
     */
    List<SysDepartTreeModel> queryTreeListByPid(String parentId,String ids, String primaryKey);

    /**
     * Get all parent department ids for a department
     *
     * @param departId query by departId
     * @return JSONObject
     */
    JSONObject queryAllParentIdByDepartId(String departId);

    /**
     * Get all parent department ids for a department
     *
     * @param orgCode query by orgCode
     * @return JSONObject
     */
    JSONObject queryAllParentIdByOrgCode(String orgCode);
    /**
     * Get company info
     * @param orgCode department code
     * @return
     */
    SysDepart queryCompByOrgCode(String orgCode);
    /**
     * Get child departments
     * @param pid
     * @return
     */
    List<SysDepart> queryDeptByPid(String pid);

    /**
     * Get companies my department has joined
     * @return
     */
    List<SysDepart> getMyDepartList();

    /**
     * DeleteDepartment
     * @param id
     */
    void deleteDepart(String id);

    /**
     * Address book: query departments by tenant id
     * @param parentId
     * @param tenantId
     * @param departName
     * @return
     */
    List<SysDepartTreeModel> queryBookDepTreeSync(String parentId, Integer tenantId, String departName);

    /**
     * ByidQueryDepartmentInfo
     * @param parentId
     * @return
     */
    SysDepart getDepartById(String parentId);

    /**
     * ByidQueryDepartmentInfo
     * @param parentId
     * @return
     */
    IPage<SysDepart> getMaxCodeDepart(Page<SysDepart> page, String parentId);

    /**
     * Update leaf node
     * @param id
     * @param izLeaf
     */
    void updateIzLeaf(String id, Integer izLeaf);

    /**
     * Get export department data
     * @param tenantId
     * @return
     */
    List<ExportDepartVo> getExcelDepart(int tenantId);

    void importExcel(List<ExportDepartVo> listSysDeparts, List<String> errorMessageList);

    /**
     * Export departments by tenant id
     * @param tenantId
     * @return
     */
    List<SysDepartExportVo> getExportDepart(Integer tenantId);

    /**
     * Export system department Excel
     * @param listSysDeparts
     * @param errorMessageList
     */
    void importSysDepart(List<SysDepartExportVo> listSysDeparts, List<String> errorMessageList);
}
