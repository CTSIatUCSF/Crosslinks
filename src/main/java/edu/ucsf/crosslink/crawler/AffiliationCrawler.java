package edu.ucsf.crosslink.crawler;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.jsoup.nodes.Document;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.io.IOModule;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;

public abstract class AffiliationCrawler extends Crawler {

	private static final Logger LOG = Logger.getLogger(AffiliationCrawler.class.getName());

	private int savedCnt = 0;
	private int skippedCnt = 0;
	private List<Researcher> researchers = new ArrayList<Researcher>();
	private List<Researcher> removeList = new ArrayList<Researcher>();	
	private Map<String, Long> recentlyProcessedAuthors = new HashMap<String, Long>();
	private Date started;
	private Date ended;

	private Affiliation affiliation;
	private SiteReader reader;
	private AuthorParser parser;
	private CrosslinkPersistance store;
	
	private int errorsToAbort = 5;
	private int authorReadErrorThreshold = 3;
	private int daysConsideredOld = 4;

	private static void showUse() {
		System.out.println("Pass in the name of the properties file");
	}	

	public AffiliationCrawler(Affiliation affiliation, Mode crawlingMode) {
		super(crawlingMode);
		this.affiliation = affiliation;
	}
	
	@Inject
	public void setConfiguartion(SiteReader reader, AuthorParser parser, CrosslinkPersistance store,
			@Named("errorsToAbort") Integer errorsToAbort, 
			@Named("authorReadErrorThreshold") Integer authorReadErrorThreshold,
			@Named("daysConsideredOld") Integer daysConsideredOld) throws Exception {
		this.reader = reader;
		this.parser = parser;
		this.store = store;
		this.errorsToAbort = errorsToAbort;
		this.authorReadErrorThreshold = authorReadErrorThreshold;
		this.daysConsideredOld = daysConsideredOld;
		store.upsertAffiliation(affiliation);		
	}
	
    public String getSiteRoot() {
    	return affiliation.getBaseURL();
    }
    
    protected Document getDocument(String url) throws IOException, InterruptedException {
    	return reader.getDocument(url);
    }

	public String getCounts() {
		// found is dynamic
		int remaining = getRemainingAuthorsSize();
		return super.getCounts() + ", Saved : " + savedCnt + ", Skipped : " + skippedCnt + ", Remaining : " +  remaining;
	}

	public String getDates() {
		// found is dynamic
		return super.getDates() + ", LastFinish : " + getDateLastCrawled();				
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
	}
	
	public Affiliation getHarvester() {
		return affiliation;
	}

	@Override
	protected void clear() {
		super.clear();
		savedCnt = 0;
		skippedCnt = 0;
	}
	
	public void crawl() {
		try {
			if (Arrays.asList(Status.IDLE, Status.FINISHED, Status.ERROR).contains(getStatus())) {
				// fresh start
				clear();
				started = new Date();
				gatherURLs();
				recentlyProcessedAuthors = store.startCrawl(getAffiliation());
			}
			else {
				// we are resuming
				purgeProcessedAuthors();
			}
			
			// touch all the ones we have found.  This will make sure that we do not remove anyone that had been indexed before just due to an error in crawling their page
			Set<String> knownReseacherURLs = touchResearchers();
			
			if (readResearchers(knownReseacherURLs)) {
				store.finishCrawl(getAffiliation());
				setStatus(Status.FINISHED);
			}	
		}
		catch (Exception e) {
			setStatus(Status.ERROR);
			setLatestError(e.getMessage());
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}
		finally {
			store.close();
			ended = new Date();
		}
	}
	
	private void gatherURLs() throws Exception {
		// Now index the site
		setStatus(Status.GATHERING_URLS);
    	researchers.clear();
    	removeList.clear();
    	collectResearcherURLs();
    	// dedupe, keep those with a name if you have a choice
    	Map<String, Researcher> rbyU = new HashMap<String, Researcher>();
    	for (Researcher r : researchers) {
    		if (rbyU.containsKey(r.getURI()) && r.getLabel() == null) {
    			continue;
    		}
    		rbyU.put(r.getURI(), r);
    	}
    	researchers.clear();
    	researchers.addAll(rbyU.values());
    	Collections.sort(researchers);
		LOG.info("Found " + getResearchers().size() + " potential Profile pages for " + affiliation);		
	}

    protected abstract void collectResearcherURLs() throws Exception;
    
    protected void addResearcher(Researcher researcher) {
    	researchers.add(researcher);
    }
    
    public void removeResearcher(Researcher researcher) {
    	removeList.add(researcher);
    }
    
    public List<Researcher> getResearchers() {
    	return researchers;
    }
        
    public int getRemainingAuthorsSize() {
    	return researchers.size() - removeList.size();
    }
        
    public void purgeProcessedAuthors() {
    	researchers.removeAll(removeList);
    	removeList.clear();
    }    
    
	private Set<String> touchResearchers() throws Exception {
		setStatus(Status.VERIFY_PRIOR_RESEARCHERS);
		Set<String> touched = new HashSet<String>();
		if (Mode.DEBUG.equals(getMode())) {
			return touched;
		}
		int cnt = 0;
		store.startTransaction();
		for (Researcher researcher : getResearchers()) {
			if (Mode.DISABLED.equals(getMode())) {
				setStatus(Status.PAUSED);
				break;
			}
			// sort of ugly, but this will work with the DB store and not mess things up with the CSV store
			if (store.touch(researcher) > 0) {
				touched.add(researcher.getURI());
			}
			LOG.info("Touch " + researcher + " " + cnt++);
		}		
		store.endTransaction();
		return touched;
	}
	
	private boolean readResearchers(Set<String> knownReseacherURLs) {
		int currentErrorCnt = 0;
		setStatus(Status.READING_RESEARCHERS);
		for (Researcher researcher : getResearchers()) {
			if (Mode.DISABLED.equals(getMode())) {
				setStatus(Status.PAUSED);
				break;
			}
			setCurrentAuthor(researcher);
			
			// do not skip any if we are in forced mode
			Long ts = recentlyProcessedAuthors.get(researcher.getURI());
			if (!Mode.FORCED_NO_SKIP.equals(getMode()) && 
					((ts != null && ts > new DateTime().minusDays(daysConsideredOld).getMillis()) || store.skip(researcher))) {
				skippedCnt++;
				removeResearcher(researcher);
				LOG.info("Skipping recently processed author :" + researcher);						
			}
			else {
				try {
					if (parser.readResearcher(researcher)) {							
						LOG.info("Saving researcher :" + researcher);						
						store.saveResearcher(researcher);
						savedCnt++;
						// add to processed list
						recentlyProcessedAuthors.put(researcher.getURI(), new Date().getTime());
					}
					else {
						if (knownReseacherURLs.contains(researcher.getURI())) {
							throw new Exception("Error reading known researcher URL: " + researcher.getURI() );
						}
						else {
							addAvoided(researcher);
							LOG.info("Skipping " + researcher.getURI() + " because we could not read it's contents, and it is new to us");
						}
					}
					// if we make it here, we've processed the author
					removeResearcher(researcher);
				}
				catch (Exception e) {
					// see if it's likely to be a bad page
					if (isProbablyNotAProfilePage(researcher.getURI())) {
						addAvoided(researcher);
						LOG.log(Level.INFO, "Skipping " + researcher.getURI() + " because it does not appear to be a profile page", e);	
						removeResearcher(researcher);
						continue;
					}
					if (researcher != null) {
						addError(researcher);
						researcher.registerReadException(e);
						if (researcher.getErrorCount() > authorReadErrorThreshold) {
							// assume this is a bad URL and just move on
							continue;
						}
					}
					// important that this next line work with author = null!
					setLatestError("Issue with : " + researcher + " : " + e.getMessage());
					if (++currentErrorCnt > errorsToAbort) {
						setStatus(Status.PAUSED);
						break;
					}
				}
			}
		}		
		// if we are still in this status, we are OK
		return Status.READING_RESEARCHERS.equals(getStatus());
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
	
	public String getName() {
		return getAffiliation().getName();
	}
	
	// pass in the name of a configuration file
	public static void main(String[] args) {
		try  {								
			// get these first
			Properties prop = new Properties();
			prop.load(AffiliationCrawler.class.getResourceAsStream(Crosslinks.PROPERTIES_FILE));	
			prop.load(new FileReader(new File(args[0])));
			Injector injector = Guice.createInjector(new IOModule(prop), new CrawlerModule(prop));
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
