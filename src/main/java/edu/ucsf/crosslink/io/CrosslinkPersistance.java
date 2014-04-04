package edu.ucsf.crosslink.io;

import java.util.Date;
import java.util.Collection;

import edu.ucsf.crosslink.model.Researcher;



public interface CrosslinkPersistance {
	
	void start() throws Exception;
	
	void saveResearcher(Researcher researcher) throws Exception;
	
	Date dateOfLastCrawl();

	boolean skip(Researcher researcher);
	
	int touch(Researcher researcher) throws Exception;
	
	void close()  throws Exception;

	// only call this when things are good, not during an abort
	void finish()  throws Exception;
	
	Collection<Researcher> getResearchers();
}
