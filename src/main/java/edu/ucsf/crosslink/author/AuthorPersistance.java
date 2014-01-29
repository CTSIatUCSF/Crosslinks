package edu.ucsf.crosslink.author;

public interface AuthorPersistance {
	void saveAuthor(Author author) throws Exception;

	boolean containsAuthor(String url);

	void flush() throws Exception;
	
	void close() throws Exception;
}
