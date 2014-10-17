package edu.ucsf.crosslink.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.jsoup.nodes.Document;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.crawler.TypedOutputStats.OutputType;
import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Affiliated;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;

@Deprecated
public abstract class AffiliationCrawler implements Affiliated, Iterable<ResearcherProcessor>, R2RConstants {

	private static final Logger LOG = Logger.getLogger(AffiliationCrawler.class.getName());

	private List<Researcher> researchers = new ArrayList<Researcher>();
	private List<Researcher> removeList = new ArrayList<Researcher>();	
	private Map<String, Long> recentlyProcessedAuthors = new HashMap<String, Long>();
	private Set<String> knownReseacherURLs = null;

	private Affiliation affiliation;
	private SiteReader reader;
	private AuthorParser parser;
	private CrosslinkPersistance store;
	
	private int errorsToAbort = 5;
	private int authorReadErrorThreshold = 3;
	private int daysConsideredOld = 4;
	
	private ActiveStatus activeStatus = null;
	
	public enum ActiveStatus {GATHERING_URLS, READING_RESEARCHERS, VERIFY_PRIOR_RESEARCHERS};

	private static void showUse() {
		System.out.println("Pass in the name of the properties file");
	}	

	public AffiliationCrawler(Affiliation affiliation, CrosslinkPersistance store) throws Exception {
		this.affiliation = affiliation;
		this.store = store;
		store.save(affiliation);		
	}
	
	@Inject
	public void setConfiguartion(SiteReader reader, AuthorParser parser,
			@Named("errorsToAbort") Integer errorsToAbort, 
			@Named("authorReadErrorThreshold") Integer authorReadErrorThreshold,
			@Named("daysConsideredOld") Integer daysConsideredOld) throws Exception {
		this.reader = reader;
		this.parser = parser;
		this.errorsToAbort = errorsToAbort;
		this.authorReadErrorThreshold = authorReadErrorThreshold;
		this.daysConsideredOld = daysConsideredOld;
	}
	
    public String getSiteRoot() {
    	return affiliation.getURI();
    }
    
    protected Document getDocument(String url) throws IOException, InterruptedException {
    	return reader.getDocument(url);
    }

	public String toString() {
		// found is dynamic
		return "Active Status = " + activeStatus + ", Remaining " +  getRemainingAuthorsSize();
	}
	
	private void setActiveStatus(ActiveStatus activeStatus) {
		this.activeStatus = activeStatus;
	}

	public Affiliation getAffiliation() {
		return affiliation;
	}
	
	public Iterator<ResearcherProcessor> iterator() {
		// fresh start
		try {
			gatherURLs();
			recentlyProcessedAuthors = store.loadRecentlyHarvestedResearchers(getAffiliation(), daysConsideredOld);
			// touch all the ones we have found.  This will make sure that we do not remove anyone that had been indexed before just due to an error in crawling their page
			knownReseacherURLs = touchResearchers();			
			setActiveStatus(ActiveStatus.READING_RESEARCHERS);
		} 
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return new AffiliationCrawlerIterator();
	}
	
	private void gatherURLs() throws Exception {
		// Now index the site
		setActiveStatus(ActiveStatus.GATHERING_URLS);
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
    
    private void removeResearcher(Researcher researcher) {
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
		setActiveStatus(ActiveStatus.VERIFY_PRIOR_RESEARCHERS);
		Set<String> touched = new HashSet<String>();
		int cnt = 0;
		store.startTransaction();
		for (Researcher researcher : getResearchers()) {
			// sort of ugly, but this will work with the DB store and not mess things up with the CSV store
			if (store.touch(researcher) > 0) {
				touched.add(researcher.getURI());
			}
			LOG.info("Touch " + researcher + " " + cnt++);
		}		
		store.endTransaction();
		return touched;
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

	private class AffiliationCrawlerResearcherProcessor extends BasicResearcherProcessor {
		
		private Researcher researcher;
		
		private AffiliationCrawlerResearcherProcessor(Researcher researcher) {
			super(researcher.getURI());
			this.researcher = researcher;
		}

		public OutputType processResearcher() throws Exception {
			if (isProbablyNotAProfilePage(researcher.getURI())) {
				return OutputType.AVOIDED;
			}
			else if (allowSkip() && store.skip(researcher.getURI(), R2R_WORK_VERIFIED_DT, daysConsideredOld)) {
				// if we make it here, we've processed the author
				removeResearcher(researcher);
				return OutputType.SKIPPED;
			}
			else if (parser.readResearcher(researcher)) {							
				LOG.info("Saving researcher :" + researcher);						
				store.save(researcher);
				// add to processed list
				recentlyProcessedAuthors.put(researcher.getURI(), new Date().getTime());
				return OutputType.PROCESSED;
			}
			else {
				if (knownReseacherURLs.contains(researcher.getURI())) {
					throw new Exception("Error reading known researcher URL: " + researcher.getURI() );
				}
				else {
					return OutputType.AVOIDED;
				}
			}
		}
		
	}
	
	private class AffiliationCrawlerIterator implements Iterator<ResearcherProcessor> {
		
		public boolean hasNext() {
			return getRemainingAuthorsSize() > 0;
		}
		
		public ResearcherProcessor next() {
			return new AffiliationCrawlerResearcherProcessor(researchers.get(0));
		}
		
	}
}
