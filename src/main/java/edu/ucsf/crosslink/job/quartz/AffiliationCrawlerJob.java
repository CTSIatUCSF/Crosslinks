package edu.ucsf.crosslink.job.quartz;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;

import com.google.inject.Inject;

import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.crawler.CrawlerStartStatus;

@DisallowConcurrentExecution
public class AffiliationCrawlerJob implements InterruptableJob {
	
	private static final Logger LOG = Logger.getLogger(AffiliationCrawlerJob.class.getName());
	private static LinkedList<String> crawlerHistory = new LinkedList<String>();
	private static int historyMaxSize = 100;

	private AffiliationCrawler crawler;
	private Thread currentExecutionThread;
	
	public static List<String> getCrawlerJobHistory() {
		return crawlerHistory;
	}

	@Inject
	public AffiliationCrawlerJob(AffiliationCrawler crawler) {
		this.crawler = crawler;
	}
	
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		boolean didSomething = false;
		try {
			currentExecutionThread = Thread.currentThread();
			CrawlerStartStatus startStatus = crawler.okToStart();
			if (startStatus.isOkToStart()) {
				didSomething = true;
				crawler.crawl(startStatus);
			}
		} 
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new JobExecutionException(e);
		}
		if (didSomething) {
			synchronized (crawlerHistory) {
				crawlerHistory.addFirst("" + new Date() + " -> " + crawler.toString());
				if (crawlerHistory.size() > historyMaxSize) {
					crawlerHistory.removeLast();
				}
			}
		}
		currentExecutionThread = null;
	}

	public void interrupt() throws UnableToInterruptJobException {
		if (currentExecutionThread != null) {
			currentExecutionThread.interrupt();
		}
		
	}

}
