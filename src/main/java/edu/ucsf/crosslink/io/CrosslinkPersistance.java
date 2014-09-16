package edu.ucsf.crosslink.io;

import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import edu.ucsf.crosslink.crawler.Crawler;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;



public interface CrosslinkPersistance {
	
	Calendar startCrawl(Crawler crawler) throws Exception;
	// only call this when things are good, not during an abort
	Calendar finishCrawl(Crawler crawler) throws Exception;

	Map<String, Long> loadRecentlyHarvestedResearchers(Affiliation affiliation, int daysConsideredOld) throws Exception;
	
	void deleteMissingResearchers(Crawler crawler) throws Exception;
	
	Affiliation findAffiliationFor(String uri);
	
	// TODO clean up!
	void save(Researcher researcher) throws Exception;	
	void update(Researcher researcher, List<String> preStatements) throws Exception;
	void update(Researcher researcher) throws Exception;
	void add(Researcher researcher) throws Exception;
	
	void save(Affiliation affiliation) throws Exception;
	void update(Crawler crawler) throws Exception;

	Calendar dateOfLastCrawl(Crawler crawler);

	boolean skip(String researcherURI, String timestampField, int daysConsideredOld);
	
	int touch(Researcher researcher) throws Exception;
	
	Collection<Researcher> getResearchers();

	void startTransaction();

	void endTransaction() throws Exception;

}
