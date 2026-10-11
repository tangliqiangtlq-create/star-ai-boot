package org.jeecg.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.ImportExcelUtil;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.mapper.SysRoleMapper;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.jeecg.modules.system.service.ISysRoleService;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * Role table service implementation class
 * </p>
 *
 * @Author scott
 * @since 2018-12-19*/
@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements ISysRoleService {
    @Autowired
    SysRoleMapper sysRoleMapper;
    @Autowired
    SysUserMapper sysUserMapper;

    
    @Override
    public Page<SysRole> listAllSysRole(Page<SysRole> page, SysRole role) {
        List<String> roleCodes = null;
        if (role != null && role.getRoleCode() != null && role.getRoleCode().contains(",")) {
            roleCodes =
                Arrays.stream(role.getRoleCode().split(","))
                    .map(String::trim)
                    .filter(s -> s != null && !s.isEmpty())
                    .distinct()
                    .toList();
            // Avoid LIKE '%CHECKER,MACKER%' when multi-code is provided
            role.setRoleCode(null);
        }
        return page.setRecords(sysRoleMapper.listAllSysRole(page, role, roleCodes));
    }

    @Override
    public SysRole getRoleNoTenant(String roleCode) {
        return sysRoleMapper.getRoleNoTenant(roleCode);
    }

    @Override
    public Result importExcelCheckRoleCode(MultipartFile file, ImportParams params) throws Exception {
        List<Object> listSysRoles = ExcelImportUtil.importExcel(file.getInputStream(), SysRole.class, params);
        int totalCount = listSysRoles.size();
        List<String> errorStrs = new ArrayList<>();

        // Remove duplicate data in listSysRoles
        for (int i = 0; i < listSysRoles.size(); i++) {
            String roleCodeI =((SysRole)listSysRoles.get(i)).getRoleCode();
            for (int j = i + 1; j < listSysRoles.size(); j++) {
                String roleCodeJ =((SysRole)listSysRoles.get(j)).getRoleCode();
                // Duplicate data found
                if (roleCodeI.equals(roleCodeJ)) {
                    errorStrs.add("Row " + (j + 1) + " row roleCode value: " + roleCodeI + " already exists, import ignored");
                    listSysRoles.remove(j);
                    break;
                }
            }
        }
        // Remove duplicate data in sql
        Integer errorLines=0;
        Integer successLines=0;
        List<String> list = ImportExcelUtil.importDateSave(listSysRoles, ISysRoleService.class, errorStrs, CommonConstant.SQL_INDEX_UNIQ_SYS_ROLE_CODE);
         errorLines+=list.size();
         successLines+=(listSysRoles.size()-errorLines);
        return ImportExcelUtil.imporReturnRes(errorLines,successLines,list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRole(String roleid) {
        //1.delete role and user relationship
        sysRoleMapper.deleteRoleUserRelation(roleid);
        //2.delete roles and permissions relationships
        sysRoleMapper.deleteRolePermissionRelation(roleid);
        //3.delete role
        this.removeById(roleid);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatchRole(String[] roleIds) {
        //1.delete role and user relationship
        sysUserMapper.deleteBathRoleUserRelation(roleIds);
        //2.delete roles and permissions relationships
        sysUserMapper.deleteBathRolePermissionRelation(roleIds);
        //3.delete role
        this.removeByIds(Arrays.asList(roleIds));
        return true;
    }

    @Override
    public Long getRoleCountByTenantId(String id, Integer tenantId) {
        return sysRoleMapper.getRoleCountByTenantId(id,tenantId);
    }

    @Override
    public void checkAdminRoleRejectDel(String ids) {
        LambdaQueryWrapper<SysRole> query = new  LambdaQueryWrapper<>();
        query.in(SysRole::getId,Arrays.asList(ids.split(SymbolConstant.COMMA)));
        query.eq(SysRole::getRoleCode,"admin");
        Long adminRoleCount = sysRoleMapper.selectCount(query);
        if(adminRoleCount>0){
            throw new JeecgBootException("Admin role cannot be deleted!");
        }
    }
}
