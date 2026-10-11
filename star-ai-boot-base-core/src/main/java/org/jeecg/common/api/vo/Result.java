package org.jeecg.common.api.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.util.ApiOutputSanitizer;

/**
 *   Interface return data format
 * @author scott
 * @email jeecgos@163.com
 * @date January 19, 2019
 */
@Data
@ApiModel(value="APIReturnObject", description="APIReturnObject")
public class Result<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Success flag
	 */
	@ApiModelProperty(value = "Success flag")
	private boolean success = true;

	/**
	 * ReturnProcessMessage
	 */
	@ApiModelProperty(value = "ReturnProcessMessage")
	@Setter(AccessLevel.NONE)
	private String message = "";

	/**
	 * Return code
	 */
	@ApiModelProperty(value = "Return code")
	private Integer code = 0;
	
	/**
	 * Return data object (sanitized in setter to reduce XSS/JS hijack risk)
	 */
	@ApiModelProperty(value = "ReturnDataObject")
	@Setter(AccessLevel.NONE)
	private T result;
	
	/**
	 * Timestamp
	 */
	@ApiModelProperty(value = "Timestamp")
	private long timestamp = System.currentTimeMillis();

	public Result() {
	}

	public void setMessage(String message) {
		this.message = message == null ? "" : ApiOutputSanitizer.sanitizeForOutput(message);
	}

	public String getMessage() {
		return message;
	}

	public void setResult(T result) {
		this.result = ApiOutputSanitizer.sanitizeForOutput(result);
	}

	public T getResult() {
		return result;
	}

    /**
     * VUE3 compatibility: token expiry does not redirect to login page.
     * @param code
     * @param message
     */
	public Result(Integer code, String message) {
		this.code = code;
		this.setMessage(message);
	}
	
	public Result<T> success(String message) {
		this.setMessage(message);
		this.code = CommonConstant.SC_OK_200;
		this.success = true;
		return this;
	}

	public static<T> Result<T> ok() {
		Result<T> r = new Result<T>();
		r.setSuccess(true);
		r.setCode(CommonConstant.SC_OK_200);
		return r;
	}

	public static<T> Result<T> ok(String msg) {
		Result<T> r = new Result<T>();
		r.setSuccess(true);
		r.setCode(CommonConstant.SC_OK_200);
		//Result OK(String msg) causes compatibility issues issues/I4IP3D
		r.setResult((T) msg);
		r.setMessage(msg);
		return r;
	}

	public static<T> Result<T> ok(T data) {
		Result<T> r = new Result<T>();
		r.setSuccess(true);
		r.setCode(CommonConstant.SC_OK_200);
		r.setResult(data);
		return r;
	}

	public static<T> Result<T> OK() {
		Result<T> r = new Result<T>();
		r.setSuccess(true);
		r.setCode(CommonConstant.SC_OK_200);
		return r;
	}

	/**
	 * Created for upgrade compatibility
	 *
	 * @param msg
	 * @param <T>
	 * @return
	 */
	public static<T> Result<T> OK(String msg) {
		Result<T> r = new Result<T>();
		r.setSuccess(true);
		r.setCode(CommonConstant.SC_OK_200);
		r.setMessage(msg);
		//Result OK(String msg) causes compatibility issues issues/I4IP3D
		r.setResult((T) msg);
		return r;
	}

	public static<T> Result<T> OK(T data) {
		Result<T> r = new Result<T>();
		r.setSuccess(true);
		r.setCode(CommonConstant.SC_OK_200);
		r.setResult(data);
		return r;
	}

	public static<T> Result<T> OK(String msg, T data) {
		Result<T> r = new Result<T>();
		r.setSuccess(true);
		r.setCode(CommonConstant.SC_OK_200);
		r.setMessage(msg);
		r.setResult(data);
		return r;
	}

	public static<T> Result<T> error(String msg, T data) {
		Result<T> r = new Result<T>();
		r.setSuccess(false);
		r.setCode(CommonConstant.SC_INTERNAL_SERVER_ERROR_500);
		r.setMessage(msg);
		r.setResult(data);
		return r;
	}

	public static<T> Result<T> error(String msg) {
		return error(CommonConstant.SC_INTERNAL_SERVER_ERROR_500, msg);
	}
	
	public static<T> Result<T> error(int code, String msg) {
		Result<T> r = new Result<T>();
		r.setCode(code);
		r.setMessage(msg);
		r.setSuccess(false);
		return r;
	}

	public Result<T> error500(String message) {
		this.setMessage(message);
		this.code = CommonConstant.SC_INTERNAL_SERVER_ERROR_500;
		this.success = false;
		return this;
	}

	/**
	 * No-permission access response
	 */
	public static<T> Result<T> noauth(String msg) {
		return error(CommonConstant.SC_JEECG_NO_AUTHZ, msg);
	}

	@JsonIgnore
	private String onlTable;

}