package edu.ucsf.crosslink.io;

import java.util.Calendar;
import java.util.Collection;
import java.util.Map;

import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;



public interface CrosslinkPersistance {
	
	Calendar startCrawl(Affiliation affiliation) throws Exception;
	Map<String, Long> loadRecentlyHarvestedResearchers(Affiliation affiliation) throws Exception;
	
	// only call this when things are good, not during an abort
	Calendar finishCrawl(Affiliation affiliation) throws Exception;
	void deleteMissingResearchers(Affiliation affiliation) throws Exception;
	
	Affiliation findAffiliationFor(String uri);
	
	void save(Researcher researcher) throws Exception;
	void update(Researcher researcher) throws Exception;
	
	void save(Affiliation affiliation) throws Exception;

	Calendar dateOfLastCrawl(Affiliation affiliation);

	boolean skip(Researcher researcher);
	
	int touch(Researcher researcher) throws Exception;
	
	Collection<Researcher> getResearchers();

	void startTransaction();

	void endTransaction() throws Exception;

}
