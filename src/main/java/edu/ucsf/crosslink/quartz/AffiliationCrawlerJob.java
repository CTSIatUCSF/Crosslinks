package edu.ucsf.crosslink.quartz;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.AffiliationCrawler;

public class AffiliationCrawlerJob implements Job {
	
	private static final Logger LOG = Logger.getLogger(AffiliationCrawlerJob.class.getName());
	private static List<String> crawlerHistory = new ArrayList<String>();

	private AffiliationCrawler crawler;
	private int staleDays = 7;
	
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
			Date lastCrawled = crawler.dateLastCrawled();

			if (lastCrawled == null || Days.daysBetween(new DateTime(lastCrawled), new DateTime()).getDays() > staleDays) {
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
		crawlerHistory.add("" + new Date() + " -> " + crawler.toString());
	}

}
