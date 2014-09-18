package edu.ucsf.crosslink.io;

import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import edu.ucsf.crosslink.crawler.Crawler;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.R2RResourceObject;
import edu.ucsf.crosslink.model.Researcher;



public interface CrosslinkPersistance {
	
	Calendar startCrawl(Crawler crawler) throws Exception;
	// only call this when things are good, not during an abort
	Calendar finishCrawl(Crawler crawler) throws Exception;

	Map<String, Long> loadRecentlyHarvestedResearchers(Affiliation affiliation, int daysConsideredOld) throws Exception;
	
	void deleteMissingResearchers(Crawler crawler) throws Exception;
	Calendar dateOfLastCrawl(Crawler crawler) throws Exception;

	boolean skip(String researcherURI, String timestampField, int daysConsideredOld) throws Exception;
	
	int touch(Researcher researcher) throws Exception;
	
	Affiliation findAffiliationFor(String uri);
	
	// TODO clean up!
	void startTransaction();
	void endTransaction() throws Exception;

	void save(R2RResourceObject robj) throws Exception;	
	void update(R2RResourceObject robj) throws Exception;
	void add(R2RResourceObject robj) throws Exception;
	
	// this is SPARQL dependent... not good
	void execute(List<String> updates) throws Exception;
}
