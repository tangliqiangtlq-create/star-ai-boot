package org.jeecg.modules.system.service;

import org.jeecg.modules.system.entity.SysTenantPack;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.system.entity.SysTenantPackUser;

import java.util.List;

/**
 * @Description: Tenant product pack
 * @Author: jeecg-boot
 * @Date:   2022-12-31
 * @Version: V1.0
 */
public interface ISysTenantPackService extends IService<SysTenantPack> {

    /**
     * Add product pack and insert menus into relation table
     * @param sysTenantPack
     */
    void addPackPermission(SysTenantPack sysTenantPack);

    /**
     * Set menu id
     * @param records
     * @return
     */
    List<SysTenantPack> setPermissions(List<SysTenantPack> records);

    /**
     * Edit product pack and insert menus into relation table
     * @param sysTenantPack
     */
    void editPackPermission(SysTenantPack sysTenantPack);

    /**
     * Delete tenant product pack.
     * @param ids
     */
    void deleteTenantPack(String ids);

    /**
     * Leave tenant
     * @param tenantId
     * @param s
     */
    void exitTenant(String tenantId, String s);

    /**
     * Create 3 default product packs when creating a tenant.
     * @param tenantId
     */
    void addDefaultTenantPack(Integer tenantId);

    /**
     * Save product pack.
     * @param sysTenantPack
     */
    String saveOne(SysTenantPack sysTenantPack);


    /**
     * Save product pack to user relation.
     * @param sysTenantPackUser
     */
    void savePackUser(SysTenantPackUser sysTenantPackUser);

    /**
     * Query by tenant ID and code
     * @param tenantId
     * @param packCode
     * @return
     */
    SysTenantPack getSysTenantPack(Integer tenantId ,String packCode);
   
    /**
     * Add default product pack created by admin
     * @param id
     */
    void addTenantDefaultPack(Integer id);
}
