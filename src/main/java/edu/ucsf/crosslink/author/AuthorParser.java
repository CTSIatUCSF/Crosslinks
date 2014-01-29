package edu.ucsf.crosslink.author;

import edu.ucsf.crosslink.sitereader.SiteReader;


public interface AuthorParser {
    public Author getAuthorFromHTML(SiteReader siteReader, String url) throws Exception;
}
