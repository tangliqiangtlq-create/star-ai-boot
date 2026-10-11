package org.jeecg.modules.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.constant.TenantConstant;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.aop.TenantLog;
import org.jeecg.modules.system.entity.SysPackPermission;
import org.jeecg.modules.system.entity.SysTenant;
import org.jeecg.modules.system.entity.SysTenantPack;
import org.jeecg.modules.system.entity.SysTenantPackUser;
import org.jeecg.modules.system.mapper.SysPackPermissionMapper;
import org.jeecg.modules.system.mapper.SysRoleMapper;
import org.jeecg.modules.system.mapper.SysTenantPackMapper;
import org.jeecg.modules.system.mapper.SysTenantPackUserMapper;
import org.jeecg.modules.system.service.ISysTenantPackService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description: Tenant product pack
 * @Author: jeecg-boot
 * @Date: 2022-12-31
 * @Version: V1.0
 */
@Service
public class SysTenantPackServiceImpl extends ServiceImpl<SysTenantPackMapper, SysTenantPack> implements ISysTenantPackService {

    @Autowired
    private SysTenantPackMapper sysTenantPackMapper;

    @Autowired
    private SysTenantPackUserMapper sysTenantPackUserMapper;

    @Autowired
    private SysPackPermissionMapper sysPackPermissionMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Override
    public void addPackPermission(SysTenantPack sysTenantPack) {
        sysTenantPackMapper.insert(sysTenantPack);
        String permissionIds = sysTenantPack.getPermissionIds();
        if (oConvertUtils.isNotEmpty(permissionIds)) {
            String packId = sysTenantPack.getId();
            String[] permissionIdArray = permissionIds.split(SymbolConstant.COMMA);
            for (String permissionId : permissionIdArray) {
                this.addPermission(packId, permissionId);
            }
        }

    }

    @Override
    public List<SysTenantPack> setPermissions(List<SysTenantPack> records) {
        for (SysTenantPack pack : records) {
            List<String> permissionIds = sysPackPermissionMapper.getPermissionsByPackId(pack.getId());
            if (null != permissionIds && permissionIds.size() > 0) {
                String ids = String.join(SymbolConstant.COMMA, permissionIds);
                pack.setPermissionIds(ids);
            }
        }
        return records;
    }

    @Override
    public void editPackPermission(SysTenantPack sysTenantPack) {
        //Database summary id
        List<String> oldPermissionIds = sysPackPermissionMapper.getPermissionsByPackId(sysTenantPack.getId());
        //The ID that needs to be modified is passed from the front desk
        String permissionIds = sysTenantPack.getPermissionIds();
        //If the menu id passed is empty, then all menus in the database will be deleted.
        if (oConvertUtils.isEmpty(permissionIds)) {
            this.deletePackPermission(sysTenantPack.getId(), null);
        } else if (oConvertUtils.isNotEmpty(permissionIds) && oConvertUtils.isEmpty(oldPermissionIds)) {
            //If the menu id passed is not empty but the menu id of the database is empty, then add
            this.addPermission(sysTenantPack.getId(), permissionIds);
        } else {
            //Both non-empty; compare then add/remove
            if (oConvertUtils.isNotEmpty(oldPermissionIds)) {
                //Find the difference between the new tenant ID and the original tenant ID and delete it
                List<String> permissionList = oldPermissionIds.stream().filter(item -> !permissionIds.contains(item)).collect(Collectors.toList());
                if (permissionList.size() > 0) {
                    for (String permission : permissionList) {
                        this.deletePackPermission(sysTenantPack.getId(), permission);
                    }
                }

                //Find the difference between the original menu id and the new menu id, and add a new one
                List<String> permissionAddList = Arrays.stream(permissionIds.split(SymbolConstant.COMMA)).filter(item -> !oldPermissionIds.contains(item)).collect(Collectors.toList());
                if (permissionAddList.size() > 0) {
                    for (String permission : permissionAddList) {
                        this.addPermission(sysTenantPack.getId(), permission);
                    }
                }
            }
        }
        sysTenantPackMapper.updateById(sysTenantPack);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTenantPack(String ids) {
        String[] idsArray = ids.split(SymbolConstant.COMMA);
        for (String id : idsArray) {
            this.deletePackPermission(id,null);
            //Delete users under product packages
            this.deletePackUser(id);
            sysTenantPackMapper.deleteById(id);
        }
    }

    @Override
    public void exitTenant(String tenantId, String userId) {
        this.getById(tenantId);
    }

    @Override
    public void addDefaultTenantPack(Integer tenantId) {
        ISysTenantPackService currentService = SpringContextUtils.getApplicationContext().getBean(ISysTenantPackService.class);
        // Create a tenant super administrator
        SysTenantPack superAdminPack = new SysTenantPack(tenantId, "Super administrator", TenantConstant.SUPER_ADMIN);
        
        //step.1 create tenant pack (super admin)
        LambdaQueryWrapper<SysTenantPack> query = new LambdaQueryWrapper<>();
        query.eq(SysTenantPack::getTenantId,tenantId);
        query.eq(SysTenantPack::getPackCode, TenantConstant.SUPER_ADMIN);
        SysTenantPack sysTenantPackSuperAdmin = currentService.getOne(query);
        String packId = "";
        if(null == sysTenantPackSuperAdmin){
            packId = currentService.saveOne(superAdminPack);
        }else{
            packId = sysTenantPackSuperAdmin.getId();
        }
        //step.1.2 supplement user-pack relations
        LoginUser sysUser =  (LoginUser) StpUtil.getSession().get("loginUser");
        SysTenantPackUser packUser = new SysTenantPackUser(tenantId, packId, sysUser.getId());
        packUser.setRealname(sysUser.getRealname());
        packUser.setPackName(superAdminPack.getPackName());
        currentService.savePackUser(packUser);
        
        //step.2 Create a tenant package (organization account administrator) and add personnel relationship data
        query.eq(SysTenantPack::getTenantId,tenantId);
        query.eq(SysTenantPack::getPackCode, TenantConstant.ACCOUNT_ADMIN);
        SysTenantPack sysTenantPackAccountAdmin = currentService.getOne(query);
        if(null == sysTenantPackAccountAdmin){
            // Create super administrator
            SysTenantPack accountAdminPack = new SysTenantPack(tenantId, "Organization account administrator", TenantConstant.ACCOUNT_ADMIN);
            currentService.saveOne(accountAdminPack);
        }

        //step.3 create tenant pack (org app admin)
        query.eq(SysTenantPack::getTenantId,tenantId);
        query.eq(SysTenantPack::getPackCode, TenantConstant.APP_ADMIN);
        SysTenantPack sysTenantPackAppAdmin = currentService.getOne(query);
        if(null == sysTenantPackAppAdmin){
            // Create super administrator
            SysTenantPack appAdminPack = new SysTenantPack(tenantId, "Organization application administrator", TenantConstant.APP_ADMIN);
            currentService.saveOne(appAdminPack);
        }
        
    }

    @TenantLog(2)
    @Override
    public String saveOne(SysTenantPack sysTenantPack) {
        sysTenantPackMapper.insert(sysTenantPack);
        return sysTenantPack.getId();
    }

    @TenantLog(2)
    @Override
    public void savePackUser(SysTenantPackUser sysTenantPackUser) {
        sysTenantPackUser.setStatus(1);
        sysTenantPackUserMapper.insert(sysTenantPackUser);
    }

    @Override
    public SysTenantPack getSysTenantPack(Integer tenantId, String packCode) {
        LambdaQueryWrapper<SysTenantPack> query = new LambdaQueryWrapper<SysTenantPack>()
                .eq(SysTenantPack::getPackCode, packCode)
                .eq(SysTenantPack::getTenantId, tenantId);
        List<SysTenantPack> list = baseMapper.selectList(query);
        if(list!=null && list.size()>0){
            SysTenantPack pack = list.get(0);
            if(pack!=null && pack.getId()!=null){
                return pack;
            }
        }
        return null;
    }

    /**
     * AddMenu
     *
     * @param packId
     * @param permissionId
     */
    public void addPermission(String packId, String permissionId) {
        SysPackPermission permission = new SysPackPermission();
        permission.setPermissionId(permissionId);
        permission.setPackId(packId);
        sysPackPermissionMapper.insert(permission);
    }

    /**
     * Delete relation table entry by pack id and menu id
     *
     * @param packId
     * @param permissionId
     */
    public void deletePackPermission(String packId, String permissionId) {
        LambdaQueryWrapper<SysPackPermission> query = new LambdaQueryWrapper<>();
        query.eq(SysPackPermission::getPackId, packId);
        if (oConvertUtils.isNotEmpty(permissionId)) {
            query.eq(SysPackPermission::getPermissionId, permissionId);
        }
        sysPackPermissionMapper.delete(query);
    }

    @Override
    public void addTenantDefaultPack(Integer tenantId) {
        LambdaQueryWrapper<SysTenantPack> query = new LambdaQueryWrapper<>();
        query.eq(SysTenantPack::getPackType,"default");
        List<SysTenantPack> sysTenantPacks = sysTenantPackMapper.selectList(query);
        for (SysTenantPack sysTenantPack: sysTenantPacks) {
            SysTenantPack pack = new SysTenantPack();
            BeanUtils.copyProperties(sysTenantPack,pack);
            pack.setTenantId(tenantId);
            pack.setPackType("custom");
            pack.setId("");
            sysTenantPackMapper.insert(pack);
            List<String> permissionsByPackId = sysPackPermissionMapper.getPermissionsByPackId(sysTenantPack.getId());
            for (String permission:permissionsByPackId) {
                SysPackPermission packPermission = new SysPackPermission();
                packPermission.setPackId(pack.getId());
                packPermission.setPermissionId(permission);
                sysPackPermissionMapper.insert(packPermission);
            }   
        }
    }

    /**
     * Delete users under a product pack.
     * @param packId
     */
    private void deletePackUser(String packId) {
        LambdaQueryWrapper<SysTenantPackUser> query = new LambdaQueryWrapper<>();
        query.eq(SysTenantPackUser::getPackId, packId);
        sysTenantPackUserMapper.delete(query);
    }
}
