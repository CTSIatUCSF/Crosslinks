package edu.ucsf.crosslink;

public interface SiteReader {
    void readSite(String affiliation, String siteRoot, AuthorshipPersistance store, AuthorshipParser parser) throws Exception;
}
