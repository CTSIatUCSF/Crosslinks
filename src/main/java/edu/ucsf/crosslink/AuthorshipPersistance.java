package edu.ucsf.crosslink;

import java.util.Collection;

public interface AuthorshipPersistance {
	void saveAuthorship(Authorship authorship) throws Exception;
	
	void saveAuthorships(Collection<Authorship> authorships) throws Exception;

	boolean containsAuthor(String url);

	void flush() throws Exception;
	
	void close() throws Exception;
}
