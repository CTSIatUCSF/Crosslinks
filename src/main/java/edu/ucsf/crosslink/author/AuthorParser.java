package edu.ucsf.crosslink.author;

import edu.ucsf.crosslink.sitereader.SiteReader;


public interface AuthorParser {
	public static final String PUBMED_SECTION = "//www.ncbi.nlm.nih.gov/pubmed/";
	public static final String ORCID_SECTION = "//orcid.org/";
	
    public Author getAuthorFromHTML(SiteReader siteReader, String url) throws Exception;
}
