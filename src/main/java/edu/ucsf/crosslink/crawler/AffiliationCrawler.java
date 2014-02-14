package edu.ucsf.crosslink.crawler;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.joda.time.DateTime;
import org.joda.time.Minutes;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.io.DBModule;
import edu.ucsf.crosslink.model.Researcher;

public class AffiliationCrawler {

	private static final Logger LOG = Logger.getLogger(AffiliationCrawler.class.getName());

	private static Map<String, AffiliationCrawler> liveCrawlers = new HashMap<String, AffiliationCrawler>();
	
	private Status status = Status.IDLE;
	private Mode mode = Mode.ENABLED;
	private int saved = 0;
	private int skipped = 0;
	private int avoided = 0;
	private int currentErrorCount = 0;
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
		DISABLED, IDLE, GATHERING_URLS, READING_RESEARCHERS, PAUSED, ERROR, FINISHED;
	}
	
	public enum Mode {
		ENABLED, DISABLED, FORCED;
	}
	
	public static Collection<AffiliationCrawler> getLiveCrawlers() {
		return liveCrawlers.values();
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
		this.status = Mode.DISABLED.equals(this.mode) ? Status.DISABLED : Status.IDLE;
		liveCrawlers.put(affiliation, this);
	}
	
	@Inject
	public void setConfiguartion(@Named("errorsToAbort") Integer errorsToAbort, 
			@Named("pauseOnAbort") Integer pauseOnAbort,
			@Named("authorReadErrorThreshold") Integer authorReadErrorThreshold) {
		this.errorsToAbort = errorsToAbort;
		this.pauseOnAbort = pauseOnAbort;
		this.authorReadErrorThreshold = authorReadErrorThreshold;
	}
	
	public String toString() {
		// found is dynamic
		int remaining = reader.getRemainingAuthorsSize();
		return affiliation + " : " + status + " (" + 
				saved + ", " + skipped + ", " + avoided + ", " + currentErrorCount + ", " + remaining + ") => (saved, skipped, avoided, currentErrorCount, remaining) " +
				" (lastStart, lastStop, lastFinish) : (" + started + ", " + ended + ", " + dateLastCrawled() + ")";
	}
	
	public Date dateLastCrawled() {
		return store.dateOfLastCrawl();
	}
	
	public String getAffiliationName() {
		return affiliation;
	}
	
	private void clearCounts() {
		saved = 0;
		skipped = 0;
		avoided = 0;
		currentErrorCount = 0;
	}
	
	public Mode getMode() {
		return mode;
	}	
	
	public Status getStatus() {
		return status;
	}
	
	public String getLatestError() {
		return latestError;
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
		if (Status.DISABLED.equals(status)) {
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
	
			// read authors.  This this ugly 0ing mess
			currentErrorCount = 0;
			status = Status.READING_RESEARCHERS;
			for (Researcher author : reader.getAuthors()) {
				currentAuthor = author;
				if (store.skip(author.getURL())) {
					skipped++;
					LOG.info("Skipping recently processed author :" + author);						
				}
				else {
					try {
						Researcher details = parser.getAuthorFromHTML(author.getURL());
						if (details != null) {							
							author.merge(details);
							LOG.info("Saving author :" + author);						
							store.saveResearcher(author);
							saved++;
						}
						else {
							avoided++;
							LOG.info("Skipping " + author.getURL() + " because it does not appear to be a profile page");
						}
						// if we make it here, we've processed the author
						reader.removeAuthor(author);
					}
					catch (Exception e) {
						if (author != null) {
							author.registerReadException(e);
							if (author.getErrorCount() > authorReadErrorThreshold) {
								// assume this is a bad URL and just move on
								continue;
							}
						}
						// important that this next line work with author = null!
						latestError = "Issue with : " + author + " : " + e.getMessage();
						if (++currentErrorCount > errorsToAbort) {
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
	
}
