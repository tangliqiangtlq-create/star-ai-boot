package org.jeecg.common.exception;

/**
 * @Description: jeecg-boot custom SQL injection exception
 * @author: jeecg-boot
 */
public class JeecgSqlInjectionException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public JeecgSqlInjectionException(String message){
		super(message);
	}
	
	public JeecgSqlInjectionException(Throwable cause)
	{
		super(cause);
	}
	
	public JeecgSqlInjectionException(String message, Throwable cause)
	{
		super(message,cause);
	}
}
