package edu.ucsf.crosslink.crawler.parser;

import edu.ucsf.crosslink.model.Researcher;

public interface AuthorParser {
	public static final String PUBMED_SECTION = "//www.ncbi.nlm.nih.gov/pubmed/";
	public static final String ORCID_SECTION = "//orcid.org/";
	
    public Researcher getAuthorFromHTML(String url) throws Exception;
}
