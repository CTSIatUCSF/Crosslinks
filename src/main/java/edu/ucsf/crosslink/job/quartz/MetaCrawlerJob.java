package edu.ucsf.crosslink.job.quartz;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import java.util.logging.Logger;



import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;

import com.google.inject.Inject;

import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.crawler.AffiliationCrawlerFactory;


@DisallowConcurrentExecution
public class MetaCrawlerJob implements Job {
	
	private static final Logger LOG = Logger.getLogger(MetaCrawlerJob.class.getName());
	private static final String TRIGGER_PREFIX = "trigger";
	private static final String GROUP = "crawlers";

	private final Quartz quartz;
	private AffiliationCrawlerFactory factory;
	
	@Inject
	public MetaCrawlerJob(Quartz quartz, AffiliationCrawlerFactory factory) {
		this.quartz = quartz;
		this.factory = factory;
	}

	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		try {
		    for (AffiliationCrawler crawler : factory.getOldestCrawlers()) {
		    	if (!crawler.okToStart()) {
		    		// not necessary but this helps keep the scheduler free
		    		continue;
		    	}
		    	String affiliation = crawler.getAffiliationName();
			    JobDetail job = newJob(AffiliationCrawlerJob.class)
			        .withIdentity(affiliation, GROUP)
			        .build();
	
			    // Trigger the job to run once now, and to not re-fire if it can't run now
			    Trigger trigger = newTrigger()
			        .withIdentity(TRIGGER_PREFIX + affiliation, GROUP)
			        .startNow()
			        .withSchedule(simpleSchedule()
			        		.withMisfireHandlingInstructionNextWithRemainingCount()) 
			        .forJob(job)
			        .build();
	
			    LOG.info("Scheduling " + affiliation);
			    quartz.getScheduler().scheduleJob(job, trigger);
		    }
		}
		catch (Exception e) {
			throw new JobExecutionException(e);
		}
	}
}
