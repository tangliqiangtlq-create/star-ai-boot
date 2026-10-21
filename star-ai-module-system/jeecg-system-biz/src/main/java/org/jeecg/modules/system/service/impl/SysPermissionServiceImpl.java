package org.jeecg.modules.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.security.C3FieldReflectionInspector;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.common.util.security.LedgerWriteGuard;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.system.entity.SysPermission;
import org.jeecg.modules.system.entity.SysPermissionDataRule;
import org.jeecg.modules.system.mapper.SysPermissionMapper;
import org.jeecg.modules.system.mapper.SysRolePermissionMapper;
import org.jeecg.modules.system.model.TreeModel;
import org.jeecg.modules.system.service.ISysPermissionDataRuleService;
import org.jeecg.modules.system.service.ISysPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.*;

/**
 * <p>
 * Menu permission table service implementation
 * </p>
 *
 * @Author scott
 * @since 2018-12-21
 */
@Service
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission> implements ISysPermissionService {

	private static final Set<String> REQUIRED_FIELDS_FOR_EDIT =
		Set.of("name", "perms", "permsType", "icon", "component", "componentName", "url", "redirect", "sortNo", "menuType", "leaf", "route", "keepAlive", "description", "updateBy", "updateTime", "status", "alwaysShow", "internalOrExternal");

	private static final Set<String> REQUIRED_FIELDS_FOR_DELFLAG =
		Set.of("delFlag", "updateBy", "updateTime");

	@Resource
	private SysPermissionMapper sysPermissionMapper;
	
	@Resource
	private ISysPermissionDataRuleService permissionDataRuleService;

	@Resource
	private SysRolePermissionMapper sysRolePermissionMapper;



	@Override
	public void switchVue3Menu() {
		sysPermissionMapper.backupVue2Menu();
		sysPermissionMapper.changeVue3Menu();
	}

	@Override
	public List<TreeModel> queryListByParentId(String parentId) {
		return sysPermissionMapper.queryListByParentId(parentId);
	}

	/**
	  * Physical delete
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	@CacheEvict(value = CacheConstant.SYS_DATA_PERMISSIONS_CACHE,allEntries=true)
	public void deletePermission(String id) throws JeecgBootException {
		SysPermission sysPermission = this.getById(id);
		if(sysPermission==null) {
			throw new JeecgBootException("Menu info not found");
		}
		String pid = sysPermission.getParentId();
		if(oConvertUtils.isNotEmpty(pid)) {
			Long count = this.count(new QueryWrapper<SysPermission>().lambda().eq(SysPermission::getParentId, pid));
			if(count==1) {
				//If parent has no other children, mark parent as leaf
				this.sysPermissionMapper.setMenuLeaf(pid, 1);
			}
		}
		sysPermissionMapper.deleteById(id);
		// The node may be a child node but may also be the parent node of other nodes, so cascading deletion is required.
		this.removeChildrenBy(sysPermission.getId());
		//Association deletion
		Map map = new HashMap(5);
		map.put("permission_id",id);
		//Delete data rules
		this.deletePermRuleByPermId(id);
		//Delete role authorization table
		sysRolePermissionMapper.deleteByMap(map);
		// NOTE: cleaned - sys_depart_permission / sys_depart_role_permission tables removed
	}
	
	/**
	 * Delete related child nodes by parent id
	 * 
	 * @return
	 */
	public void removeChildrenBy(String parentId) {
		LambdaQueryWrapper<SysPermission> query = new LambdaQueryWrapper<>();
		// Encapsulates the query condition parentId as the primary key,
		query.eq(SysPermission::getParentId, parentId);
		// Find all children under the primary key
		List<SysPermission> permissionList = this.list(query);
		if (permissionList != null && permissionList.size() > 0) {
            // id
			String id = "";
            // The number of children detected
			Long num = Long.valueOf(0);
			// If the found set is not empty, delete all
			this.remove(query);
			// Then traverse the collection just found, and based on each object, find whether it still has children.
			for (int i = 0, len = permissionList.size(); i < len; i++) {
				id = permissionList.get(i).getId();
				Map map = new HashMap(5);
				map.put("permission_id",id);
				//Delete data rules
				this.deletePermRuleByPermId(id);
				//Delete role authorization table
				sysRolePermissionMapper.deleteByMap(map);
				// NOTE: cleaned - sys_depart_permission / sys_depart_role_permission tables removed
				num = this.count(new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getParentId, id));
				// If so, recurse
				if (num > 0) {
					this.removeChildrenBy(id);
				}
			}
		}
	}
	
	/**
	  * logical delete
	 */
	// C3-WHITELIST-CTRLLED: field-level only (not updateById whole entity)
	@Override
	@CacheEvict(value = CacheConstant.SYS_DATA_PERMISSIONS_CACHE,allEntries=true)
	//@CacheEvict(value = CacheConstant.SYS_DATA_PERMISSIONS_CACHE,allEntries=true,condition="#sysPermission.menuType==2")
	public void deletePermissionLogical(String id) throws JeecgBootException {
		SysPermission sysPermission = this.getById(id);
		if(sysPermission==null) {
			throw new JeecgBootException("Menu info not found");
		}
		String pid = sysPermission.getParentId();
		Long count = this.count(new QueryWrapper<SysPermission>().lambda().eq(SysPermission::getParentId, pid));
		if(count==1) {
			//If parent has no other children, mark parent as leaf
			this.sysPermissionMapper.setMenuLeaf(pid, 1);
		}
		LambdaUpdateWrapper<SysPermission> uw1 = Wrappers.lambdaUpdate();
		uw1.eq(SysPermission::getId, id)
		   .set(SysPermission::getDelFlag, 1)
		   .set(SysPermission::getUpdateBy, StpUtil.getLoginIdAsString())
		   .set(SysPermission::getUpdateTime, new Date());
		Set<String> actual1 = C3FieldReflectionInspector.extractSetFieldNames(uw1);
		if (!actual1.containsAll(REQUIRED_FIELDS_FOR_DELFLAG)) { Set<String> miss = new HashSet<>(REQUIRED_FIELDS_FOR_DELFLAG); miss.removeAll(actual1); throw new JeecgBootException("C3 BLOCK MISSING_WRITE_FIELDS table=sys_permission missing=" + miss); }
		// LedgerWriteGuard.assertLambdaUpdateWrapperFields("sys_permission", uw1, "update_time", "update_by"); // table not whitelisted in LedgerWriteGuard
		baseMapper.update(null, uw1);
	}

	@Override
	@CacheEvict(value = CacheConstant.SYS_DATA_PERMISSIONS_CACHE,allEntries=true)
	public void addPermission(SysPermission sysPermission) throws JeecgBootException {
		//----------------------------------------------------------------------
		//Determine whether it is a first-level menu. If so, clear the parent menu.
		if(CommonConstant.MENU_TYPE_0.equals(sysPermission.getMenuType())) {
			sysPermission.setParentId(null);
		}
		//----------------------------------------------------------------------
		String pid = sysPermission.getParentId();
		if(oConvertUtils.isNotEmpty(pid)) {
			//Configure parent node is not a leaf node
			this.sysPermissionMapper.setMenuLeaf(pid, 0);
		}
		sysPermission.setCreateTime(new Date());
		sysPermission.setDelFlag(0);
		sysPermission.setLeaf(true);
		this.save(sysPermission);
	}

	// C3-WHITELIST-CTRLLED: field-level only (not updateById whole entity)
	@Override
	@CacheEvict(value = CacheConstant.SYS_DATA_PERMISSIONS_CACHE,allEntries=true)
	public void editPermission(SysPermission sysPermission) throws JeecgBootException {
		SysPermission p = this.getById(sysPermission.getId());
		//TODO check whether node still has children
		if(p==null) {
			throw new JeecgBootException("Menu info not found");
		}else {
			Date now = new Date();
			sysPermission.setUpdateTime(now);
			//----------------------------------------------------------------------
			//Step1. Determine whether it is a first-level menu. If so, clear the parent menuID.
			if(CommonConstant.MENU_TYPE_0.equals(sysPermission.getMenuType())) {
				sysPermission.setParentId("");
			}
			//Step2.If menu has no children, mark as leaf
			Long count = this.count(new QueryWrapper<SysPermission>().lambda().eq(SysPermission::getParentId, sysPermission.getId()));
			if(count==0) {
				sysPermission.setLeaf(true);
			}
			//----------------------------------------------------------------------
			LambdaUpdateWrapper<SysPermission> uw2 = Wrappers.lambdaUpdate();
			uw2.eq(SysPermission::getId, sysPermission.getId())
			   .set(SysPermission::getParentId, sysPermission.getParentId())
			   .set(SysPermission::getName, sysPermission.getName())
			   .set(SysPermission::getPerms, sysPermission.getPerms())
			   .set(SysPermission::getPermsType, sysPermission.getPermsType())
			   .set(SysPermission::getIcon, sysPermission.getIcon())
			   .set(SysPermission::getComponent, sysPermission.getComponent())
			   .set(SysPermission::getComponentName, sysPermission.getComponentName())
			   .set(SysPermission::getUrl, sysPermission.getUrl())
			   .set(SysPermission::getRedirect, sysPermission.getRedirect())
			   .set(SysPermission::getSortNo, sysPermission.getSortNo())
			   .set(SysPermission::getMenuType, sysPermission.getMenuType())
			   .set(SysPermission::isLeaf, sysPermission.isLeaf())
			   .set(SysPermission::isRoute, sysPermission.isRoute())
			   .set(SysPermission::isKeepAlive, sysPermission.isKeepAlive())
			   .set(SysPermission::getDescription, sysPermission.getDescription())
			   .set(SysPermission::getRuleFlag, sysPermission.getRuleFlag())
			   .set(SysPermission::isHidden, sysPermission.isHidden())
			   .set(SysPermission::isHideTab, sysPermission.isHideTab())
			   .set(SysPermission::getStatus, sysPermission.getStatus())
			   .set(SysPermission::isAlwaysShow, sysPermission.isAlwaysShow())
			   .set(SysPermission::isInternalOrExternal, sysPermission.isInternalOrExternal())
			   .set(SysPermission::getUpdateBy, StpUtil.getLoginIdAsString())
			   .set(SysPermission::getUpdateTime, now);
			Set<String> actual2 = C3FieldReflectionInspector.extractSetFieldNames(uw2);
			if (!actual2.containsAll(REQUIRED_FIELDS_FOR_EDIT)) { Set<String> miss = new HashSet<>(REQUIRED_FIELDS_FOR_EDIT); miss.removeAll(actual2); throw new JeecgBootException("C3 BLOCK MISSING_WRITE_FIELDS table=sys_permission missing=" + miss); }
			// LedgerWriteGuard.assertLambdaUpdateWrapperFields("sys_permission", uw2, "update_time", "update_by"); // table not whitelisted in LedgerWriteGuard
			baseMapper.update(null, uw2);
			
			//If the parent menu of the current menu changes, you need to modify the status of the leaf nodes of the new parent menu and the old parent menu.
			String pid = sysPermission.getParentId();
            boolean flag = (oConvertUtils.isNotEmpty(pid) && !pid.equals(p.getParentId())) || oConvertUtils.isEmpty(pid)&&oConvertUtils.isNotEmpty(p.getParentId());
            if (flag) {
				//a.Configure the new parent menu is not a leaf node
				this.sysPermissionMapper.setMenuLeaf(pid, 0);
				//b.if old parent has no other children, mark as leaf
				Long cc = this.count(new QueryWrapper<SysPermission>().lambda().eq(SysPermission::getParentId, p.getParentId()));
				if(cc==0) {
					if(oConvertUtils.isNotEmpty(p.getParentId())) {
						this.sysPermissionMapper.setMenuLeaf(p.getParentId(), 1);
					}
				}
				
			}

		}
		
	}

	@Override
	public List<SysPermission> queryByUser(String userId) {
		List<SysPermission> permissionList = this.sysPermissionMapper.queryByUser(userId);
		//================= begin: when enabling tenant, default-add test role if missing================
		if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
			if (permissionList == null) {
				permissionList = new ArrayList<>();
			}
			List<SysPermission> testRoleList = sysPermissionMapper.queryPermissionByTestRoleId();
			//update-begin-author:liusq date:20230427 for: [QQYUN-5168]vue3 duplicate menus; dedupe by id
			for (SysPermission permission: testRoleList) {
				boolean hasPerm = permissionList.stream().anyMatch(a->a.getId().equals(permission.getId()));
				if(!hasPerm){
					permissionList.add(permission);
				}
			}
			//update-end-author:liusq date:20230427 for: [QQYUN-5168]vue3 duplicate menus; dedupe by id
		}
		//================= end: when enabling tenant, default-add test role if missing================
		return permissionList;
	}

	/**
	 * Delete related SysPermissionDataRule records by permissionId
	 */
	@Override
	public void deletePermRuleByPermId(String id) {
		LambdaQueryWrapper<SysPermissionDataRule> query = new LambdaQueryWrapper<>();
		query.eq(SysPermissionDataRule::getPermissionId, id);
		Long countValue = this.permissionDataRuleService.count(query);
		if(countValue > 0) {
			this.permissionDataRuleService.remove(query);	
		}
	}

	/**
	  * Get data-permission URLs with fuzzy match rules
	 */
	@Override
	@Cacheable(value = CacheConstant.SYS_DATA_PERMISSIONS_CACHE)
	public List<String> queryPermissionUrlWithStar() {
		return this.baseMapper.queryPermissionUrlWithStar();
	}

	@Override
	public boolean hasPermission(String username, SysPermission sysPermission) {
		int count = baseMapper.queryCountByUsername(username,sysPermission);
		if(count>0){
			return true;
		}else{
			return false;
		}
	}

	@Override
	public boolean hasPermission(String username, String url) {
		SysPermission sysPermission = new SysPermission();
		sysPermission.setUrl(url);
		int count = baseMapper.queryCountByUsername(username,sysPermission);
		if(count>0){
			return true;
		}else{
			return false;
		}
	}

	@Override
	public List<SysPermission> queryDepartPermissionList(String departId) {
		return sysPermissionMapper.queryDepartPermissionList(departId);
	}

	@Override
	public boolean checkPermDuplication(String id, String url,Boolean alwaysShow) {
		QueryWrapper<SysPermission> qw=new QueryWrapper();
		qw.lambda().eq(true,SysPermission::getUrl,url).ne(oConvertUtils.isNotEmpty(id),SysPermission::getId,id).eq(true,SysPermission::isAlwaysShow,alwaysShow);
		return count(qw)==0;
	}

}
