package edu.ucsf.crosslink.io;


import java.util.Date;

import edu.ucsf.crosslink.author.Author;

public interface CrosslinkPersistance {
	
	void start() throws Exception;
	
	int saveAuthor(Author author) throws Exception;
	
	Date dateOfLastCrawl();

	boolean skipAuthor(String url);
	
	void close()  throws Exception;

	// only call this when things are good, not during an abort
	void finish()  throws Exception;
}
