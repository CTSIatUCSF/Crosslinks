package edu.ucsf.crosslink;

import java.util.Collection;


public interface AuthorshipParser {
    public Collection<Authorship> getAuthorshipsFromHTML(String url) throws Exception;
}
