package edu.ucsf.crosslink;

import java.util.Collection;


public interface AuthorshipParser {
    public Collection<Authorship> getAuthorshipsFromHTML(SiteReader siteReader, String url) throws Exception;
}
