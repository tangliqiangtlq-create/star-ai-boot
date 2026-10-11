package org.jeecg.modules.system.model;

import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @Title: DuplicateCheckVo
 * @Description: Duplicate-check VO
 * @Author Zhang Daihao
 * @Date 2019-03-25
 * @Version V1.0
 */
@Data
@ApiModel(value="Duplicate check data model",description="Duplicate check data model")
public class DuplicateCheckVo implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * Table name
	 */
	@ApiModelProperty(value="Table name",name="tableName",example="sys_log")
	private String tableName;
	
	/**
	 * Field name
	 */
	@ApiModelProperty(value="Field name",name="fieldName",example="id")
	private String fieldName;
	
	/**
	 * Field value
	 */
	@ApiModelProperty(value="Field value",name="fieldVal",example="1000")
	private String fieldVal;
	
	/**
	 * DataID
	*/
	@ApiModelProperty(value="DataID",name="dataId",example="2000")
	private String dataId;

}