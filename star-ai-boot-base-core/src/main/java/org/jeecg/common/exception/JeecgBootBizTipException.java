package org.jeecg.common.exception;

import org.jeecg.common.constant.CommonConstant;

/**
 * @Description: Business tip exception (for operational business tips)
 * @date: 2024-04-26
 * @author: scott
 */
public class JeecgBootBizTipException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Error code returned to the frontend
	 */
	private int errCode = CommonConstant.SC_INTERNAL_SERVER_ERROR_500;

	public JeecgBootBizTipException(String message){
		super(message);
	}

	public JeecgBootBizTipException(String message, int errCode){
		super(message);
		this.errCode = errCode;
	}

	public int getErrCode() {
		return errCode;
	}

	public JeecgBootBizTipException(Throwable cause)
	{
		super(cause);
	}
	
	public JeecgBootBizTipException(String message, Throwable cause)
	{
		super(message,cause);
	}
}
