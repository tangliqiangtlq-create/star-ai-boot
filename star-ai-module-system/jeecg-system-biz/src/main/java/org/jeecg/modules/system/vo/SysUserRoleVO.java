package org.jeecg.modules.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Description: UserRolevo
 * @author: jeecg-boot
 */
@Data
public class SysUserRoleVO implements Serializable{
	private static final long serialVersionUID = 1L;

	/**Departmentid*/
	private String roleId;
	/**Matching user id set*/
	private List<String> userIdList;

	public SysUserRoleVO() {
		super();
	}

	public SysUserRoleVO(String roleId, List<String> userIdList) {
		super();
		this.roleId = roleId;
		this.userIdList = userIdList;
	}

}
