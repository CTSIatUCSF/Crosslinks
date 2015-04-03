package edu.ucsf.crosslink.job.quartz;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.UnableToInterruptJobException;
import org.quartz.impl.matchers.GroupMatcher;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.ProcessorControllerFactory;
import edu.ucsf.crosslink.web.Stoppable;

@Singleton
public class Quartz implements Runnable, Stoppable {

	private static final Logger LOG = Logger.getLogger(Quartz.class.getName());
	
	private static final String TRIGGER_PREFIX = "trigger";
	private static final String GROUP = "controllers";
	private static int historyMaxSize = 100;

	private ScheduledExecutorService configRefreshExecutors;
	private ExecutorService forcedExecutors;
	private Set<String> forcedProcessorControllers = new HashSet<String>();
	private final Scheduler scheduler;
	private ProcessorControllerFactory controllerFactory;

	private static LinkedList<String> metaControllerHistory = new LinkedList<String>();
	
	public static List<String> getMetaControllerHistory() {
		return metaControllerHistory;
	}
	
	@Inject
	public Quartz(final SchedulerFactory factory, final GuiceJobFactory jobFactory,
			@Named("scanInterval") Integer scanInterval, ProcessorControllerFactory controllerFactory) throws SchedulerException {
		scheduler = factory.getScheduler();
		scheduler.setJobFactory(jobFactory);		
		scheduler.start();

		this.controllerFactory = controllerFactory;
		
		// this thing should only ever need one thread
		// we run the config refresh outside of quartz so that it won't be blocked
		configRefreshExecutors = Executors.newSingleThreadScheduledExecutor();
		configRefreshExecutors.scheduleAtFixedRate(this, 0, scanInterval, TimeUnit.SECONDS);    	
		forcedExecutors = Executors.newCachedThreadPool();
	}

	public final Scheduler getScheduler() {
		return scheduler;
	}
	
	public void interrupt(String jobName) throws UnableToInterruptJobException {
		scheduler.interrupt(new JobKey(jobName, GROUP));
	}

	public void shutdown() {
		try {
			configRefreshExecutors.shutdownNow();
			scheduler.shutdown();		
			forcedExecutors.shutdown();
		} catch (SchedulerException e) {
			// ... handle it
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	private Set<String> getScheduledJobNames() throws SchedulerException {
		Set<String> retval = new HashSet<String>();
		for (JobKey key : getScheduler().getJobKeys(GroupMatcher.jobGroupEquals(GROUP))) {
			retval.add(key.getName());
		}
		return retval;
	}

	private Set<String> getExecutingJobNames() throws SchedulerException {
		Set<String> retval = new HashSet<String>();
		for (JobExecutionContext context : getScheduler().getCurrentlyExecutingJobs()) {
			retval.add(context.getJobDetail().getKey().getName());
		}
		return retval;
	}
	
	private boolean isScheduled(ProcessorController processorController) throws SchedulerException {
		for (JobKey key : getScheduler().getJobKeys(GroupMatcher.jobGroupEquals(GROUP))) {
			if (key.getName().equals(processorController.getName())) {
				return true;
			}
		}
		return false;
	}

	private boolean isExecuting(ProcessorController processorController) throws SchedulerException {
		for (JobExecutionContext context : getScheduler().getCurrentlyExecutingJobs()) {
			if (context.getJobDetail().getKey().getName().equals(processorController.getName())) {
				return true;
			}
		}
		return false;
	}

	public void run() {
		try {
			// pause all triggering of jobs
			getScheduler().pauseAll();
			
			// see if any are forced and not yet running and if so run those now
			// this is ugly with the set, fix that
		    for (ProcessorController processorController : controllerFactory.getCrawlers()) {
		    	if (processorController.isForced() && !forcedProcessorControllers.contains(processorController.getName())) {
		    		forcedExecutors.execute(processorController);
		    		forcedProcessorControllers.add(processorController.getName());
		    	}
		    	else {
		    		// they will eventually finish and go into unforced mode. 
		    		// this should clear them out
		    		forcedProcessorControllers.remove(processorController.getName());
		    	}
		    }			
		    
		    // now reload
			controllerFactory.loadNewCrawlers();
		    for (ProcessorController processorController : controllerFactory.getCrawlers()) {
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
			    getScheduler().scheduleJob(job, trigger);
		    }
		    getScheduler().resumeAll();
		}
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}
	}

}
