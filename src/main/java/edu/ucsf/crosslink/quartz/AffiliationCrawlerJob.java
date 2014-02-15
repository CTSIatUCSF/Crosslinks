package edu.ucsf.crosslink.quartz;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.crawler.AffiliationCrawler;

@DisallowConcurrentExecution
public class AffiliationCrawlerJob implements InterruptableJob {
	
	private static final Logger LOG = Logger.getLogger(AffiliationCrawlerJob.class.getName());
	private static LinkedList<String> crawlerHistory = new LinkedList<String>();
	private static int historyMaxSize = 100;

	private AffiliationCrawler crawler;
	private int staleDays = 7;
	private Thread currentExecutionThread;
	
	public static List<String> getCrawlerJobHistory() {
		return crawlerHistory;
	}

	@Inject
	public AffiliationCrawlerJob(AffiliationCrawler crawler) {
		this.crawler = crawler;
	}

	@Inject
	public void setConfiguration(@Named("staleDays") Integer staleDays) {
		this.staleDays = staleDays;
	}	
	
	@Override
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		try {
			currentExecutionThread = Thread.currentThread();
			Date lastCrawled = crawler.getDateLastCrawled();

			// this is weird.  Need to either put this in the crawler or completely pull it out
			if (lastCrawled == null || AffiliationCrawler.Mode.FORCED.equals(crawler.getMode()) || 
					Days.daysBetween(new DateTime(lastCrawled), new DateTime()).getDays() > staleDays) {
				LOG.info("Starting to crawl " + crawler);
				crawler.crawl();
			}
			else {
				LOG.info("Skipping " + crawler + " because it was crawled on " + lastCrawled + " which is less than" + staleDays + " days ago");
			}
		} 
		catch (Exception e) {
			throw new JobExecutionException(e);
		}
		synchronized (crawlerHistory) {
			crawlerHistory.addFirst("" + new Date() + " -> " + crawler.toString());
			if (crawlerHistory.size() > historyMaxSize) {
				crawlerHistory.removeLast();
			}
		}
		currentExecutionThread = null;
	}

	@Override
	public void interrupt() throws UnableToInterruptJobException {
		if (currentExecutionThread != null) {
			currentExecutionThread.interrupt();
		}
		
	}

}
