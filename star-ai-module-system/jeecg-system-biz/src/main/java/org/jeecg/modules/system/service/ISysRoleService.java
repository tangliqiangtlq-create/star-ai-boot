package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Param;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * <p>
 * Role table service
 * </p>
 *
 * @Author scott
 * @since 2018-12-19
 */
public interface ISysRoleService extends IService<SysRole> {
    /**
     * Query all roles (no tenant isolation)
     * @param page
     * @param role
     * @return
     */
    Page<SysRole> listAllSysRole(@Param("page") Page<SysRole> page, SysRole role);

    /**
     * Query whether role exists without tenant isolation
     *
     * @param roleCode
     * @return
     */
    SysRole getRoleNoTenant(@Param("roleCode") String roleCode);
    
    /**
     * Import Excel and verify roleCode uniqueness
     *
     * @param file
     * @param params
     * @return
     * @throws Exception
     */
    Result importExcelCheckRoleCode(MultipartFile file, ImportParams params) throws Exception;

    /**
     * DeleteRole
     * @param roleid
     * @return
     */
    public boolean deleteRole(String roleid);

    /**
     * Batch deleteRole
     * @param roleids
     * @return
     */
    public boolean deleteBatchRole(String[] roleids);

    /**
     * Check whether the role exists in the current tenant by role id and current tenant
     * @param id
     * @return
     */
    Long getRoleCountByTenantId(String id, Integer tenantId);

    /**
     * Verify whether role is admin
     * 
     * @param ids
     */
    void checkAdminRoleRejectDel(String ids);
}
