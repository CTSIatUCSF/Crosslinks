package edu.ucsf.crosslink.crawler;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.time.StopWatch;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlClient;
import net.sourceforge.sitemaps.UnknownFormatException;
import net.sourceforge.sitemaps.http.ProtocolException;

public abstract class SparqlCrawler extends Crawler {

	private static final Logger LOG = Logger.getLogger(SparqlCrawler.class.getName());

	private SparqlClient sparqlClient = null;
	
	private ExecutorService executiveService = null;
	
	private long readListTime = 0;
	private long readResearcherTime = 0;
	
	private int queueSize = 0;
	private int offset = 0;
	private int limit = 0;
	
	// remove harvester as required item
	protected SparqlCrawler(String name, Mode crawlingMode, CrosslinkPersistance store,
			SparqlClient sparqlClient, int threadCount, int limit) throws Exception {
		super(name, crawlingMode, store);
		this.sparqlClient = sparqlClient;
		this.limit = limit;

		// a negative value means run in process, 0 means umlimited
		if (threadCount >= 0) {
			executiveService = threadCount > 0 ? Executors.newFixedThreadPool(threadCount) : Executors.newCachedThreadPool();
		}
	}
	
	protected abstract String getSparqlQuery();
	
	protected abstract String getResearcherURI(QuerySolution qs);
	
	protected abstract boolean avoid(QuerySolution qs);
	
	protected abstract boolean skip(QuerySolution qs);
	
	private int collectResearcherURLs() throws UnknownHostException, MalformedURLException, UnknownFormatException, IOException, ProtocolException, InterruptedException {		
		final AtomicInteger added = new AtomicInteger();
		StopWatch sw = new StopWatch();
		sw.start();
		String query = getSparqlQuery();
		String finalQuery = limit > 0 ? String.format(query + " OFFSET %d LIMIT %d", offset, limit) : query;
		sparqlClient.select(finalQuery, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext() && !isPaused()) {				
					QuerySolution qs = rs.next();
					String personURI = getResearcherURI(qs);
					
					try {
						setLastFoundResearcher(personURI);
						if (avoid(qs)) {
							addAvoided(personURI);
						}
						else if (skip(qs)) {
							addSkip(personURI);
						}
						else {
							processResearcher(personURI);
						}
						added.incrementAndGet();
					}
					catch (Exception e) {
						setLatestError("Error collecting " + personURI + " " + e.getMessage(), e);
						LOG.log(Level.WARNING, "Error collecting " + personURI, e);
					}
				}	
			}
		});
		sw.stop();
		readListTime += sw.getTime();
		// if we are still finding researchers, then return true so we can some back for more
		return added.get();
    }
	
	protected SparqlClient getSparqlClient() {
		return sparqlClient;
	}
	
	protected abstract QueuedRunnable getResearcherProcessor(String researcherURI);
	
	private void processResearcher(String researcherURI) {
		QueuedRunnable processor = getResearcherProcessor(researcherURI);
		if (executiveService != null) {
			executiveService.submit(processor);
		}
		else {
			processor.run();
		}
	}

	public boolean crawl() throws InterruptedException, UnknownHostException, MalformedURLException, UnknownFormatException, IOException, ProtocolException {
		if (Mode.FORCED_NO_SKIP.equals(getMode())) {
			offset = 0;
		}

		int newlyFound = 0;
		do {
			LOG.info(getCounts());
			LOG.info(getRates());
			if (limit > 0 && queueSize > limit) {
				Thread.sleep(10000);
			}
			else {
				newlyFound = collectResearcherURLs();
				offset += limit;
			}
		}
		while (newlyFound > 0 && !isPaused());
		
		if (executiveService != null) {
			executiveService.shutdown();
			executiveService.awaitTermination(10, TimeUnit.MINUTES);
		}
		offset = 0;
		return false;
	}
	
	@Override
	public String getCounts() {
		return super.getCounts() + ", Queue " + queueSize + ", Limit = " + limit;
	}
	
	@Override
	public String getRates() {
		int saved = Math.max(1, getSavedCnt());
		return super.getRates() + 
				", Query/person : " + PeriodFormat.getDefault().print(new Period(readListTime/saved)) +
				", Read/person : " + PeriodFormat.getDefault().print(new Period(readResearcherTime/saved));
	}
	
	protected abstract class QueuedRunnable implements Runnable {
		private String researcherURI = null;
		private Researcher researcher = null;
		
		protected QueuedRunnable(String researcherURI) {
			queueSize++;
			this.researcherURI = researcherURI;
		}
		
		protected abstract Researcher timedRun(String researcherURI) throws Exception;
		
		public void run() {
			StopWatch sw = new StopWatch();
			sw.start();
			try {
				researcher = timedRun(researcherURI);
			}
			catch (Exception e) {
				setLatestError("Error processing " + researcherURI + " " + e.getMessage(), e);
				addError(researcherURI);
			}
			finally {
				queueSize--;
				sw.stop();
				readResearcherTime += sw.getTime();						
			}
		}
	}
	
}
