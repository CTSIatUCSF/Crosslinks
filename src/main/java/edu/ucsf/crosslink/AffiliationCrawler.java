package edu.ucsf.crosslink;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.Minutes;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.author.Author;
import edu.ucsf.crosslink.author.AuthorParser;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.io.DBModule;
import edu.ucsf.crosslink.sitereader.SiteReader;

public class AffiliationCrawler {

	private static final Logger LOG = Logger.getLogger(AffiliationCrawler.class.getName());

	private static Map<String, AffiliationCrawler> liveCrawlers = new HashMap<String, AffiliationCrawler>();
	
	private Status status = Status.IDLE;
	private int saved = 0;
	private int skipped = 0;
	private int avoided = 0;
	private int error = 0;
	private Date started;
	private Date ended;

	private String affiliation;
	private SiteReader reader;
	private AuthorParser parser;
	private CrosslinkPersistance store;
	
	private int errorsToAbort = 5;
	private int pauseOnAbort = 60;
	
	private List<Author> authors = new ArrayList<Author>();
	
	// pass in the name of a configuration file
	public static void main(String[] args) {
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
		DISABLED, IDLE, GATHERING_URLS, READING_RESEARCHERS, PAUSED, ERROR;
	}
	
	public static Collection<AffiliationCrawler> getLiveCrawlers() {
		return liveCrawlers.values();
	}

	private static void showUse() {
		System.out.println("Pass in the name of the properties file");
	}	

	public AffiliationCrawler(@Named("Affiliation") String affiliation, SiteReader reader, AuthorParser parser, CrosslinkPersistance store) {
		this(affiliation, reader, parser, store, true);
	}
			
	@Inject
	public AffiliationCrawler(@Named("Affiliation") String affiliation, SiteReader reader, AuthorParser parser, CrosslinkPersistance store,
			@Named("enableCrawling") Boolean enableCrawling) {
		this.affiliation = affiliation;
		this.reader = reader;
		this.parser = parser;
		this.store = store;
		this.status = enableCrawling ? Status.IDLE : Status.DISABLED;
		liveCrawlers.put(affiliation, this);
	}
	
	@Inject
	public void setConfiguartion(@Named("errorsToAbort") Integer errorsToAbort, @Named("pauseOnAbort") Integer pauseOnAbort) {
		this.errorsToAbort = errorsToAbort;
		this.pauseOnAbort = pauseOnAbort;
	}
	
	public String toString() {
		// found is dynamic
		int found = authors.size();
		return affiliation + " : " + status + " (" + 
				saved + " + " + skipped + " + " + avoided + " + " + error + " of " + found + ") => (saved + skipped + avoided + error of found) " +
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
		error = 0;
	}
	
	public Status getStatus() {
		return status;
	}
	
	public boolean isActive() {
		return Arrays.asList(Status.GATHERING_URLS, Status.READING_RESEARCHERS).contains(status);
	}
	
	public boolean isOk() {
		return !Arrays.asList(Status.PAUSED, Status.ERROR).contains(status);
	}

	public void crawl() throws Exception {
		// ugly, but it works
		// decisions about if we SHOULD crawl will be made by the job
		// decisions about it we CAN crawl can be made here
		if (!isOk() && Minutes.minutesBetween(new DateTime(ended), new DateTime()).getMinutes() < pauseOnAbort) {
			return;
		}

		try {
			if (Status.IDLE.equals(status)) {
				// fresh start
				clearCounts();
				started = new Date();
				// Now index the site
				store.start();
				status = Status.GATHERING_URLS;
				try {
					authors.clear();
					authors = reader.collectAuthors();
					LOG.info("Found " + authors.size() + " potential Profile pages for " + affiliation);
				}
				catch (Exception e) {
					status = Status.ERROR;
					LOG.log(Level.SEVERE, e.getMessage(), e);
					throw e;
				}
				finally {
					store.close();
				}
			}
	
			// read authors
			status = Status.READING_RESEARCHERS;
			for (Author author : authors) {
				if (store.skipAuthor(author.getURL())) {
					skipped++;
					LOG.info("Skipping recently processed author :" + author);						
				}
				else {
					try {
						Author details = parser.getAuthorFromHTML(author.getURL());
						if (details != null) {
							author.merge(details);
							LOG.info("Saving author :" + author);						
							store.saveAuthor(author);
							saved++;
						}
						else {
							avoided++;
							LOG.info("Skipping " + author.getURL() + " because it does not appear to be a profile page");
						}
					}
					catch (Exception e) {
						error++;
						LOG.log(Level.WARNING, "Error reading page for " + author.getURL(), e);
						if (error > errorsToAbort) {
							status = Status.PAUSED;
							throw new Exception(status.toString() + " because of too many errors: " + error);
						}
					}
				}
			}
			store.finish();
			status = Status.IDLE;
		}
		finally {
			ended = new Date();
		}
	}
		
}
