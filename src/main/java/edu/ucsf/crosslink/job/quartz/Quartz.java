package edu.ucsf.crosslink.job.quartz;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.UnableToInterruptJobException;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;



import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.io.IOModule;
import edu.ucsf.crosslink.web.Stoppable;

@Singleton
public class Quartz implements Stoppable {

	private static final Logger LOG = Logger.getLogger(Quartz.class.getName());
	
	static final String META_JOB = "metaJob";
	private static final String GROUP = "meta";

	private final Scheduler scheduler;

	public static void main(String[] args) {
		try {
			Properties prop = new Properties();
			prop.load(AffiliationCrawler.class.getResourceAsStream(Crosslinks.PROPERTIES_FILE));			
			Quartz quartz = Guice.createInjector(new IOModule(prop), new QuartzModule(prop)).getInstance(Quartz.class);
			quartz.shutdown();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Inject
	public Quartz(final SchedulerFactory factory,
			final GuiceJobFactory jobFactory,
			@Named("scanInterval") Integer scanInterval) throws SchedulerException {
		scheduler = factory.getScheduler();
		scheduler.setJobFactory(jobFactory);
		
	    JobDetail job = newJob(MetaCrawlerJob.class)
		        .withIdentity(META_JOB, GROUP)
		        .build();

	    // Trigger the job to run now, and then repeat 
	    Trigger trigger = newTrigger()
	        .withIdentity("metaTrigger", GROUP)
	        .startNow()
	        .withSchedule(simpleSchedule()
	        		.withIntervalInSeconds(scanInterval)
	                .repeatForever()) 
	        .forJob(job)
	        .build();

	    scheduler.scheduleJob(job, trigger);

		scheduler.start();
	}

	public final Scheduler getScheduler() {
		return scheduler;
	}
	
	public void interrupt(String jobName) throws UnableToInterruptJobException {
		scheduler.interrupt(new JobKey(jobName, GROUP));
	}

	public void shutdown() {
		try {
			scheduler.shutdown();
		} catch (SchedulerException e) {
			// ... handle it
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}
	}

}
