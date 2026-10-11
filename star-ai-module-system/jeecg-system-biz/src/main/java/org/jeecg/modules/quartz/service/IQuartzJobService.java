package org.jeecg.modules.quartz.service;

import java.util.List;

import org.jeecg.modules.quartz.entity.QuartzJob;
import org.quartz.SchedulerException;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: online scheduled task management
 * @Author: jeecg-boot
 * @Date: 2019-04-28
 * @Version: V1.1
 */
public interface IQuartzJobService extends IService<QuartzJob> {

    /**
     * Find scheduled job by class name
     * @param jobClassName class name
     * @return List<QuartzJob>
     */
	List<QuartzJob> findByJobClassName(String jobClassName);

    /**
     * Save scheduled job.
     * @param quartzJob
     * @return boolean
     */
	boolean saveAndScheduleJob(QuartzJob quartzJob);

    /**
     * Edit scheduled job
     * @param quartzJob
     * @return boolean
     * @throws SchedulerException
     */
	boolean editAndScheduleJob(QuartzJob quartzJob) throws SchedulerException;

    /**
     * Delete scheduled job.
     * @param quartzJob
     * @return boolean
     */
	boolean deleteAndStopJob(QuartzJob quartzJob);

    /**
     * Resume scheduled job
     * @param quartzJob
     * @return
     */
	boolean resumeJob(QuartzJob quartzJob);

	/**
	 * Execute scheduled job
	 * @param quartzJob
     * @throws Exception
	 */
	void execute(QuartzJob quartzJob) throws Exception;

	/**
	 * Pause job
	 * @param quartzJob
	 * @throws SchedulerException
	 */
	void pause(QuartzJob quartzJob);
}
