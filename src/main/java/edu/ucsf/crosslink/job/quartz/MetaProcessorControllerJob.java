package edu.ucsf.crosslink.job.quartz;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;

import com.google.inject.Inject;

import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.ProcessorControllerFactory;

@DisallowConcurrentExecution
public class MetaProcessorControllerJob implements Job {
	
	private static final Logger LOG = Logger.getLogger(MetaProcessorControllerJob.class.getName());
	private static final String TRIGGER_PREFIX = "trigger";
	private static final String GROUP = "controllers";
	private static int historyMaxSize = 100;

	private final Quartz quartz;
	private ProcessorControllerFactory factory;
	private static LinkedList<String> metaControllerHistory = new LinkedList<String>();
	
	public static List<String> getMetaControllerHistory() {
		return metaControllerHistory;
	}

	@Inject
	public MetaProcessorControllerJob(Quartz quartz, ProcessorControllerFactory factory) {
		this.quartz = quartz;
		this.factory = factory;
	}
	
	private boolean isScheduled(ProcessorController processorController) throws SchedulerException {
		for (JobKey key : quartz.getScheduler().getJobKeys(GroupMatcher.jobGroupEquals(GROUP))) {
			if (key.getName().equals(processorController.getName())) {
				return true;
			}
		}
		return false;
	}

	private boolean isExecuting(ProcessorController processorController) throws SchedulerException {
		for (JobExecutionContext context : quartz.getScheduler().getCurrentlyExecutingJobs()) {
			if (context.getJobDetail().getKey().getName().equals(processorController.getName())) {
				return true;
			}
		}
		return false;
	}

	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		try {
		    for (ProcessorController processorController : factory.getCrawlers()) {
		    	if (isScheduled(processorController) || isExecuting(processorController)) {
		    		continue;
		    	}
				synchronized (metaControllerHistory) {
					metaControllerHistory.addFirst("Scheduling " + processorController.getName() + " at " + DateFormat.getDateTimeInstance().format(new Date()));
					if (metaControllerHistory.size() > historyMaxSize) {
						metaControllerHistory.removeLast();
					}
				}
		    	String name = processorController.getName();
			    JobDetail job = newJob(ProcessorControllerJob.class)
			        .withIdentity(name, GROUP)
			        .build();
	
			    // Trigger the job to run now or as soon as possible
			    Trigger trigger = newTrigger()
			        .withIdentity(TRIGGER_PREFIX + name, GROUP)
			        .startNow()
			        .forJob(job)
			        .build();
	
			    LOG.info("Scheduling " + name);
			    quartz.getScheduler().scheduleJob(job, trigger);
		    }
		}
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new JobExecutionException(e);
		}
	}
}
