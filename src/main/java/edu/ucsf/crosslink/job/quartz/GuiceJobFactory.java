package edu.ucsf.crosslink.job.quartz;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import com.google.inject.Inject;

import edu.ucsf.crosslink.processor.controller.ProcessorControllerFactory;

public class GuiceJobFactory implements JobFactory {

	private static final Logger LOG = Logger.getLogger(GuiceJobFactory.class.getName());

	private final ProcessorControllerFactory processorControllerFactory;

	@Inject
	public GuiceJobFactory(ProcessorControllerFactory processorControllerFactory) {
		this.processorControllerFactory = processorControllerFactory;
	}
	
	public Job newJob(final TriggerFiredBundle bundle, final Scheduler schedular)
			throws SchedulerException {
		JobDetail jobDetail = bundle.getJobDetail();
		String jobName = jobDetail.getKey().getName();
		try {
			return processorControllerFactory.getInjector(jobName).getInstance(ProcessorControllerJob.class);
		} 
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new SchedulerException(e);
		}
	}
}
