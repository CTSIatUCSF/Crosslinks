package edu.ucsf.crosslink;

public interface AuthorshipPersistance {
	void saveAuthorship(Authorship authorship) throws Exception;
	
	boolean containsAuthor(String url);

	void flush() throws Exception;
	
	void close() throws Exception;
}
