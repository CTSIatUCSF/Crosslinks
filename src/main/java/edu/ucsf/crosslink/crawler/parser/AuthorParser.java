package edu.ucsf.crosslink.crawler.parser;

import edu.ucsf.crosslink.model.Researcher;

@Deprecated
public interface AuthorParser {
	public static final String ORCID_SECTION = "//orcid.org/";
	
    public boolean readResearcher(Researcher researcher) throws Exception;
}
