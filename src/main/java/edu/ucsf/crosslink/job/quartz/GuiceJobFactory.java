package edu.ucsf.crosslink.job.quartz;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import com.google.inject.Inject;
import com.google.inject.Injector;

import edu.ucsf.crosslink.processor.controller.ProcessorControllerFactory;

public class GuiceJobFactory implements JobFactory {

	private static final Logger LOG = Logger.getLogger(GuiceJobFactory.class.getName());

	private final Injector guice;
	private final ProcessorControllerFactory processorControllerFactory;

	@Inject
	public GuiceJobFactory(Injector guice, ProcessorControllerFactory processorControllerFactory) {
		this.guice = guice;
		this.processorControllerFactory = processorControllerFactory;
	}
	
	public Job newJob(final TriggerFiredBundle bundle, final Scheduler schedular)
			throws SchedulerException {
		JobDetail jobDetail = bundle.getJobDetail();
		String jobName = jobDetail.getKey().getName();
		if (Quartz.META_JOB.equals(jobName)) {
			return guice.getInstance(MetaProcessorControllerJob.class);
		}
		else {
			try {
				return processorControllerFactory.getInjector(jobName).getInstance(ProcessorControllerJob.class);
			} 
			catch (Exception e) {
				LOG.log(Level.SEVERE, e.getMessage(), e);
				throw new SchedulerException(e);
			}
		}
	}
}
