package edu.ucsf.crosslink.job.quartz;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
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
import edu.ucsf.crosslink.crawler.CrawlerStartStatus;

@DisallowConcurrentExecution
public class MetaCrawlerJob implements Job {
	
	private static final Logger LOG = Logger.getLogger(MetaCrawlerJob.class.getName());
	private static final String TRIGGER_PREFIX = "trigger";
	private static final String GROUP = "crawlers";
	private static int historyMaxSize = 100;

	private final Quartz quartz;
	private AffiliationCrawlerFactory factory;
	private static LinkedList<String> metaCrawlerHistory = new LinkedList<String>();
	
	public static List<String> getMetaCrawlerHistory() {
		return metaCrawlerHistory;
	}

	@Inject
	public MetaCrawlerJob(Quartz quartz, AffiliationCrawlerFactory factory) {
		this.quartz = quartz;
		this.factory = factory;
	}

	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		try {
		    for (AffiliationCrawler crawler : factory.getCrawlers()) {
		    	CrawlerStartStatus reason = crawler.okToStart();
				synchronized (metaCrawlerHistory) {
					metaCrawlerHistory.addFirst("Trigger status for " + crawler.getAffiliationName() + " : " + reason);
					if (metaCrawlerHistory.size() > historyMaxSize) {
						metaCrawlerHistory.removeLast();
					}
				}
		    	if (!reason.isOkToStart()) {
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
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new JobExecutionException(e);
		}
	}
}
