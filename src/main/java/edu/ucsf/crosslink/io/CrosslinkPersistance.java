package edu.ucsf.crosslink.io;

import java.util.Date;
import java.util.Collection;
import java.util.Map;

import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;



public interface CrosslinkPersistance {
	
	Map<String, Long> startCrawl(Affiliation affiliation) throws Exception;
	
	Affiliation findAffiliationFor(String uri);
	
	// only call this when things are good, not during an abort
	void finishCrawl(Affiliation affiliation) throws Exception;
	
	void saveResearcher(Researcher researcher) throws Exception;
	
	void upsertAffiliation(Affiliation affiliation) throws Exception;

	Date dateOfLastCrawl(Affiliation affiliation);

	boolean skip(Researcher researcher);
	
	int touch(Researcher researcher) throws Exception;
	
	void close();
	
	Collection<Researcher> getResearchers();

	void startTransaction();

	void endTransaction() throws Exception;

}
