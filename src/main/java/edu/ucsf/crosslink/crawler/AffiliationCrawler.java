package edu.ucsf.crosslink.crawler;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Minutes;
import org.quartz.UnableToInterruptJobException;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.io.IOModule;
import edu.ucsf.crosslink.job.quartz.AffiliationCrawlerJob;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;

public class AffiliationCrawler implements Comparable<AffiliationCrawler>, Runnable {

	private static final Logger LOG = Logger.getLogger(AffiliationCrawler.class.getName());

	private Status status = Status.IDLE;
	private Mode mode = Mode.ENABLED;
	private int savedCnt = 0;
	private int skippedCnt = 0;
	private List<Researcher> avoided = new ArrayList<Researcher>();
	private List<Researcher> error = new ArrayList<Researcher>();
	private Date started;
	private Date ended;
	private CrawlerStartStatus lastStartStatus;

	private Affiliation affiliation;
	private SiteReader reader;
	private AuthorParser parser;
	private CrosslinkPersistance store;
	
	private int errorsToAbort = 5;
	private int pauseOnAbort = 60;
	private int authorReadErrorThreshold = 3;
	private int staleDays = 7;
	private String latestError = null;
	private Researcher currentAuthor = null;
	
	private AffiliationCrawlerJob job;
	private Thread crawlingThread;
	
	public enum Status {
		GATHERING_URLS, READING_RESEARCHERS, VERIFY_PRIOR_RESEARCHERS, ERROR, PAUSED, FINISHED, IDLE;
	}
	
	public enum Mode {
		ENABLED, DISABLED, FORCED, FORCED_NO_SKIP, DEBUG;
	}
	
	private static void showUse() {
		System.out.println("Pass in the name of the properties file");
	}	

	@Inject
	public AffiliationCrawler(Affiliation affiliation, SiteReader reader, AuthorParser parser, CrosslinkPersistance store,
			Mode crawlingMode) {
		this.affiliation = affiliation;
		this.reader = reader;
		this.parser = parser;
		this.store = store;
		this.mode = crawlingMode;
		this.status = Status.IDLE;
	}
	
	@Inject
	public void setConfiguartion(@Named("errorsToAbort") Integer errorsToAbort, 
			@Named("pauseOnAbort") Integer pauseOnAbort,
			@Named("authorReadErrorThreshold") Integer authorReadErrorThreshold,
			@Named("staleDays") Integer staleDays) {
		this.errorsToAbort = errorsToAbort;
		this.pauseOnAbort = pauseOnAbort;
		this.authorReadErrorThreshold = authorReadErrorThreshold;
		this.staleDays = staleDays;
	}
	
	@Inject 
	public void setQuartzItems(AffiliationCrawlerJob job) {
		this.job = job;
	}
	
	public String toString() {
		return affiliation + " : " + getState() + " " + getCounts() + ", " + getDates() + " " + getDuration(); 
	}
	
	public String getCounts() {
		// found is dynamic
		int remaining = reader.getRemainingAuthorsSize();
		return "(saved, skipped, avoided, error, remaining) = > (" + savedCnt + ", " + skippedCnt + ", " + avoided.size() + ", " + error.size() + ", " + remaining + ")";
	}

	public String getDates() {
		// found is dynamic
		return "(lastStart, lastStop, lastFinish) : (" + started + ", " + ended + ", " + getDateLastCrawled() + ")";		
	}
	
	public String getDuration() {
		if (started == null) {
			return "";
		}
		else {
			return "" +  Minutes.minutesBetween(new DateTime(started), ended != null ? new DateTime(ended) : new DateTime()).getMinutes() + " minutes";
		}
	}
	
	public Date getDateLastCrawled() {
		return store.dateOfLastCrawl();
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
	}
	
	private void clearCounts() {
		savedCnt = 0;
		skippedCnt = 0;
		avoided.clear();
		error.clear();
	}
	
	public void setMode(String mode) throws UnableToInterruptJobException {
		this.mode = Mode.valueOf(mode);
		if (Status.GATHERING_URLS.equals(status) && Mode.DISABLED.equals(this.mode) && job != null) {
			// try and interrupt the job
			job.interrupt();
			this.status = Status.IDLE;
		}
		
	}
	
	public Mode getMode() {
		return mode;
	}	
	
	public Status getStatus() {
		return status;
	}
	
	public String getState() {
		return mode.toString() + " " + status.toString();
	}
	
	public String getLatestError() {
		return latestError;
	}
	
	public List<Researcher> getErrors() {
		return error;
	}
	
	public List<Researcher> getAvoided() {
		return avoided;
	}

	public boolean isActive() {
		return Arrays.asList(Status.GATHERING_URLS, Status.VERIFY_PRIOR_RESEARCHERS, Status.READING_RESEARCHERS).contains(status);
	}
	
	public boolean isOk() {
		return !Arrays.asList(Status.PAUSED, Status.ERROR).contains(status);
	}
	
	private boolean isForced() {
		return Arrays.asList(Mode.FORCED_NO_SKIP, Mode.FORCED).contains(mode);		
	}
	
	public Researcher getCurrentAuthor() {
		return currentAuthor;
	}

	public void run() {
		lastStartStatus = getStartStatus();
		if (!lastStartStatus.isOkToStart()) {
			return;
		}
		crawlingThread = Thread.currentThread();
		try {
			if (Arrays.asList(Status.IDLE, Status.FINISHED, Status.ERROR).contains(status)) {
				// fresh start
				clearCounts();
				started = new Date();
				gatherURLs();
				store.start();
			}
			else {
				// we are resuming
				reader.purgeProcessedAuthors();
			}
			
			// touch all the ones we have found.  This will make sure that we do not remove anyone that had been indexed before just due to an error in crawling their page
			Set<String> knownReseacherURLs = touchResearchers();
			
			if (readResearchers(knownReseacherURLs)) {
				store.finish();
				status = Status.FINISHED;
				if (isForced()) {
					// don't leave in forced mode
					mode = Mode.ENABLED;
				}				
			}	
		}
		catch (Exception e) {
			status = Status.ERROR;
			latestError = e.getMessage();
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}
		finally {
			store.close();
			ended = new Date();
			crawlingThread = null;
		}
	}
	
	public CrawlerStartStatus getLastStartStatus() {
		return lastStartStatus;
	}

	public String getCurrentStackTrace() {
		Thread activeThread = crawlingThread;
		StringWriter sw = new StringWriter();
		PrintWriter writer = new PrintWriter(sw);
		if (activeThread != null) {
			int i = 0;
			for(StackTraceElement ste : activeThread.getStackTrace()) {
				writer.println(ste.toString() + "<br/>");
				if (i++ > 30) {
					break;
				}
			}
		}
		writer.flush();
		return sw.toString();
	}

	private CrawlerStartStatus getStartStatus() {
		if (Mode.DISABLED.equals(mode)) {
			return new CrawlerStartStatus(false, "in mode " + mode.toString());
		}
		else if (isActive()) {
			return new CrawlerStartStatus(false, "currently active");
		}
		else if (!isOk()) {
			int minutesBetween = Minutes.minutesBetween(new DateTime(ended), new DateTime()).getMinutes();
			return new CrawlerStartStatus(minutesBetween > pauseOnAbort, "paused " + minutesBetween + " of " + pauseOnAbort + " minutes");
		}
		else if (isForced()) {
			return new CrawlerStartStatus(true, "isForced");
		}
		else if (getDateLastCrawled() == null) {
			return new CrawlerStartStatus(true, "never crawled before");
		}
		else {
			int daysBetween = Days.daysBetween(new DateTime(getDateLastCrawled()), new DateTime()).getDays();
			return  new CrawlerStartStatus(daysBetween > staleDays, "waited " + daysBetween + " of " + staleDays + " days");
		}
	}
	
	private void gatherURLs() throws Exception {
		// Now index the site
		status = Status.GATHERING_URLS;
		reader.collectResearchers();					
		LOG.info("Found " + reader.getReseachers().size() + " potential Profile pages for " + affiliation);		
	}
	
	private Set<String> touchResearchers() throws Exception {
		status = Status.VERIFY_PRIOR_RESEARCHERS;
		Set<String> touched = new HashSet<String>();
		if (Mode.DEBUG.equals(mode)) {
			return touched;
		}
		for (Researcher researcher : reader.getReseachers()) {
			if (Mode.DISABLED.equals(mode)) {
				status = Status.PAUSED;
				break;
			}
			// sort of ugly, but this will work with the DB store and not mess things up with the CSV store
			if (store.touch(researcher) > 0) {
				touched.add(researcher.getHomePageURL());
			}
		}		
		return touched;
	}
	
	private boolean readResearchers(Set<String> knownReseacherURLs) {
		int currentErrorCnt = 0;
		status = Status.READING_RESEARCHERS;
		for (Researcher researcher : reader.getReseachers()) {
			if (Mode.DISABLED.equals(mode)) {
				status = Status.PAUSED;
				break;
			}
			currentAuthor = researcher;
			// do not skip any if we are in forced mode
			if (!Mode.FORCED_NO_SKIP.equals(mode) && store.skip(researcher)) {
				skippedCnt++;
				reader.removeResearcher(researcher);
				LOG.info("Skipping recently processed author :" + researcher);						
			}
			else {
				try {
					if (parser.readResearcher(researcher)) {							
						LOG.info("Saving researcher :" + researcher);						
						store.saveResearcher(researcher);
						savedCnt++;
					}
					else {
						if (knownReseacherURLs.contains(researcher.getHomePageURL())) {
							throw new Exception("Error reading known researcher URL: " + researcher.getHomePageURL() );
						}
						else {
							avoided.add(researcher);
							LOG.info("Skipping " + researcher.getHomePageURL() + " because we could not read it's contents, and it is new to us");
						}
					}
					// if we make it here, we've processed the author
					reader.removeResearcher(researcher);
				}
				catch (Exception e) {
					// see if it's likely to be a bad page
					if (isProbablyNotAProfilePage(researcher.getHomePageURL())) {
						avoided.add(researcher);
						LOG.log(Level.INFO, "Skipping " + researcher.getHomePageURL() + " because it does not appear to be a profile page", e);	
						reader.removeResearcher(researcher);
						continue;
					}
					if (researcher != null) {
						error.add(researcher);
						researcher.registerReadException(e);
						if (researcher.getErrorCount() > authorReadErrorThreshold) {
							// assume this is a bad URL and just move on
							continue;
						}
					}
					// important that this next line work with author = null!
					latestError = "Issue with : " + researcher + " : " + e.getMessage();
					if (++currentErrorCnt > errorsToAbort) {
						status = Status.PAUSED;
						break;
					}
				}
			}
		}		
		// if we are still in this status, we are OK
		return Status.READING_RESEARCHERS.equals(status);
	}
	
	private static boolean isProbablyNotAProfilePage(String url) {
		String[] knownNonProfilePages = {"/search", "/about"};
		for (String knownNonProfilePage : knownNonProfilePages) {
			if (url.toLowerCase().contains(knownNonProfilePage + "/") || url.toLowerCase().endsWith(knownNonProfilePage)) {
				return true;
			}
		}
		return false;
	}
	
	public Date getHowOld() {
		if (isForced()) {
			return null; // act like you are brand new
		}
		else if (ended != null) {
			return ended;
		}
		else if (started != null) {
			return started;
		}
		else {
			return getDateLastCrawled();
		}
	}
	
	public int compareTo(AffiliationCrawler o) {
    	// always put active ones in the front
    	if (this.isActive() != o.isActive()) {
    		return this.isActive() ? -1 : 1;
    	}	    	

    	CrawlerStartStatus astatus = this.getLastStartStatus();
    	CrawlerStartStatus bstatus = o.getLastStartStatus();	    	
    	if (astatus != null && bstatus != null) {
    		return astatus.compareTo(bstatus);
    	}
    	else if (astatus == null) {
    		return bstatus == null ? 0 : -1;
    	}
    	else {
    		return 1;
    	}
    } 
	
	// pass in the name of a configuration file
	public static void main(String[] args) {
		try  {								
			// get these first
			Properties prop = new Properties();
			prop.load(AffiliationCrawler.class.getResourceAsStream(Crosslinks.PROPERTIES_FILE));	
			prop.load(new FileReader(new File(args[0])));
			Injector injector = Guice.createInjector(new IOModule(prop), new AffiliationCrawlerModule(prop));
			AffiliationCrawler crawler = injector.getInstance(AffiliationCrawler.class);		
			crawler.setMode("DEBUG");
			crawler.run();
		}
		catch (Exception e) {
			e.printStackTrace();
			showUse();
		}		
	}
	
}
