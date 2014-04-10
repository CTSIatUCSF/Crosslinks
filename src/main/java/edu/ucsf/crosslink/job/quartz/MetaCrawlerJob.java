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

import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.crawler.AffiliationCrawlerFactory;
import edu.ucsf.crosslink.model.Affiliation;

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
	
	private boolean isScheduled(AffiliationCrawler crawler) throws SchedulerException {
		for (JobKey key : quartz.getScheduler().getJobKeys(GroupMatcher.jobGroupEquals(GROUP))) {
			if (key.getName().equals(crawler.getAffiliation().getName())) {
				return true;
			}
		}
		return false;
	}

	private boolean isExecuting(AffiliationCrawler crawler) throws SchedulerException {
		for (JobExecutionContext context : quartz.getScheduler().getCurrentlyExecutingJobs()) {
			if (context.getJobDetail().getKey().getName().equals(crawler.getAffiliation().getName())) {
				return true;
			}
		}
		return false;
	}

	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		try {
		    for (AffiliationCrawler crawler : factory.getCrawlers()) {
		    	if (isScheduled(crawler) || isExecuting(crawler)) {
		    		continue;
		    	}
				synchronized (metaCrawlerHistory) {
					metaCrawlerHistory.addFirst("Scheduling " + crawler.getAffiliation() + " at " + DateFormat.getDateTimeInstance().format(new Date()));
					if (metaCrawlerHistory.size() > historyMaxSize) {
						metaCrawlerHistory.removeLast();
					}
				}
		    	Affiliation affiliation = crawler.getAffiliation();
			    JobDetail job = newJob(AffiliationCrawlerJob.class)
			        .withIdentity(affiliation.getName(), GROUP)
			        .build();
	
			    // Trigger the job to run now or as soon as possible
			    Trigger trigger = newTrigger()
			        .withIdentity(TRIGGER_PREFIX + affiliation.getName(), GROUP)
			        .startNow()
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
