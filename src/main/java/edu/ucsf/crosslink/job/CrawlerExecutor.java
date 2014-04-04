package edu.ucsf.crosslink.job;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.crawler.AffiliationCrawlerFactory;
import edu.ucsf.crosslink.web.Stoppable;

@Singleton
public class CrawlerExecutor implements Runnable, Stoppable {
	private static final Logger LOG = Logger.getLogger(CrawlerExecutor.class.getName());
	
	private AffiliationCrawlerFactory factory;
	ScheduledExecutorService executorService;
	
	@Inject
	public CrawlerExecutor(AffiliationCrawlerFactory factory, @Named("scanInterval") Integer scanInterval, @Named("threadCount") Integer threadCount) {
		this.factory = factory;
		
		// pass into some scheduled loader
		executorService = Executors.newScheduledThreadPool(threadCount, new ThreadFactory() {
			   public Thread newThread(Runnable runnable) {
			      Thread thread = Executors.defaultThreadFactory().newThread(runnable);
			      thread.setDaemon(true);
			      return thread;
			   }
			});
		
    	executorService.scheduleAtFixedRate(this, 0, scanInterval, TimeUnit.SECONDS);    	
	}

	public void run() {
		try {
		    for (AffiliationCrawler crawler : factory.getCrawlers()) {
		    	if (!crawler.okToStart().isOkToStart()) {
		    		// not necessary but this helps keep the scheduler free
		    		continue;
		    	}
		    	else {
		    		executorService.execute(new AffiliationCrawlerRunner(crawler));
		    	}
		    }
		}
		catch (Exception e) {
			LOG.log(Level.WARNING, e.getMessage(), e);
		}
	}
	
	public void shutdown() {
		executorService.shutdownNow();
	}
	
	private class AffiliationCrawlerRunner implements Runnable {
		private AffiliationCrawler crawler;
		
		private AffiliationCrawlerRunner(AffiliationCrawler crawler) {
			this.crawler = crawler;
		}
		
		public void run() {
			try {
				crawler.crawl(null);
			}
			catch (Exception e) {
				LOG.log(Level.WARNING, "Exception while crawling" + crawler, e);
			}
		}
	}

}
