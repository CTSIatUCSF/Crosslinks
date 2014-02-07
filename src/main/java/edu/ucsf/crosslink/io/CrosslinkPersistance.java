package edu.ucsf.crosslink.io;


import java.util.Date;

import edu.ucsf.crosslink.author.Author;

public interface CrosslinkPersistance {
	
	void start() throws Exception;
	
	void saveAuthor(Author author) throws Exception;
	
	Date dateOfLastCrawl();

	boolean skipAuthor(String url);

	void close()  throws Exception;
}
