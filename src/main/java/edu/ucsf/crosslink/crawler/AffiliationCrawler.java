package edu.ucsf.crosslink.crawler;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.quartz.UnableToInterruptJobException;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.io.DBModule;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.quartz.Quartz;

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
	private String latestError = null;
	private Researcher currentAuthor = null;
	
	private Quartz quartz;
	private String jobName;
	
	// pass in the name of a configuration file
	public static void main(String[] args) {
		Researcher author = new Researcher("UCSF", "Meeks", "Eric", null, "http://profiles.ucsf.edu/eric.meeks", 
				"http://profiles.ucsf.edu/profile/Modules/CustomViewPersonGeneralInfo/PhotoHandler.ashx?NodeID=368698", null);
		try  {								
			// get these first
			Properties prop = new Properties();
			prop.load(AffiliationCrawler.class.getResourceAsStream(Crosslinks.PROPERTIES_FILE));	
			prop.load(new FileReader(new File(args[0])));
			AffiliationCrawler crawler = Guice.createInjector(new DBModule(prop), 
										new AffiliationCrawlerModule(prop)).getInstance(AffiliationCrawler.class);
			crawler.crawl();
		}
		catch (Exception e) {
			e.printStackTrace();
			showUse();
		}		
	}
	
	public enum Status {
		GATHERING_URLS, READING_RESEARCHERS, ERROR, PAUSED, FINISHED, IDLE;
	}
	
	public enum Mode {
		ENABLED, DISABLED, FORCED;
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
			@Named("authorReadErrorThreshold") Integer authorReadErrorThreshold) {
		this.errorsToAbort = errorsToAbort;
		this.pauseOnAbort = pauseOnAbort;
		this.authorReadErrorThreshold = authorReadErrorThreshold;
	}
	
	@Inject 
	public void setQuartzItems(Quartz quartz, @Named(Quartz.JOB_NAME) String jobName) {
		this.quartz = quartz;
		this.jobName = jobName;
	}
	
	public String toString() {
		return affiliation + " : " + getState() + " " + getCounts() + ", " + getDates(); 
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
		if (Status.GATHERING_URLS.equals(status) && Mode.DISABLED.equals(this.mode) && quartz != null) {
			// try and interrupt the job
			quartz.interrupt(this.jobName);
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
		return Arrays.asList(Status.GATHERING_URLS, Status.READING_RESEARCHERS).contains(status);
	}
	
	public boolean isOk() {
		return !Arrays.asList(Status.PAUSED, Status.ERROR).contains(status);
	}
	
	public Researcher getCurrentAuthor() {
		return currentAuthor;
	}

	public void crawl() throws Exception {
		// ugly, but it works
		// decisions about if we SHOULD crawl will be made by the job
		// decisions about it we CAN crawl can be made here
		if (Mode.FORCED.equals(mode)) {
			// don't leave in forced mode
			mode = Mode.ENABLED;
		}
		
		if (Mode.DISABLED.equals(mode)) {
			return;
		}
		else if (!isOk() && Minutes.minutesBetween(new DateTime(ended), new DateTime()).getMinutes() < pauseOnAbort) {
			return;
		}

		try {
			if (Arrays.asList(Status.IDLE, Status.FINISHED, Status.ERROR).contains(status)) {
				// fresh start
				clearCounts();
				started = new Date();
				// Now index the site
				store.start();
				status = Status.GATHERING_URLS;
				reader.collectAuthors();					
				LOG.info("Found " + reader.getAuthors().size() + " potential Profile pages for " + affiliation);
			}
			else {
				// we are resuming
				reader.purgeProcessedAuthors();
			}
	
			int currentErrorCnt = 0;
			status = Status.READING_RESEARCHERS;
			for (Researcher author : reader.getAuthors()) {
				if (Mode.DISABLED.equals(mode)) {
					status = Status.PAUSED;
					break;
				}
				currentAuthor = author;
				if (store.skip(author.getURL())) {
					skippedCnt++;
					reader.removeAuthor(author);
					LOG.info("Skipping recently processed author :" + author);						
				}
				else {
					try {
						Researcher details = parser.getAuthorFromHTML(author.getURL());
						if (details != null) {							
							author.merge(details);
							LOG.info("Saving author :" + author);						
							store.saveResearcher(author);
							savedCnt++;
						}
						else {
							avoided.add(author);
							LOG.info("Skipping " + author.getURL() + " because it does not appear to be a profile page");
						}
						// if we make it here, we've processed the author
						reader.removeAuthor(author);
					}
					catch (Exception e) {
						// see if it's likely to be a bad page
						if (isProbablyNotAProfilePage(author.getURL())) {
							avoided.add(author);
							LOG.log(Level.INFO, "Skipping " + author.getURL() + " because it does not appear to be a profile page", e);	
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
			if (Status.READING_RESEARCHERS.equals(status)) {
				store.finish();
				status = Status.FINISHED;
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
		if (Mode.FORCED.equals(mode)) {
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
	
	@Override
	public int compareTo(AffiliationCrawler o) {
		// TODO Auto-generated method stub
		return this.status == o.status ? this.getAffiliationName().compareTo(o.getAffiliationName()) : this.status.compareTo(o.status);
	}
	
	static class DateComparator implements Comparator<AffiliationCrawler> {
	    @Override
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
