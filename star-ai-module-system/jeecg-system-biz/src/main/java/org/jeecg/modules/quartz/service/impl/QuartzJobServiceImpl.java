package org.jeecg.modules.quartz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.DateUtils;
import org.jeecg.modules.quartz.entity.QuartzJob;
import org.jeecg.modules.quartz.mapper.QuartzJobMapper;
import org.jeecg.modules.quartz.service.IQuartzJobService;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * @Description: online scheduled task management
 * @Author: jeecg-boot
 * @Date: 2019-04-28
 * @Version: V1.1
 */
@Slf4j
@Service
public class QuartzJobServiceImpl extends ServiceImpl<QuartzJobMapper, QuartzJob> implements IQuartzJobService {
	@Autowired
	private QuartzJobMapper quartzJobMapper;
	@Autowired
	private Scheduler scheduler;

	/**
	 * Job group for immediate execution
	 */
	private static final String JOB_TEST_GROUP = "test_group";

	@Override
	public List<QuartzJob> findByJobClassName(String jobClassName) {
		return quartzJobMapper.findByJobClassName(jobClassName);
	}

	/**
	 * Save and start scheduled job
	 */
	@Override
	@Transactional(rollbackFor = JeecgBootException.class)
	public boolean saveAndScheduleJob(QuartzJob quartzJob) {
		// DB setting update
		quartzJob.setDelFlag(CommonConstant.DEL_FLAG_0);
		boolean success = this.save(quartzJob);
		if (success) {
			if (CommonConstant.STATUS_NORMAL.equals(quartzJob.getStatus())) {
				// Add scheduled job
				this.schedulerAdd(quartzJob.getId(), quartzJob.getJobClassName().trim(), quartzJob.getCronExpression().trim(), quartzJob.getParameter());
			}
		}
		return success;
	}

	/**
	 * Resume scheduled job
	 */
	@Override
	@Transactional(rollbackFor = JeecgBootException.class)
	public boolean resumeJob(QuartzJob quartzJob) {
		schedulerDelete(quartzJob.getId());
		schedulerAdd(quartzJob.getId(), quartzJob.getJobClassName().trim(), quartzJob.getCronExpression().trim(), quartzJob.getParameter());
		quartzJob.setStatus(CommonConstant.STATUS_NORMAL);
		// C3-ALLOWED: INTERNAL_ONLY_NOT_USER_BODY
		return this.updateById(quartzJob);
	}

	/**
	 * Edit and enable/disable scheduled job
	 * @throws SchedulerException 
	 */
	@Override
	@Transactional(rollbackFor = JeecgBootException.class)
	public boolean editAndScheduleJob(QuartzJob quartzJob) throws SchedulerException {
		if (CommonConstant.STATUS_NORMAL.equals(quartzJob.getStatus())) {
			schedulerDelete(quartzJob.getId());
			schedulerAdd(quartzJob.getId(), quartzJob.getJobClassName().trim(), quartzJob.getCronExpression().trim(), quartzJob.getParameter());
		}else{
			scheduler.pauseJob(JobKey.jobKey(quartzJob.getId()));
		}
		// C3-ALLOWED: INTERNAL_ONLY_NOT_USER_BODY
		return this.updateById(quartzJob);
	}

	/**
	 * Delete and stop scheduled job
	 */
	@Override
	@Transactional(rollbackFor = JeecgBootException.class)
	public boolean deleteAndStopJob(QuartzJob job) {
		schedulerDelete(job.getId());
		boolean ok = this.removeById(job.getId());
		return ok;
	}

	@Override
	public void execute(QuartzJob quartzJob) throws Exception {
		String jobName = quartzJob.getJobClassName().trim();
		Date startDate = new Date();
		String ymd = DateUtils.date2Str(startDate,DateUtils.yyyymmddhhmmss.get());
		String identity =  jobName + ymd;
		// Execute once after 3 seconds
		// update-begin--author:sunjianlei ---- date:20210511--- for: Run scheduled job immediately; change delay from 3s to 0.1s -------
		startDate.setTime(startDate.getTime() + 100L);
		// update-end--author:sunjianlei ---- date:20210511--- for: Run scheduled job immediately; change delay from 3s to 0.1s -------
		// Define a Trigger
		SimpleTrigger trigger = (SimpleTrigger)TriggerBuilder.newTrigger()
				.withIdentity(identity, JOB_TEST_GROUP)
				.startAt(startDate)
				.build();
		// Build job info
		JobDetail jobDetail = JobBuilder.newJob(getClass(jobName).getClass()).withIdentity(identity).usingJobData("parameter", quartzJob.getParameter()).build();
		// Add trigger and jobDetail to scheduler
		scheduler.scheduleJob(jobDetail, trigger);
		// Start scheduler
		scheduler.start();
	}

	@Override
	@Transactional(rollbackFor = JeecgBootException.class)
	public void pause(QuartzJob quartzJob){
		schedulerDelete(quartzJob.getId());
		quartzJob.setStatus(CommonConstant.STATUS_DISABLE);
		// C3-ALLOWED: INTERNAL_ONLY_NOT_USER_BODY
		this.updateById(quartzJob);
	}

	/**
	 * Add scheduled job
	 *
	 * @param jobClassName
	 * @param cronExpression
	 * @param parameter
	 */
	private void schedulerAdd(String id, String jobClassName, String cronExpression, String parameter) {
		try {
			// Start scheduler
			scheduler.start();

			// Build job info
			JobDetail jobDetail = JobBuilder.newJob(getClass(jobClassName).getClass()).withIdentity(id).usingJobData("parameter", parameter).build();

			// Cron schedule builder (task execution time)
			CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(cronExpression);

			// Build new trigger from updated cronExpression
			CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(id).withSchedule(scheduleBuilder).build();

			scheduler.scheduleJob(jobDetail, trigger);
		} catch (SchedulerException e) {
			throw new JeecgBootException("Failed to create scheduled task", e);
		} catch (RuntimeException e) {
			throw new JeecgBootException(e.getMessage(), e);
		}catch (Exception e) {
			throw new JeecgBootException("Backend cannot find class name: " + jobClassName, e);
		}
	}

	/**
	 * Delete scheduled job
	 * 
	 * @param id
	 */
	private void schedulerDelete(String id) {
		try {
			scheduler.pauseTrigger(TriggerKey.triggerKey(id));
			scheduler.unscheduleJob(TriggerKey.triggerKey(id));
			scheduler.deleteJob(JobKey.jobKey(id));
		} catch (Exception e) {
			log.error("operation failed", e);
			throw new JeecgBootException("Failed to delete scheduled task");
		}
	}

	private static Job getClass(String classname) throws Exception {
		Class<?> class1 = Class.forName(classname);
		return (Job) class1.newInstance();
	}

}
