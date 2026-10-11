package org.jeecg.modules.system.vo;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * @Description: SystemDepartmentVO
 * @author: jeecg-boot
 */
@Data
public class SysDepartUsersVO implements Serializable{
	private static final long serialVersionUID = 1L;
	
	/**Departmentid*/
	private String depId;
	/**Matching user id set*/
	private List<String> userIdList;
	public SysDepartUsersVO(String depId, List<String> userIdList) {
		super();
		this.depId = depId;
		this.userIdList = userIdList;
	}
    //update-begin--Author:kangxiaolin  Date:20190908 for: [512] fix add-existing-user under dept mgmt --------------------

	public SysDepartUsersVO(){

	}
    //update-begin--Author:kangxiaolin  Date:20190908 for: [512] fix add-existing-user under dept mgmt --------------------

}
