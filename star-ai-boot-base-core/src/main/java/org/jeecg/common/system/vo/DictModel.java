package org.jeecg.common.system.vo;

import java.io.Serializable;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: Dictionary
 * @author: jeecg-boot
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DictModel implements Serializable{
	private static final long serialVersionUID = 1L;

	public DictModel() {
	}
	
	public DictModel(String value, String text) {
		this.value = value;
		this.text = text;
	}

	public DictModel(String value, String text, String color) {
		this.value = value;
		this.text = text;
		this.color = color;
	}

	/**
	 * Dictionaryvalue
	 */
	private String value;
	/**
	 * Dictionary text
	 */
	private String text;
	/**
	 * Dictionary color
	 */
	private String color;

	/**
	 * Special purpose: JgEditableTable
	 * @return
	 */
	public String getTitle() {
		return this.text;
	}
	/**
	 * Special purpose: vue3 Select component
	 */
	public String getLabel() {
		return this.text;
	}


	/**
	 * For form designer: related record table data storage
	 * QQYUN-5595 [Form Designer] foreign-table field import is not translated
	 */
	private JSONObject jsonObject;

}
