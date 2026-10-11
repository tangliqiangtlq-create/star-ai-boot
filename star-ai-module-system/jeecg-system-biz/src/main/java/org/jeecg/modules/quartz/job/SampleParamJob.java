package org.jeecg.modules.quartz.job;

import org.jeecg.common.util.DateUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import lombok.extern.slf4j.Slf4j;

/**
 * Sample parameterized scheduled job
 * 
 * @Author Scott
 */
@Slf4j
public class SampleParamJob implements Job {

	/**
	 * If parameter variable names change, update QuartzJobController accordingly
	 */
	private String parameter;

	public void setParameter(String parameter) {
		this.parameter = parameter;
	}

	@Override
	public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
		log.info(" Job Execution key："+jobExecutionContext.getJobDetail().getKey());
		log.info( String.format("welcome %s! Jeecg-Boot parameterized scheduled task SampleParamJob !   Time:" + DateUtils.now(), this.parameter));
	}
}
