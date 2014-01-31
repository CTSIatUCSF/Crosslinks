package edu.ucsf.crosslink.io;

import edu.ucsf.crosslink.author.Author;

public interface CrosslinkPersistance {
	
	void start(String affiliationName)  throws Exception;
	
	void saveAuthor(Author author) throws Exception;

	boolean skipAuthor(String url);

	void close()  throws Exception;
}
