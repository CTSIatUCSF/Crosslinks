package edu.ucsf.crosslink.sitereader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.name.Named;


import edu.ucsf.crosslink.author.Author;

import net.sourceforge.sitemaps.Sitemap;
import net.sourceforge.sitemaps.SitemapParser;
import net.sourceforge.sitemaps.SitemapUrl;
import net.sourceforge.sitemaps.UnknownFormatException;
import net.sourceforge.sitemaps.http.ProtocolException;

public class ProfilesSitemapReader extends SiteReader  {

	private static final Logger LOG = Logger.getLogger(ProfilesSitemapReader.class.getName());
	
	@Inject
	public ProfilesSitemapReader(@Named("Affiliation") String affiliation, @Named("BaseURL") String siteRoot) {
		super(affiliation, siteRoot);
	}

    public void collectAuthorURLS() throws UnknownHostException, MalformedURLException, UnknownFormatException, IOException, ProtocolException, InterruptedException {
		SitemapParser smp = new SitemapParser();
		smp.processSitemap(new URL(getSiteRoot() + "/sitemap.xml"));
		Sitemap sitemap = smp.getSitemap();
		
		Collection<SitemapUrl> urls = sitemap.getUrlList();

		for (SitemapUrl url : urls) {
			addAuthor(new Author(url.getUrl().toString()));
		}
		LOG.info("Found " + getAuthors().size() + " profile pages");
    }
    
}
