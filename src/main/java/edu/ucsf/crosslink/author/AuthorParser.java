package edu.ucsf.crosslink.author;

public interface AuthorParser {
	public static final String PUBMED_SECTION = "//www.ncbi.nlm.nih.gov/pubmed/";
	public static final String ORCID_SECTION = "//orcid.org/";
	
    public Author getAuthorFromHTML(String url) throws Exception;
}
