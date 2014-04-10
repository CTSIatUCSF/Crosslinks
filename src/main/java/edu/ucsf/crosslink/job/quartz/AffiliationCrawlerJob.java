package edu.ucsf.crosslink.job.quartz;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;

import com.google.inject.Inject;

import edu.ucsf.crosslink.crawler.AffiliationCrawler;

@DisallowConcurrentExecution
public class AffiliationCrawlerJob implements InterruptableJob {
	
	private static final Logger LOG = Logger.getLogger(AffiliationCrawlerJob.class.getName());

	private AffiliationCrawler crawler;
	private Thread currentExecutionThread;
	
	@Inject
	public AffiliationCrawlerJob(AffiliationCrawler crawler) {
		this.crawler = crawler;
	}
	
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		try {
			currentExecutionThread = Thread.currentThread();
			crawler.run();
		} 
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new JobExecutionException(e);
		}
		currentExecutionThread = null;
	}

	public void interrupt() throws UnableToInterruptJobException {
		if (currentExecutionThread != null) {
			currentExecutionThread.interrupt();
		}
		
	}

}
