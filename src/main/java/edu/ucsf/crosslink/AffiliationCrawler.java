package edu.ucsf.crosslink;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Minutes;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.author.Author;
import edu.ucsf.crosslink.author.AuthorParser;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.io.DBModule;
import edu.ucsf.crosslink.sitereader.SiteReader;

public class AffiliationCrawler {

	private static final Logger LOG = Logger.getLogger(AffiliationCrawler.class.getName());

	private static Map<String, AffiliationCrawler> liveCrawlers = new HashMap<String, AffiliationCrawler>();
	
	private String status = "Idle";
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
	
	public static Collection<AffiliationCrawler> getLiveCrawlers() {
		return liveCrawlers.values();
	}

	private static void showUse() {
		System.out.println("Pass in the name of the properties file");
	}	

	@Inject
	public AffiliationCrawler(@Named("Affiliation") String affiliation, SiteReader reader, AuthorParser parser, CrosslinkPersistance store) {
		this.affiliation = affiliation;
		this.reader = reader;
		this.parser = parser;
		this.store = store;
		liveCrawlers.put(affiliation, this);
	}
	
	@Inject
	public void setConfiguartion(@Named("errorsToAbort") Integer errorsToAbort, @Named("pauseOnAbort") Integer pauseOnAbort) {
		this.errorsToAbort = errorsToAbort;
		this.pauseOnAbort = pauseOnAbort;
	}
	
	public String toString() {
		// found is dynamic
		int found = reader.getAuthors().size();
		return affiliation + " : " + status + " (" + 
				saved + " + " + skipped + " + " + avoided + " + " + error + " of " + found + ") => (saved + skipped + avoided + error of found) " +
				" last crawled on " + dateLastCrawled();
	}
	
	public Date dateLastCrawled() {
		return store.dateOfLastCrawl();
	}
	
	public String getAffiliationName() {
		return affiliation;
	}
	
	public void crawl() throws Exception {
		// ugly, but it works
		if (status.startsWith("Aborting") && Minutes.minutesBetween(new DateTime(started), new DateTime()).getMinutes() < pauseOnAbort) {
			return;
		}
		started = new Date();
		// Now index the site
		store.start();
		status = "Gathering researcher URLs";
		try {
			reader.collectAuthorURLS();
			Collection<Author> authors = reader.getAuthors();
			status = "Reading researchers";
			LOG.info("Found " + authors.size() + " potential Profile pages for " + affiliation);
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
							status = "Aborting because we have " + error + " errors";
							throw new Exception(status);
						}
					}
				}
			}
		}
		finally {
			store.close();
		}
		status = "Finished";
		ended = new Date();
	}
}
