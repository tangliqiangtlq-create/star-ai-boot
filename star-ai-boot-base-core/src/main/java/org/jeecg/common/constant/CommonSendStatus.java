package org.jeecg.common.constant;

/**
 * 	System notification - Release status
 * @Author LeeShaoQing
 *
 */
public interface CommonSendStatus {

    /**
     * Unpublished
     */
    public static final String UNPUBLISHED_STATUS_0 = "0";

    /**
     * Published
     */
	public static final String PUBLISHED_STATUS_1 = "1";

    /**
     * Revoke
     */
	public static final String REVOKE_STATUS_2 = "2";

    /**
     * App push session identifier suffix.
     */
	public static final String  APP_SESSION_SUFFIX = "_app";


	/**-----【Process related notification templatecode】------------------------------------------------------------*/
	/**Process reminder——System notification message template*/
	public static final String TZMB_BPM_CUIBAN = "bpm_cuiban";
	/**Process CC - System Notification Message Template*/
	public static final String TZMB_BPM_CC = "bpm_cc";
	/**Process reminder——Email notification message template*/
	public static final String TZMB_BPM_CUIBAN_EMAIL = "bpm_cuiban_email";
	/**Standard template—System messagenotify*/
	public static final String TZMB_SYS_TS_NOTE = "sys_ts_note";
	/**Process timeout reminder - system notification message template*/
	public static final String TZMB_BPM_CHAOSHI_TIP = "bpm_chaoshi_tip";
	/**-----【Process related notification templatecode】-----------------------------------------------------------*/

	/**
	 * System notification extension params (e.g. workflow CC/reminder route params)
	 */
	public static final String MSG_ABSTRACT_JSON = "msg_abstract";
}
