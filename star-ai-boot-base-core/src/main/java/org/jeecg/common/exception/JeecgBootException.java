package org.jeecg.common.exception;

import org.jeecg.common.constant.CommonConstant;

/**
 * @Description: jeecg-boot custom exception
 * @author: jeecg-boot
 */
public class JeecgBootException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Error code returned to the frontend
	 */
	private int errCode = CommonConstant.SC_INTERNAL_SERVER_ERROR_500;

	public JeecgBootException(String message){
		super(message);
	}

	public JeecgBootException(String message, int errCode){
		super(message);
		this.errCode = errCode;
	}

	public int getErrCode() {
		return errCode;
	}

	public JeecgBootException(Throwable cause)
	{
		super(cause);
	}
	
	public JeecgBootException(String message,Throwable cause)
	{
		super(message,cause);
	}
}
