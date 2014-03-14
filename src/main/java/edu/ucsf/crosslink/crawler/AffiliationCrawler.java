package edu.ucsf.crosslink.crawler;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import edu.ucsf.crosslink.model.Researcher;

public class AffiliationCrawler implements Comparable<AffiliationCrawler> {

	private static final Logger LOG = Logger.getLogger(AffiliationCrawler.class.getName());

	private Status status = Status.IDLE;
	private Mode mode = Mode.ENABLED;
	private int savedCnt = 0;
	private int skippedCnt = 0;
	private List<Researcher> avoided = new ArrayList<Researcher>();
	private List<Researcher> error = new ArrayList<Researcher>();
	private Date started;
	private Date ended;

	private String affiliation;
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
			crawler.crawl();
		}
		catch (Exception e) {
			e.printStackTrace();
			showUse();
		}		
	}
	
	public enum Status {
		GATHERING_URLS, READING_RESEARCHERS, VERIFY_PRIOR_RESEARCHERS, ERROR, PAUSED, FINISHED, IDLE;
	}
	
	public enum Mode {
		ENABLED, DISABLED, FORCED, FORCED_NO_SKIP, DEBUG;
	}
	
	private static void showUse() {
		System.out.println("Pass in the name of the properties file");
	}	

	public AffiliationCrawler(@Named("Affiliation") String affiliation, SiteReader reader, AuthorParser parser, CrosslinkPersistance store) {
		this(affiliation, reader, parser, store, Mode.ENABLED);
	}
			
	@Inject
	public AffiliationCrawler(@Named("Affiliation") String affiliation, SiteReader reader, AuthorParser parser, CrosslinkPersistance store,
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
	
	public String getAffiliationName() {
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

	public synchronized void crawl() throws Exception {
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
		}
	}
	
	public boolean okToStart() {
		if (Mode.DISABLED.equals(mode)) {
			return false;
		}
		else if (isActive()) {
			return false;
		}
		else if (!isOk() && Minutes.minutesBetween(new DateTime(ended), new DateTime()).getMinutes() < pauseOnAbort) {
			return false;
		}
		else if (isForced()) {
			return true;
		}
		else if (getDateLastCrawled() == null) {
			return true;
		}
		else {
			return Days.daysBetween(new DateTime(getDateLastCrawled()), new DateTime()).getDays() > staleDays;
		}
	}
	
	private void gatherURLs() throws Exception {
		// Now index the site
		status = Status.GATHERING_URLS;
		reader.collectAuthors();					
		LOG.info("Found " + reader.getAuthors().size() + " potential Profile pages for " + affiliation);		
	}
	
	private Set<String> touchResearchers() throws Exception {
		status = Status.VERIFY_PRIOR_RESEARCHERS;
		Set<String> touched = new HashSet<String>();
		if (Mode.DEBUG.equals(mode)) {
			return touched;
		}
		for (Researcher author : reader.getAuthors()) {
			if (Mode.DISABLED.equals(mode)) {
				status = Status.PAUSED;
				break;
			}
			// sort of ugly, but this will work with the DB store and not mess things up with the CSV store
			if (store.touch(author.getHomePageURL()) > 0) {
				touched.add(author.getHomePageURL());
			}
		}		
		return touched;
	}
	
	private boolean readResearchers(Set<String> knownReseacherURLs) {
		int currentErrorCnt = 0;
		status = Status.READING_RESEARCHERS;
		for (Researcher author : reader.getAuthors()) {
			if (Mode.DISABLED.equals(mode)) {
				status = Status.PAUSED;
				break;
			}
			currentAuthor = author;
			// do not skip any if we are in forced mode
			if (!Mode.FORCED_NO_SKIP.equals(mode) && store.skip(author.getHomePageURL())) {
				skippedCnt++;
				reader.removeAuthor(author);
				LOG.info("Skipping recently processed author :" + author);						
			}
			else {
				try {
					Researcher details = parser.getAuthorFromHTML(author.getHomePageURL());
					if (details != null) {							
						author.merge(details);
						LOG.info("Saving author :" + author);						
						store.saveResearcher(author);
						savedCnt++;
					}
					else {
						if (knownReseacherURLs.contains(author.getHomePageURL())) {
							throw new Exception("Error reading known researcher URL: " + author.getHomePageURL() );
						}
						else {
							avoided.add(author);
							LOG.info("Skipping " + author.getHomePageURL() + " because we could not read it's contents, and it is new to us");
						}
					}
					// if we make it here, we've processed the author
					reader.removeAuthor(author);
				}
				catch (Exception e) {
					// see if it's likely to be a bad page
					if (isProbablyNotAProfilePage(author.getHomePageURL())) {
						avoided.add(author);
						LOG.log(Level.INFO, "Skipping " + author.getHomePageURL() + " because it does not appear to be a profile page", e);	
						reader.removeAuthor(author);
						continue;
					}
					if (author != null) {
						error.add(author);
						author.registerReadException(e);
						if (author.getErrorCount() > authorReadErrorThreshold) {
							// assume this is a bad URL and just move on
							continue;
						}
					}
					// important that this next line work with author = null!
					latestError = "Issue with : " + author + " : " + e.getMessage();
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
		return this.status == o.status ? this.getAffiliationName().compareTo(o.getAffiliationName()) : this.status.compareTo(o.status);
	}
	
	static class DateComparator implements Comparator<AffiliationCrawler> {
	    public int compare(AffiliationCrawler a, AffiliationCrawler b) {
	    	Date aDate = a.getHowOld();
	    	Date bDate = b.getHowOld();
	    	if (aDate != null && bDate != null) {
	    		return aDate.compareTo(bDate);
	    	}
	    	else if (aDate == null) {
	    		return bDate == null ? 0 : -1;
	    	}
	    	else {
	    		return 1;
	    	}
	    }
	}
 
	
}
