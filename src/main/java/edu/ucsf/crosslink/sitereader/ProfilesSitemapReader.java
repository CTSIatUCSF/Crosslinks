package edu.ucsf.crosslink.sitereader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;


import edu.ucsf.crosslink.author.Author;

import net.sourceforge.sitemaps.Sitemap;
import net.sourceforge.sitemaps.SitemapParser;
import net.sourceforge.sitemaps.SitemapUrl;
import net.sourceforge.sitemaps.UnknownFormatException;
import net.sourceforge.sitemaps.http.ProtocolException;

public class ProfilesSitemapReader extends SiteReader  {

	private static final Logger LOG = Logger.getLogger(ProfilesSitemapReader.class.getName());
	
	
	public ProfilesSitemapReader(String affiliation, String siteRoot) {
		super(affiliation, siteRoot);
	}

    public List<Author> getAuthors() throws UnknownHostException, MalformedURLException, UnknownFormatException, IOException, ProtocolException, InterruptedException {
    	List<Author> authors = new ArrayList<Author>();
		SitemapParser smp = new SitemapParser();
		smp.processSitemap(new URL(getSiteRoot() + "/sitemap.xml"));
		Sitemap sitemap = smp.getSitemap();
		
		Collection<SitemapUrl> urls = sitemap.getUrlList();

		for (SitemapUrl url : urls) {
			authors.add(new Author(url.getUrl().toString()));
		}
		LOG.info("Found " + authors.size() + " profile pages");
    	return authors;
    }

}
